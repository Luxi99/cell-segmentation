import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import qupath.lib.objects.PathObjects
import qupath.lib.regions.ImagePlane
import qupath.lib.roi.ROIs
import qupath.lib.roi.RoiTools

import java.awt.Color
import java.awt.Shape

import static org.junit.jupiter.api.Assertions.*

import qupath.lib.objects.PathObject
import qupath.lib.objects.classes.PathClass

import java.awt.Rectangle
import java.awt.geom.Area
import java.awt.image.BufferedImage

class LabelMaskBuilderTest {

    static final int W = 100
    static final int H = 100

    static PathObject createAnnotation(String className, Shape shape) {
        return createAnnotationWithChildren(className, shape, [])
    }

    static PathObject createAnnotationWithChildren(String className, Shape shape, List<PathObject> children) {
        def pathClass = PathClass.fromString(className)
        def roi = RoiTools.getShapeROI(shape, ImagePlane.getDefaultPlane(), 1)
        def annotation = PathObjects.createAnnotationObject(roi)

        annotation.setPathClass(pathClass)
        annotation.addChildObjects(children)

        return annotation
    }

    static int countPixelsWithValue(BufferedImage img, int value) {
        def raster = img.getRaster()
        int count = 0
        for (int y = 0; y < img.getHeight(); y++)
            for (int x = 0; x < img.getWidth(); x++)
                if (raster.getSample(x, y, 0) == value) count++
        return count
    }

    // --- sortAnnotations ---

    @Test
    @DisplayName("I nuclei devono essere messi in fondo alla lista")
    void testNucleiInFondo() {
        Shape shape = new Rectangle(0, 0, 10, 10)
        def annotations = [
                createAnnotation("red blood cell", shape),
                createAnnotation("nucleo", shape),
                createAnnotation("necrosis", shape),
                createAnnotation("positive", shape),
                createAnnotation("negative", shape),
                createAnnotation("suamous epithelial cells", shape),
                createAnnotation("echinocytes", shape),
                createAnnotation("nucleo", shape),
                createAnnotation("lymphocyte", shape),
                createAnnotation("neutrofilo", shape),
                createAnnotation("others", shape),
        ]
        def sorted = ExtractManualSegNewRefactored.sortAnnotations(annotations)
        assertEquals("nucleo", sorted[-1].getPathClass().getName())
        assertEquals("nucleo", sorted[-2].getPathClass().getName())
        sorted[0..8].each { assertNotEquals("nucleo", it.getPathClass().getName()) }
    }

    @Test
    @DisplayName("Lista senza nuclei non deve cambiare ordine relativo")
    void testOrdineSenzaNuclei() {
        def shape = new Rectangle(0, 0, 10, 10)
        def sorted = ExtractManualSegNewRefactored.sortAnnotations([
                createAnnotation("red blood cell", shape),
                createAnnotation("lymphocyte", shape),
                createAnnotation("positive", shape),
        ])
        assertEquals(["red blood cell", "lymphocyte", "positive"],
                sorted.collect { it.getPathClass().getName() })
    }

    @Test
    @DisplayName("Lista vuota deve restituire lista vuota")
    void testOrdinamentoListaVuota() {
        assertTrue(ExtractManualSegNewRefactored.sortAnnotations([]).isEmpty())
    }

    @Test
    @DisplayName("Lista con soli nuclei deve rimanere invariata")
    void testOrdinamentoSoloNuclei() {
        def shape = new Rectangle(0, 0, 10, 10)
        def sorted = ExtractManualSegNewRefactored.sortAnnotations([
                createAnnotation("nucleo", shape),
                createAnnotation("nucleo", shape),
        ])
        assertEquals(2, sorted.size())
        sorted.each { assertEquals("nucleo", it.getPathClass().getName()) }
    }

    // --- subtractChildren ---

    @Test
    @DisplayName("Senza figli l'area deve essere uguale alla forma originale")
    void testSottrazioneSenzaFigli() {
        def parent = new Rectangle(10, 10, 40, 40)
        def area = ExtractManualSegNewRefactored.subtractChildren(parent, [])
        def differenza = new Area(area)
        differenza.subtract(new Area(parent))
        assertTrue(differenza.isEmpty())
    }

    @Test
    @DisplayName("Il figlio deve essere sottratto dall'area padre")
    void testSottrazioneFiglio() {
        def parent = new Rectangle(10, 10, 40, 40)
        def child  = new Rectangle(20, 20, 10, 10)
        def area = ExtractManualSegNewRefactored.subtractChildren(parent, [child])

        def img = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_BINARY)
        def g = img.createGraphics()
        g.setColor(Color.WHITE)
        g.fill(area)
        g.dispose()

        // Funziona perchà l'immagine creata ha pixel di 1 bit e si sta considerando solo 1 canale
        int pixelBianchi = countPixelsWithValue(img, 1)

