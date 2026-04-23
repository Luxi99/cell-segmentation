QUPATH     = ./qupath/bin/QuPath
PROJECT    ?= "progetti qupath/default/default.qpproj"
SCRIPT     = "QuPath Scripts/ExtractManualSegNewRefactored.groovy"
GRADLE_DIR = groovy-tests

.PHONY: test extract all help

test:
	cd $(GRADLE_DIR) && ./gradlew test

check_project:
	@if [ ! -f "$(PROJECT)" ]; then \
		echo "Errore: progetto non trovato: $(PROJECT)"; \
		exit 1; \
	fi

extract:
	check_project
	@echo "Using project: $(PROJECT)"
	$(QUPATH) script --project $(PROJECT) $(SCRIPT)

all: test extract

help:
	@echo "make test     — esegui i test unitari Groovy sullo script di estrazione"
	@echo "make extract  — estrai le label images con QuPath headless"
	@echo "make all      — test + estrazione in sequenza"