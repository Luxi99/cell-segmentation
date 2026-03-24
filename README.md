## Setup

### Prerequisiti
- Git
- Miniconda o Anaconda

### Installazione
```bash
git clone <url-repo>
```

## Opzione A — Conda (consigliata, compatibile con Cellpose)
```bash
conda env create -f environment.yml
conda activate cell-segmentation
```

## Opzione B — venv + pip
```bash
python -m venv .venv
source .venv/bin/activate      # Linux/Mac
pip install -r requirements.txt
```

### Struttura
- `Data/input/` — immagini in ingresso (non versionato)
- `Data/output/` — output generati (non versionato)
- `Data/QuPath Scripts/` - script automatizzati per QuPath
- `QuPath/` — eseguibile QuPath (non versionato)
- `Progetti QuPath/` — progetti di QuPath
- `models/` — modelli per la segmentazione delle cellule (non versionato)
