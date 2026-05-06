import groovyjarjarantlr4.v4.runtime.misc.NotNull
import qupath.lib.objects.PathObject
import qupath.lib.scripting.QP

import javax.imageio.ImageIO
import java.awt.Color
import java.awt.Shape
import java.awt.geom.Area
import java.awt.image.BufferedImage
import java.awt.image.WritableRaster


/**
 * Ordina una lista di PathObject, quindi annotazioni dei perimetri di regioni di
 * interesse (ROI) in modo che gli oggetti con label "nucleo" non compaiano mai prima
 * di oggetti con altre label (gli oggetti nucleo sono posizionati per ultimi).
 *
 * @param annotations La lista non nulla di annotazioni fatte sull'immagine
 * @return La lista non nulla di annotazioni ordinata con quelle etichettate con "nucleo" per ultime
 *
 * */
static @NotNull List<PathObject> sortAnnotations(@NotNull List<PathObject> annotations) {
    return annotations.sort { a, b ->
        def aName = a.getPathClass()?.getName()?.toLowerCase() ?: ""
        def bName = b.getPathClass()?.getName()?.toLowerCase() ?: ""

        def aIsNucleus = aName.contains("nucleo") ? 1 : 0
        def bIsNucleus = bName.contains("nucleo") ? 1 : 0

        return aIsNucleus <=> bIsNucleus
    }
}

/**
 * Sottrae le aree figlie (potenzialmente l'area di un nucleo) dall'area del
 * padre (potenzialmente l'area totale del citoplasma)
 *
 * @param parentShape La forma non nulla corrispondente all'oggetto padre
 * @param childShapes La lista non nulla delle forme figlie di {@code parentShape}
 * @return Un'oggetto {@code java.awt.geom.Area} corrispondente
 * all'area di {@code parentShape} meno quella di ogni elemento non nullo di {@code childShapes}
 */
static @NotNull Area subtractChildren(@NotNull Shape parentShape, @NotNull List<Shape> childShapes) {
    Area area = new Area(parentShape)
    childShapes.each {if(it==null) childShapes.remove(it)}
    childShapes.each {
        area.subtract(new Area(it))
    }

    return area
}

/**
 * Rasterizza un'area in una maschera a 16 bit, riempiendo i pixel interni a tale area con il valore
 * di una data label o etichetta.
 *
 * @param raster L'oggetto {@code java.awt.image.WritableRaster} non nullo
 * corrispondente al raster su cui disegnare la Labeled-Mask
 * @param area L'oggetto {@code java.awt.geom.Area} non nullo corrispondente all'area da disegnare sul raster
 * @param label L'intero >= 1 corrispondente all'etichetta da assegnare alla maschera corrente
 * @param w L'intero > 0 corrispondente alla larghezza dell'immagine
 * @param h L'intero > 0 corrispondente all'altezza dell'immagine
 */
static void paintLabel(@NotNull WritableRaster raster, @NotNull Area area, int label, int w, int h) {
    def temp = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_BINARY)
    def g = temp.createGraphics()
    g.setColor(Color.WHITE)
    g.fill(area)
    g.dispose()

    def binaryRaster = temp.getRaster()
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            if (binaryRaster.getSample(x, y, 0) > 0) {  // Se ci sono pixel dell'oggetto corrente "attivi" nella maschera binaria temporanea, li attiva anche con la relativa label appendendoli anche nel raster finale
                raster.setSample(x, y, 0, label)
            }
        }
    }
}

/**
 * Restituisce la stringa corrispondente al record in formato TSV dell'annotazione data
 *
 * @param ann L'oggetto {@code PathObject} non nullo corrispondente all'annotazione presa in considerazione
 * @param label Il numero di etichetta dell'annotazione corrispondente
 * @return Una stringa non nulla corrispondente al record in formato TSV di {@code ann}
 */
static @NotNull String createTableRecord(@NotNull PathObject ann, int label) {
    def roi = ann.getROI();

    def cx = roi.getCentroidX()
    def cy = roi.getCentroidY()
    def cls = ann.getPathClass()?.getName() ?: "Unclassified"

    return "${label}\t${cx}\t${cy}\t${cls}"
}

