QUPATH     = ./qupath/bin/QuPath
SCRIPT     = QuPath Scripts/LabelMaskBuilder.groovy
GRADLE_DIR = groovy-tests

# Argomenti del makefile
PROJECT    	?= progetti qupath/default/default.qpproj
SEPARATE_NUCLEI ?= true

.PHONY: test check-project extract all help

check-project:
	@if [ ! -f "$(PROJECT)" ]; then \
		echo "Errore: progetto non trovato: $(PROJECT)"; \
		exit 1; \
	else \
		echo "Progetto trovato!"; \
	fi
	
test:
	cd $(GRADLE_DIR) && ./gradlew test

extract: check-project
	@echo "Using project: $(PROJECT)"
	"$(QUPATH)" script --project "$(PROJECT)" --args "[$(SEPARATE_NUCLEI)]" "$(SCRIPT)"

all: test extract

help:
	@echo "———————————————————————————"
	@echo "	 COMANDI"
	@echo "———————————————————————————"
	@echo "make check-project    			— verifica che il percorso specificato per il progetto QuPath sia valido"
	@echo "make test             			— esegui i test unitari Groovy sullo script di estrazione"
	@echo "make extract				— estrai le label images con QuPath headless scegliendo se separare i nuclei"
	@echo "make all              			— test + estrazione in sequenza"
	@echo ""
	@echo "———————————————————————————"
	@echo "	 PARAMETRI"
	@echo "———————————————————————————"
	@echo 
	@echo "PROJECT=<percorso_del_progetto>		— definisce il percorso del progetto su cui lavorare"
	@echo "SEPARATE_NUCLEI=<true> o <false>	— indica se ignorare i nuclei o 'separarli' dalle cellule. Di default è = true"
	@echo ""
	@echo "———————————————————————————"
	@echo "	 ESEMPI"
	@echo "———————————————————————————"
	@echo "make extract \\"
	@echo "	PROJECT=/my/own/project/folder/project.qpproj \\"
	@echo "	SEPARATE_NUCLEI=false"
	@echo ""
	@echo "make all \\"
	@echo "	PROJECT=/my/own/project/folder/project.qpproj"
	
