## Setup

### Prerequisiti
- Git
- Python 3.12.3 o Miniconda/Anaconda
- QuPath v0.6.0 o più recenti

## Installazione
```bash
git clone <url-repo>
cd cell-segmentation
```

### Opzione A — Conda (compatibile con Cellpose)

Creazione dell'ambiente principale (funzionante standalone con Cellpose 3.1.1.3 e modello cyto3)
```bash
conda env create -f main-environment.yml
conda activate cell-segmentation
```

Volendo è anche possibile usare Cellpose-SAM (Cellpose 4.x con modello `cpsam`) creando un ambiente separato.  
**N.B.:** `cpsam` è un modello molto valido, ma anche molto più oneroso in termini di spazio di archiviazione
e di prestazioni richieste rispetto al modello "predefinito" `cyto3`. La creazione di due ambienti separati è dovuta
a motivi di retrocompatibilità tra le due versioni di Cellpose

Per maggiori informazioni consultare:  
`https://github.com/BIOP/qupath-extension-cellpose?tab=readme-ov-file#qupath-extension-cellposeomnipose-first-time-setup`  
`https://www.cellpose.org/`

```bash
conda env create -f cellpose-sam-environment.yml
conda activate cellpose-sam
```


### Opzione B (ANCORA NON SUPPORTATA) — venv + pip 
```bash
# python -m venv .venv
# source .venv/bin/activate      # Linux/Mac
# pip install -r requirements.txt
```

### Struttura
- `Data/input/` — immagini in ingresso (non versionato)
- `Data/output/` — output generati (non versionato)
- `Data/QuPath Scripts/` - script automatizzati per QuPath
- `QuPath/` — eseguibile QuPath (non versionato)
- `Progetti QuPath/` — progetti di QuPath
- `models/` — modelli per la segmentazione delle cellule (non versionato)


## QuPath

Aggiungere il catalogo estensioni BIOP seguendo quanto indicato nel passo "Installation" di [questa](https://github.com/BIOP/qupath-biop-catalog?tab=readme-ov-file#installation) pagina.  
Quindi seguire quanto detto in [questa](https://github.com/BIOP/qupath-extension-cellpose?tab=readme-ov-file#installation) pagina al passo "On QuPath 0.6.x" e installare cellpose dall'extension manager (in particolare dal catalogo BIOP appena installato).  

Andare su `Edit` -> `Preferences` -> `Cellpose/Omnipose` e in `Cellpose 'python.exe' location` impostare il percorso dell'eseguibile di python nell'ambiente virtuale principale, che ottengo dopo aver eseguito i comandi:

```bash
conda activate cell-segmentation
which python
```
Quindi nel campo `Cellpose SAM 'python.exe' location` impostare il percorso dell'eseguibile di python nell'ambiente virtuale dedicato a Cellpose SAM, che ottengo dopo aver eseguito i comandi:

```bash
conda activate cellpose-sam
which python
```

Quindi uscire dall'ambiente virtuale SAM con `conda deactivate`.

**N.B.:** Questo metodo di installazione di Cellpose funziona solo con versioni di QuPath a partire dalla 0.6.0. Per versioni meno recenti riferirsi a [questo](https://github.com/BIOP/qupath-extension-cellpose?tab=readme-ov-file#on-qupath-05x) link, tuttavia al momento non è garantita compatibilità. Inoltre le operazioni precedenti si intendono effettuate sempre all'interno dell'ambiente virtuale attivato in precedenza.