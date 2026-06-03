"""
scripts/train_cellpose.py

Fine-tuning di Cellpose 3.x su cyto3 con divisione 75/25 train/validation
e grafici di valutazione del training.

Grafici prodotti:
  - Loss curve (train + validation per epoca)
  - Distribuzione aree cellule (ground truth vs predizioni sul validation set)
  - Esempi visivi (immagine, ground truth, predizione) sul validation set

Uso:
  conda activate cell-segmentation
  python scripts/train_cellpose.py \\
      --train-dir Data/output/cellpose_training \\
      --model-dir models/cellpose_custom \\
      --n-epochs 100 \\
      --diameter 30

Dipendenze:
  pip install cellpose==3.1.1.1 numpy tifffile scikit-learn matplotlib
"""

import argparse
import logging
import numpy as np
import tifffile
import matplotlib.pyplot as plt
import matplotlib.gridspec as gridspec
import torch
from pathlib import Path
from sklearn.model_selection import train_test_split
from cellpose.metrics import average_precision

logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")
logger = logging.getLogger(__name__)


# -------------------------------------------------------
# Caricamento dati
# -------------------------------------------------------

def load_training_data(train_dir: Path):
    img_files = sorted([
        f for f in train_dir.glob("*.tif")
        if "_masks" not in f.name
    ])

    if not img_files:
        raise ValueError(f"Nessuna immagine trovata in: {train_dir}")

    images, masks = [], []
    skipped = 0

    for img_path in img_files:
        mask_path = train_dir / f"{img_path.stem}_mask.tif"
        #logger.info(f"Cercando maschera {img_path.stem}_mask.tif")
        if not mask_path.exists():
            logger.warning(f"Maschera non trovata per {img_path.name} — saltato")
            skipped += 1
            continue

        img = tifffile.imread(str(img_path))
        mask = tifffile.imread(str(mask_path))

        n_objects = len(np.unique(mask)) - 1
        if n_objects == 0:
            logger.warning(f"Nessun oggetto in {img_path.name} — saltato")
            skipped += 1
            continue

        images.append(img)
        masks.append(mask)
        logger.info(f"  {img_path.name}: {n_objects} oggetti")

    logger.info(f"\nImmagini caricate: {len(images)} ({skipped} saltate)")
    return images, masks


# -------------------------------------------------------
# Grafici
# -------------------------------------------------------

def plot_loss_curve(train_losses, val_losses, output_path: Path):
    """
    Grafico della loss per epoca — train e validation.
    Permette di identificare overfitting (val loss sale mentre train scende).
    """
    epochs = range(1, len(train_losses) + 1)

    fig, ax = plt.subplots(figsize=(10, 5))
    ax.plot(epochs, train_losses, label="Train loss", color="steelblue", linewidth=1.5)
    ax.plot(epochs, val_losses,   label="Validation loss", color="tomato",
            linewidth=1.5, linestyle="--")
    ax.set_xlabel("Epoca")
    ax.set_ylabel("Loss")
    ax.set_title("Loss curve — Train vs Validation")
    ax.legend()
    ax.grid(True, alpha=0.3)

    # Marca il punto di loss minima sul validation
    best_epoch = int(np.argmin(val_losses)) + 1
    best_loss = min(val_losses)
    ax.axvline(x=best_epoch, color="tomato", linestyle=":", alpha=0.7)
    ax.annotate(
        f"Min val loss\nepoca {best_epoch}\n{best_loss:.4f}",
        xy=(best_epoch, best_loss),
        xytext=(best_epoch + len(epochs) * 0.05, best_loss + (max(val_losses) - min(val_losses)) * 0.1),
        fontsize=8,
        color="tomato",
        arrowprops=dict(arrowstyle="->", color="tomato", lw=1)
    )

    plt.tight_layout()
    plt.savefig(str(output_path), dpi=150, bbox_inches="tight")
    plt.close()
    logger.info(f"Loss curve salvata: {output_path.name}")