        assertTrue(pixelBianchi == 1500)
    }

    @Test
    @DisplayName("Il figlio deve creare un buco nell'area padre")
    void testBucoNelCitoplasma() {
        def parent = new Rectangle(10, 10, 40, 40)
        def child  = new Rectangle(20, 20, 10, 10)
        def area = ExtractManualSegNewRefactored.subtractChildren(parent, [child])

        def img = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_BINARY)
        def g = img.createGraphics()
        g.setColor(Color.WHITE)
        g.fill(area)
        g.dispose()

        def raster = img.getRaster()
        assertEquals(0, raster.getSample(25, 25, 0), "Il centro del nucleo deve essere un buco")
        assertTrue(raster.getSample(12, 12, 0) > 0, "Un punto nel citoplasma deve essere bianco")
    }

    // --- paintLabel ---

    @Test
    @DisplayName("paintLabel deve scrivere il label corretto nei pixel")
    void testPaintLabel() {
        def img = new BufferedImage(W, H, BufferedImage.TYPE_USHORT_GRAY)
        ExtractManualSegNewRefactored.paintLabel(img.getRaster(), new Area(new Rectangle(10, 10, 20, 20)), 42, W, H)
        assertEquals(42, img.getRaster().getSample(20, 20, 0))
    }

    @Test
    @DisplayName("I pixel fuori dall'area devono rimanere a 0")
    void testPixelEsterniRimangono0() {
        def img = new BufferedImage(W, H, BufferedImage.TYPE_USHORT_GRAY)
        ExtractManualSegNewRefactored.paintLabel(img.getRaster(), new Area(new Rectangle(10, 10, 20, 20)), 1, W, H)
        assertEquals(0, img.getRaster().getSample(0, 0, 0))
        assertEquals(0, img.getRaster().getSample(99, 99, 0))
    }

    @Test
    @DisplayName("Due label diversi non devono sovrascriversi")
    void testDueLabel() {
        def img = new BufferedImage(W, H, BufferedImage.TYPE_USHORT_GRAY)
        def raster = img.getRaster()
        ExtractManualSegNewRefactored.paintLabel(raster, new Area(new Rectangle(5,  5,  20, 20)), 1, W, H)
        ExtractManualSegNewRefactored.paintLabel(raster, new Area(new Rectangle(60, 60, 20, 20)), 2, W, H)
        assertEquals(1, raster.getSample(15, 15, 0))
        assertEquals(2, raster.getSample(70, 70, 0))
        assertEquals(0, raster.getSample(40, 40, 0))
    }

    // --- createTableRecord ---

    @Test
    @DisplayName("createTableRecord deve restituire una stringa TSV corretta")
    void testCreateTableRecord() {
        def ann = createAnnotation("red blood cell", new Rectangle(10, 10, 20, 20))
        def parts = ExtractManualSegNewRefactored.createTableRecord(ann, 3).split("\t")
        assertEquals(4, parts.length)
        assertEquals("3", parts[0])
        assertEquals("red blood cell", parts[3])
    }

    @Test
    @DisplayName("createTableRecord con classe null deve usare 'Unclassified'")
    void testCreateTableRecordSenzaClasse() {
        def roi = ROIs.createRectangleROI(0, 0, 10, 10)

        def ann = PathObjects.createAnnotationObject(roi)

        assertTrue(ExtractManualSegNewRefactored.createTableRecord(ann, 1).endsWith("Unclassified"))
    }

    // --- buildMaskAndTable ---

    @Test
    @DisplayName("buildMaskAndTable deve assegnare label progressivi da 1")
    void testLabelProgressivi() {
        def annotations = [
                createAnnotation("red blood cell",       new Rectangle(5,  5,  20, 20)),
                createAnnotation("lymphocyte", new Rectangle(40, 40, 20, 20)),
        ]
        def raster = ExtractManualSegNewRefactored.buildMaskAndTable(annotations, W, H).image.getRaster()
        assertEquals(1, raster.getSample(15, 15, 0))
        assertEquals(2, raster.getSample(50, 50, 0))
        assertEquals(0, raster.getSample(0,  0,  0))
    }

    @Test
    @DisplayName("buildMaskAndTable deve mettere i nuclei in fondo")
    void testNucleiInFondoNellaMaschera() {
        def result = ExtractManualSegNewRefactored.buildMaskAndTable([
                createAnnotation("nucleo", new Rectangle(5,  5,  20, 20)),
                createAnnotation("red blood cell",    new Rectangle(40, 40, 20, 20)),
        ], W, H)
        assertTrue(result.table[0].contains("red blood cell"))
        assertTrue(result.table[1].contains("nucleo"))
    }

    @Test
    @DisplayName("buildMaskAndTable con lista vuota deve restituire maschera tutta a 0")
    void testMascheraVuota() {
        def result = ExtractManualSegNewRefactored.buildMaskAndTable([], W, H)
        assertEquals(W * H, countPixelsWithValue(result.image, 0))
        assertTrue(result.table.isEmpty())
    }

    @Test
    @DisplayName("buildMaskAndTable con nucleo dentro citoplasma deve creare il buco")
    void testBucoIntegrazione() {
        def nucleus = createAnnotation("nucleo", new Rectangle(25, 25, 10, 10))
        def cytoplasmAnn = createAnnotationWithChildren(
                "positive",
                new Rectangle(10, 10, 50, 50),
                [nucleus]
        )
        def raster = ExtractManualSegNewRefactored.buildMaskAndTable([cytoplasmAnn, nucleus], W, H).image.getRaster()
        assertNotEquals(1, raster.getSample(30, 30, 0),
                "Il centro del nucleo non deve avere il label del citoplasma")
    }
}