"""
scripts/prepare_cellpose_training.py

Prepara i dati di training per il fine-tuning di Cellpose 3.x a partire
dalle maschere estratte da QuPath e dalle immagini CZI originali.

Pipeline:
  1. Converte CZI → TIFF (scala di grigi, canale 0)
  2. Legge le maschere 16bit estratte dallo script Groovy
  3. Salva le coppie (immagine, maschera) nel formato atteso da Cellpose

Formato output Cellpose:
  train/
  ├── img001.tif          ← immagine originale
  ├── img001_seg.npy      ← maschera come dict numpy
  ├── img002.tif
  ├── img002_seg.npy
  └── ...

Uso:
  conda activate cell-segmentation
  python scripts/prepare_cellpose_training.py \\
      --czi-dir Data/input/Fase1adese \\
      --masks-dir "Progetti QuPath/Fase1 BW Project/exports" \\
      --output-dir Data/output/cellpose_training

Dipendenze:
  pip install aicsimageio[czi] numpy scipy tifffile
"""

import argparse
import logging
import numpy as np
import tifffile
import shutil
from pathlib import Path

logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")
logger = logging.getLogger(__name__)


# -------------------------------------------------------
# Step 1 — Conversione CZI → TIFF
# -------------------------------------------------------

def convert_czi_to_tiff(czi_path: Path, output_path: Path) -> np.ndarray:
    """
    Legge un file CZI e lo salva come TIFF in scala di grigi (canale 0).
    Restituisce l'array numpy dell'immagine.
    """
    from aicsimageio import AICSImage

    """
    T -> Timepoint
    Z -> Layer asse Z
    C -> Canali
    """
    img = AICSImage(str(czi_path))
    data = img.get_image_data("YX", T=0, Z=0, C=0)

    tifffile.imwrite(str(output_path), data)

    # Verifica che i valori siano preservati
    reloaded = tifffile.imread(str(output_path))
    assert np.array_equal(data, reloaded), "La conversione ha alterato i valori!"

    return data


# -------------------------------------------------------
# Pipeline principale
# -------------------------------------------------------

def find_matching_mask(image_name: str, masks_dir: Path) -> Path | None:
    """
    Trova la maschera corrispondente a un'immagine CZI.
    Lo script Groovy salva le maschere come:
      <nome_immagine>_mask.tif
    """
    stem = Path(image_name).stem
    mask_path = masks_dir / f"{stem}_mask.tif"
    return mask_path if mask_path.exists() else None


def prepare_training_data(
    czi_dir: str,
    masks_dir: str,
    output_dir: str,
) -> None:
    """
    Pipeline completa di preparazione dati per Cellpose.
    """
    czi_dir = Path(czi_dir)
    masks_dir = Path(masks_dir)
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    czi_files = list(czi_dir.rglob("*.czi"))
    if not czi_files:
        logger.warning(f"Nessun'immagine CZI trovata in: {czi_dir}")
        return

    logger.info(f"Immagini trovate: {len(czi_files)}")

    n_ok = 0
    n_skip = 0
    n_error = 0

    for czi_path in sorted(czi_files):
        logger.info(f"\nProcessing: {czi_path.name}")

        # Cerca la maschera corrispondente
        mask_path = find_matching_mask(czi_path.name, masks_dir)
        if mask_path is None:
            logger.warning(f"  Nessuna maschera trovata per {czi_path.name} — saltato")
            n_skip += 1
            continue

        try:
            # Step 1: converti CZI → TIFF
            output_img_path = output_dir / f"{czi_path.stem}.tif"
            logger.info(f"  Conversione CZI → TIFF...")
            img_array = convert_czi_to_tiff(czi_path, output_img_path)
            logger.info(f"  Immagine: {img_array.shape} dtype={img_array.dtype}")

            # Step 2: copia maschera nella cartella di output
            output_mask_path = output_dir / mask_path.name
            logger.info(f"  Copia maschera: {mask_path} → {output_mask_path}")
            shutil.copy2(mask_path, output_mask_path)

            n_ok += 1

        except Exception as e:
            logger.error(f"  Errore processing {czi_path.name}: {e}")
            n_error += 1

    logger.info(f"\n{'='*40}")
    logger.info(f"Completato:")
    logger.info(f"  OK:      {n_ok}")
    logger.info(f"  Saltati: {n_skip}")
    logger.info(f"  Errori:  {n_error}")
    logger.info(f"Output in: {output_dir}")


# -------------------------------------------------------
# Entry point
# -------------------------------------------------------

if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Prepara dati di training per Cellpose da QuPath + CZI"
    )
    parser.add_argument(
        "--czi-dir",
        required=True,
        help="Cartella contenente i file CZI originali (ricerca ricorsiva)"
    )
    parser.add_argument(
        "--masks-dir",
        required=True,
        help="Cartella contenente le maschere estratte da QuPath (*_manual_labels_16bit.tif)"
    )
    parser.add_argument(
        "--output-dir",
        required=True,
        help="Cartella di output per i dati di training Cellpose"
    )
    args = parser.parse_args()

    prepare_training_data(
        czi_dir=args.czi_dir,
        masks_dir=args.masks_dir,
        output_dir=args.output_dir,
    )