def plot_area_distribution(gt_masks, pred_masks, output_path: Path):
    """
    Istogramma confronto aree cellule — ground truth vs predizioni.
    Mostra se il modello tende a sotto/sovrastimare le dimensioni.
    """
    def get_areas(masks_list):
        areas = []
        for mask in masks_list:
            for label_id in np.unique(mask)[1:]:  # escludi sfondo
                areas.append(np.sum(mask == label_id))
        return areas

    gt_areas   = get_areas(gt_masks)
    pred_areas = get_areas(pred_masks)

    fig, axes = plt.subplots(1, 2, figsize=(12, 5))

    # Istogramma sovrapposto
    bins = np.linspace(
        min(min(gt_areas), min(pred_areas)),
        max(max(gt_areas), max(pred_areas)),
        40
    )
    axes[0].hist(gt_areas,   bins=bins, alpha=0.6, label="Ground truth",
                 color="steelblue", edgecolor="white")
    axes[0].hist(pred_areas, bins=bins, alpha=0.6, label="Predizioni",
                 color="tomato", edgecolor="white")
    axes[0].set_xlabel("Area (pixel)")
    axes[0].set_ylabel("Numero cellule")
    axes[0].set_title("Distribuzione aree cellule")
    axes[0].legend()
    axes[0].grid(True, alpha=0.3)

    # Scatter GT vs Pred per immagine
    gt_means   = [np.mean([np.sum(m == lid) for lid in np.unique(m)[1:]]) for m in gt_masks]
    pred_means = [np.mean([np.sum(m == lid) for lid in np.unique(m)[1:]]) if len(np.unique(m)) > 1
                  else 0 for m in pred_masks]

    axes[1].scatter(gt_means, pred_means, color="steelblue", alpha=0.7, edgecolors="white")
    max_val = max(max(gt_means), max(pred_means)) * 1.1
    axes[1].plot([0, max_val], [0, max_val], "k--", alpha=0.4, label="Ideale")
    axes[1].set_xlabel("Area media GT (pixel)")
    axes[1].set_ylabel("Area media predetta (pixel)")
    axes[1].set_title("Area media per immagine: GT vs Predizioni")
    axes[1].legend()
    axes[1].grid(True, alpha=0.3)

    plt.tight_layout()
    plt.savefig(str(output_path), dpi=150, bbox_inches="tight")
    plt.close()
    logger.info(f"Distribuzione aree salvata: {output_path.name}")


def plot_iou_distribution(gt_masks, pred_masks, output_path):
    thresholds = [0.5, 0.75, 0.9]

    ap, tp, fp, fn = average_precision(gt_masks, pred_masks, threshold=thresholds)

    fig, axes = plt.subplots(1, 2, figsize=(12, 5))

    # AP per soglia
    mean_ap = ap.mean(axis=0)
    axes[0].bar(
        [f"IoU≥{t}" for t in thresholds],
        mean_ap * 100,
        color="steelblue", edgecolor="white"
    )
    axes[0].set_ylabel("Average Precision (%)")
    axes[0].set_title("Average Precision per soglia IoU")
    axes[0].set_ylim(0, 100)
    axes[0].grid(True, alpha=0.3, axis="y")

    # TP, FP, FN a soglia 0.5
    totals = [tp[:, 0].sum(), fp[:, 0].sum(), fn[:, 0].sum()]
    axes[1].bar(
        ["True Positive", "False Positive", "False Negative"],
        totals,
        color=["steelblue", "tomato", "orange"],
        edgecolor="white"
    )
    axes[1].set_ylabel("Numero cellule")
    axes[1].set_title("TP / FP / FN a soglia IoU ≥ 0.5")
    axes[1].grid(True, alpha=0.3, axis="y")

    plt.tight_layout()
    plt.savefig(str(output_path), dpi=150, bbox_inches="tight")
    plt.close()
    
def plot_visual_examples(val_imgs, gt_masks, pred_masks, output_path: Path, n_examples: int = 3):
    """
    Affianca immagine originale, ground truth e predizione
    per N immagini del validation set.
    Permette ispezione visiva qualitativa del risultato.
    """
    n = min(n_examples, len(val_imgs))
    fig = plt.figure(figsize=(12, 4 * n))
    gs = gridspec.GridSpec(n, 3, figure=fig, hspace=0.4, wspace=0.3)

    for i in range(n):
        img  = val_imgs[i]
        gt   = gt_masks[i]
        pred = pred_masks[i]

        ax_img  = fig.add_subplot(gs[i, 0])
        ax_gt   = fig.add_subplot(gs[i, 1])
        ax_pred = fig.add_subplot(gs[i, 2])

        ax_img.imshow(img, cmap="gray")
        ax_img.set_title(f"Immagine {i+1}")
        ax_img.axis("off")

        ax_gt.imshow(gt, cmap="nipy_spectral")
        n_gt = len(np.unique(gt)) - 1
        ax_gt.set_title(f"Ground truth ({n_gt} cellule)")
        ax_gt.axis("off")

        ax_pred.imshow(pred, cmap="nipy_spectral")
        n_pred = len(np.unique(pred)) - 1
        ax_pred.set_title(f"Predizione ({n_pred} cellule)")
        ax_pred.axis("off")

    plt.suptitle("Esempi visivi — Validation set", fontsize=13, y=1.01)
    plt.savefig(str(output_path), dpi=150, bbox_inches="tight")
    plt.close()
    logger.info(f"Esempi visivi salvati: {output_path.name}")


# -------------------------------------------------------
# Training
# -------------------------------------------------------