/**
 * Salva la tabella pre-formattata in TSV in un file al percorso dato
 *
 * @param labelTable La stringa non nulla rappresentante la tabella in formato TSV, pre-formattata in TSV
 * @param header La stringa non nulla corrispondente all'header del file TSV, pre-formattata in TSV
 * @param path La stringa non nulla rappresentante il percorso di salvataggio del file
 */
static void saveTable(@NotNull List labelTable, @NotNull String header,@NotNull String path) {
    try {
        new File(path).text = ([header] + labelTable).join("\n")
        println "✅ Manual segmentation label table saved to: $path"
    } catch (Exception e) {
        println "❌ Failed to save table: ${e.message}"
    }
}

/**
 * Salva l'immagine con formato specificato in un file al percorso dato
 *
 * @param labelImage L'immagine non nulla da salvare
 * @param formatName La stringa non nulla rappresentante il formato in cui sarà salvata l'immagine
 * @param path La stringa non nulla rappresentante il percorso di salvataggio dell'immagine
 */
static void saveMask(@NotNull BufferedImage labelImage, @NotNull String formatName, @NotNull String path) {
    try {
        ImageIO.write(labelImage, formatName, new File(path))
        println "✅ Manual segmentation label image saved (16-bit) to: $path"
    } catch (Exception e) {
        println "❌ Failed to save label image: ${e.message}"
    }
}

/**
 * Restituisce una mappa da label image a label table corrispondente.
 *
 * @param annotations La lista non nulla delle annotazioni fatte sull'immagine di partenza
 * @param w L'intero > 0 corrispondente alla larghezza dell'immagine di partenza
 * @param h L'intero > 0 corrispondente all'altezza dell'immagine di partenza
 * @return Una mappa non nulla con chiave l'immagine su cui si sta lavorando e valore la tabella TSV
 * contenente le informazioni sulle annotazioni nell'immagine
 */
static @NotNull Map buildMaskAndTable(List<PathObject> annotations, int w, int h) {
    def labelImage = new BufferedImage(w, h, BufferedImage.TYPE_USHORT_GRAY)
    def raster = labelImage.getRaster()
    def labelTable = []
    int label = 1

    def sorted = sortAnnotations(annotations)

    for (ann in sorted) {
        if (label > 65535) {
            println "Numero etichette disponibili superato. Mi fermo a 65535 annotazioni (16 bit)"
            break
        }

        def roi = ann.getROI()
        def shape = roi?.getShape()

        if (shape == null) {
            println "Salto annotazione con forma nulla"
            continue
        }

        def childShapes = ann
                .getChildObjects()
                .collect { it.getROI()?.getShape() }
                .findAll { it != null }

        def area = subtractChildren(shape, childShapes ?: [])

        paintLabel(raster, area, label, w, h)
        labelTable << createTableRecord(ann, label)

        label++
    }

    return [image: labelImage, table: labelTable]
}

final def OUTPUT_DIR = QP.buildFilePath(QP.PROJECT_BASE_DIR, "exports")
final def IMAGE_NAME = QP.getProjectEntry()?.getImageName() ?: "Unnamed"
final def LABEL_IMAGE_PATH = QP.buildFilePath(OUTPUT_DIR, IMAGE_NAME + "_manual_labels_16bit.tif")
final def TABLE_PATH = QP.buildFilePath(OUTPUT_DIR, IMAGE_NAME + "_manual_labels.tsv")
QP.mkdirs(OUTPUT_DIR)

def imageData = QP.getCurrentImageData()
def server = imageData.getServer()
def w = server.getWidth()
def h = server.getHeight()

// Aggiorna automaticamente la gerarchia del file in modo che annotazioni interamente contenute
// dentro altre siano impostate come figlie delle seconde
def hierarchy = QP.getCurrentHierarchy()
hierarchy.resolveHierarchy()
QP.fireHierarchyUpdate()

def annotations = QP.getAnnotationObjects().toList()

if (annotations.isEmpty()) {
    println "Nessuna annotazione trovata!"
    return
}

def result = buildMaskAndTable(annotations, w, h)

saveMask(result.image, "TIFF", LABEL_IMAGE_PATH)
saveTable(result.table, "LabelID\tCentroidX\tCentroidY\tClass", TABLE_PATH)