def train(
    train_dir: str,
    model_dir: str,
    pretrained_model: str = "cyto3",
    n_epochs: int = 100,
    learning_rate: float = 0.1,
    weight_decay: float = 1e-5,
    batch_size: int = 8,
    min_train_masks: int = 1,
    diameter: float = None,
) -> None:
    from cellpose import models, train as cp_train

    train_dir = Path(train_dir)
    model_dir = Path(model_dir)
    plots_dir = model_dir / "plots"
    model_dir.mkdir(parents=True, exist_ok=True)
    plots_dir.mkdir(parents=True, exist_ok=True)

    # Carica dati
    logger.info(f"Caricamento dati da: {train_dir}")
    images, masks = load_training_data(train_dir)

    if len(images) < 4:
        raise ValueError(
            f"Servono almeno 4 immagini per la divisione 75/25, "
            f"trovate {len(images)}."
        )

    # Divisione 75/25
    train_imgs, val_imgs, train_masks, val_masks = train_test_split(
        images, masks, test_size=0.25, random_state=42
    )

    logger.info(f"\nDivisione dataset:")
    logger.info(f"  Training:   {len(train_imgs)} immagini (75%)")
    logger.info(f"  Validation: {len(val_imgs)} immagini (25%)")
    logger.info(f"\nConfigurazione training:")
    logger.info(f"  Modello base:   {pretrained_model}")
    logger.info(f"  Epoche:         {n_epochs}")
    logger.info(f"  Learning rate:  {learning_rate}")
    logger.info(f"  Weight decay:   {weight_decay}")
    logger.info(f"  Batch size:     {batch_size}")
    logger.info(f"  Diametro:       {diameter if diameter else 'automatico'}")

    # Carica modello base
    logger.info(f"\nCaricamento modello base: {pretrained_model}")
    model = models.CellposeModel(model_type=pretrained_model, gpu=False)

    # Fine-tuning
    logger.info("\nAvvio fine-tuning...")
    model_path, train_losses, val_losses = cp_train.train_seg(
        model.net,
        train_data=train_imgs,
        train_labels=train_masks,
        test_data=val_imgs,
        test_labels=val_masks,
        normalize=True,
        n_epochs=n_epochs,
        learning_rate=learning_rate,
        weight_decay=weight_decay,
        batch_size=batch_size,
        min_train_masks=min_train_masks,
        model_name="cellpose_custom",
        save_path=str(model_dir),
    )

    logger.info(f"\nTraining completato!")
    logger.info(f"Modello salvato in: {model_path}")

    # -------------------------------------------------------
    # Grafici
    # -------------------------------------------------------
    logger.info("\nGenerazione grafici...")

    # 1. Loss curve
    plot_loss_curve(
        train_losses, val_losses,
        plots_dir / "loss_curve.png"
    )

    # 2. Predizioni sul validation set per area e esempi visivi
    logger.info("Esecuzione predizioni sul validation set...")
    trained_model = models.CellposeModel(
        pretrained_model=model_path, gpu=False
    )
    pred_masks = []
    for img in val_imgs:
        masks_pred, _, _ = trained_model.eval(
            img,
            diameter=diameter,
            channels=[0, 0],
            normalize=True
        )
        pred_masks.append(masks_pred)

    # 3. Distribuzione aree
    plot_area_distribution(
        val_masks, pred_masks,
        plots_dir / "area_distribution.png"
    )

    # 4. IoU distribution
    plot_iou_distribution(
        val_masks, pred_masks,
        plots_dir / "iou_distribution.png"
    )

    # 5. Esempi visivi (era il 3, diventa il 5)
    plot_visual_examples(
        val_imgs, val_masks, pred_masks,
        plots_dir / "visual_examples.png",
        n_examples=min(3, len(val_imgs))
    )

    logger.info(f"\nGrafici salvati in: {plots_dir}")
    logger.info(f"Per usare il modello:")
    logger.info(f"  model = CellposeModel(pretrained_model='{model_path}')")


# -------------------------------------------------------
# Entry point
# -------------------------------------------------------

if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Fine-tuning Cellpose 3.x con grafici di valutazione"
    )
    parser.add_argument("--train-dir",       required=True)
    parser.add_argument("--model-dir",       required=True)
    parser.add_argument("--pretrained-model", default="cyto3")
    parser.add_argument("--n-epochs",        type=int,   default=100)
    parser.add_argument("--learning-rate",   type=float, default=0.1)
    parser.add_argument("--batch-size",      type=int,   default=8)
    parser.add_argument("--diameter",        type=float, default=None)
    args = parser.parse_args()

    train(
        train_dir=args.train_dir,
        model_dir=args.model_dir,
        pretrained_model=args.pretrained_model,
        n_epochs=args.n_epochs,
        learning_rate=args.learning_rate,
        batch_size=args.batch_size,
        diameter=args.diameter,
    )