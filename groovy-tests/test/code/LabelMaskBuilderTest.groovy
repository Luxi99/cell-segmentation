import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import qupath.lib.objects.PathObjects
import qupath.lib.regions.ImagePlane
import qupath.lib.roi.ROIs
import qupath.lib.roi.RoiTools

import java.awt.Color
import java.awt.Shape

import qupath.lib.objects.PathObject
import qupath.lib.objects.classes.PathClass

import java.awt.Rectangle
import java.awt.geom.Area
import java.awt.image.BufferedImage

import static org.junit.jupiter.api.Assertions.*

class LabelMaskBuilderTest {

    static final int W = 100
    static final int H = 100

    /**
     * Crea annotazione con etichetta e forma, ma senza figli
     * */
    static PathObject createAnnotation(String className, Shape shape) {
        return createAnnotationWithChildren(className, shape, [])
    }

    /**
     * Crea annotazione con etichetta, forma e figli
     * */
    static PathObject createAnnotationWithChildren(String className, Shape shape, List<PathObject> children) {
        def pathClass = PathClass.fromString(className)
        def roi = RoiTools.getShapeROI(shape, ImagePlane.getDefaultPlane(), 1)
        def annotation = PathObjects.createAnnotationObject(roi)

        annotation.setPathClass(pathClass)
        annotation.addChildObjects(children)

        return annotation
    }

    /**
     * Conta quanti pixel con un determinato valore siano presenti nell'immagine
     * */
    static int countPixelsWithValue(BufferedImage img, int value) {
        def raster = img.getRaster()
        int count = 0
        for (int y = 0; y < img.getHeight(); y++)
            for (int x = 0; x < img.getWidth(); x++)
                if (raster.getSample(x, y, 0) == value) count++
        return count
    }


    /**
     * Data una lista di annotazioni, verifica che il metodo di sorting le posizioni
     * in fondo a tale lista
     * */
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
        def sorted = LabelMaskBuilder.sortAnnotations(annotations)
        assertEquals("nucleo", sorted[-1].getPathClass().getName())
        assertEquals("nucleo", sorted[-2].getPathClass().getName())
        sorted[0..8].each { assertNotEquals("nucleo", it.getPathClass().getName()) }
    }

    /**
     * Se in lista non sono presenti nuclei, l'ordine della stessa non cambia se passata
     * al metodo di ordinamento
     * */
    @Test
    @DisplayName("Lista senza nuclei non deve cambiare ordine relativo")
    void testOrdineSenzaNuclei() {
        def shape = new Rectangle(0, 0, 10, 10)
        def sorted = LabelMaskBuilder.sortAnnotations([
                createAnnotation("red blood cell", shape),
                createAnnotation("lymphocyte", shape),
                createAnnotation("positive", shape),
        ])
        assertEquals(["red blood cell", "lymphocyte", "positive"],
                sorted.collect { it.getPathClass().getName() })
    }

    /**
     * Verifica che una lista vuota passata al metodo di ordinamento restituisca sempre una lista vuota
     * */
    @Test
    @DisplayName("Lista vuota deve restituire lista vuota")
    void testOrdinamentoListaVuota() {
        assertTrue(LabelMaskBuilder.sortAnnotations([]).isEmpty())
    }

    /**
     * Verifica che una lista di soli nuclei non cambi a seguito di un ordinamento
     * */
    @Test
    @DisplayName("Lista con soli nuclei deve rimanere invariata")
    void testOrdinamentoSoloNuclei() {
        def shape = new Rectangle(0, 0, 10, 10)
        def sorted = LabelMaskBuilder.sortAnnotations([
                createAnnotation("nucleo", shape),
                createAnnotation("nucleo", shape),
        ])
        assertEquals(2, sorted.size())
        sorted.each { assertEquals("nucleo", it.getPathClass().getName()) }
    }


    /**
     * Verifica che sottraendo l'area dei figli ad una forma che non ha figli,
     * la sua area rimanga invariata
     * */
    @Test
    @DisplayName("Senza figli l'area deve essere uguale alla forma originale")
    void testSottrazioneSenzaFigli() {
        def parent = new Rectangle(10, 10, 40, 40)
        def area = LabelMaskBuilder.subtractChildren(parent, [])
        def differenza = new Area(area)
        differenza.subtract(new Area(parent))
        assertTrue(differenza.isEmpty())
    }

    /**
     * Verifica che l'area di un'annotazione figlia venga effettivamente sottratta da quella
     * del padre contando i pixel corrispondenti all'area sottratta.
     */
    @Test
    @DisplayName("Il figlio deve essere sottratto dall'area padre")
    void testSottrazioneFiglio() {
        def parent = new Rectangle(10, 10, 40, 40)
        def child  = new Rectangle(20, 20, 10, 10)
        def area = LabelMaskBuilder.subtractChildren(parent, [child])

        def img = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_BINARY)
        def g = img.createGraphics()
        g.setColor(Color.WHITE)
        g.fill(area)
        g.dispose()

        // L'immagine creata ha pixel di 1 bit e si sta considerando solo 1 canale
        int pixelBianchi = countPixelsWithValue(img, 1)

        assertTrue(pixelBianchi == 1500)
    }


    /**
     * Verifica che l'area del figlio sottratta a quella del padre crei un "buco" nel padre.
     * Controlla verificando che il valore del pixel corrispondente al centroide del figlio nell'immagine
     * formata con l'area risultante sia 0 (sfondo nero).
     */
    @Test
    @DisplayName("Il figlio deve creare un buco nell'area padre")
    void testBucoNelCitoplasma() {
        def parent = new Rectangle(10, 10, 40, 40)
        def child  = new Rectangle(20, 20, 10, 10)
        def area = LabelMaskBuilder.subtractChildren(parent, [child])

        def img = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_BINARY)
        def g = img.createGraphics()
        g.setColor(Color.WHITE)
        g.fill(area)
        g.dispose()

        def raster = img.getRaster()
        assertEquals(0, raster.getSample(25, 25, 0), "Il centro del nucleo deve essere un buco")
        assertTrue(raster.getSample(12, 12, 0) > 0, "Un punto nel citoplasma deve essere bianco")
    }

    /**
     * Verifica che il metodo paintLabel scriva correttamente l'etichetta passata come parametro nell'immagine finale
     */
    @Test
    @DisplayName("paintLabel deve scrivere il label corretto nei pixel")
    void testPaintLabel() {
        def img = new BufferedImage(W, H, BufferedImage.TYPE_USHORT_GRAY)
        LabelMaskBuilder.paintLabel(img.getRaster(), new Area(new Rectangle(10, 10, 20, 20)), 42, W, H)
        assertEquals(42, img.getRaster().getSample(20, 20, 0))
    }

    /**
     * Verifica che i pixel al di fuori dell'area disegnata rimangano a 0
     */
    @Test
    @DisplayName("I pixel fuori dall'area devono rimanere a 0")
    void testPixelEsterniRimangono0() {
        def img = new BufferedImage(W, H, BufferedImage.TYPE_USHORT_GRAY)
        LabelMaskBuilder.paintLabel(img.getRaster(), new Area(new Rectangle(10, 10, 20, 20)), 1, W, H)
        assertEquals(0, img.getRaster().getSample(0, 0, 0))
        assertEquals(0, img.getRaster().getSample(99, 99, 0))
    }


    /**
     * Verifica che due etichette diverse non interferiscano tra loro nell'immagine finale
     */
    @Test
    @DisplayName("Due label diversi non devono sovrascriversi")
    void testDueLabel() {
        def img = new BufferedImage(W, H, BufferedImage.TYPE_USHORT_GRAY)
        def raster = img.getRaster()
        LabelMaskBuilder.paintLabel(raster, new Area(new Rectangle(5,  5,  20, 20)), 1, W, H)
        LabelMaskBuilder.paintLabel(raster, new Area(new Rectangle(60, 60, 20, 20)), 2, W, H)
        assertEquals(1, raster.getSample(15, 15, 0))
        assertEquals(2, raster.getSample(70, 70, 0))
        assertEquals(0, raster.getSample(40, 40, 0))
    }


    /**
     * Verifica che il metodo createTableRecord restituisca la corretta stringa formattata in TSV,
     * data un'annotazione
     */
    @Test
    @DisplayName("createTableRecord deve restituire una stringa TSV corretta")
    void testCreateTableRecord() {
        def ann = createAnnotation("red blood cell", new Rectangle(10, 10, 20, 20))
        def parts = LabelMaskBuilder.createTableRecord(ann, 3).split("\t")
        assertEquals(4, parts.length)
        assertEquals("3", parts[0])
        assertEquals("20.0", parts[1])
        assertEquals("20.0", parts[2])
        assertEquals("red blood cell", parts[3])
    }

    /**
     * Verifica che se manca l'etichetta ad un'annotazione, createTableRecord usa Unclassified come etichetta
     */
    @Test
    @DisplayName("createTableRecord con classe null deve usare 'Unclassified'")
    void testCreateTableRecordSenzaClasse() {
        def roi = ROIs.createRectangleROI(0, 0, 10, 10)

        def ann = PathObjects.createAnnotationObject(roi)

        assertTrue(LabelMaskBuilder.createTableRecord(ann, 1).endsWith("Unclassified"))
    }


    /**
     * Verifica che il metodo buildMaskAndTable crei un'immagine con label incrementali che partano da 1 per la
     * prima annotazione
     */
    @Test
    @DisplayName("buildMaskAndTable deve assegnare label progressivi da 1")
    void testLabelProgressivi() {
        def annotations = [
                createAnnotation("red blood cell",       new Rectangle(5,  5,  20, 20)),
                createAnnotation("lymphocyte", new Rectangle(40, 40, 20, 20)),
        ]
        def raster = LabelMaskBuilder.buildMaskAndTable(annotations, W, H).image.getRaster()
        assertEquals(1, raster.getSample(15, 15, 0))
        assertEquals(2, raster.getSample(50, 50, 0))
        assertEquals(0, raster.getSample(0,  0,  0))
    }

    @Disabled
    @Test
    @DisplayName("buildMaskAndTable deve mettere i nuclei in fondo")
    void testNucleiInFondoNellaMaschera() {
        def result = LabelMaskBuilder.buildMaskAndTable([
                createAnnotation("nucleo", new Rectangle(5,  5,  20, 20)),
                createAnnotation("red blood cell",    new Rectangle(40, 40, 20, 20)),
        ], W, H)
        assertTrue(result.table[0].contains("red blood cell"))
        assertTrue(result.table[1].contains("nucleo"))
    }

    /**
     * Verifica che da una lista vuota di annotazioni buildMaskAndTable crei una maschera con pixel tutti a 0
     */
    @Test
    @DisplayName("buildMaskAndTable con lista vuota deve restituire maschera tutta a 0")
    void testMascheraVuota() {
        def result = LabelMaskBuilder.buildMaskAndTable([], W, H)
        assertEquals(W * H, countPixelsWithValue(result.image, 0))
        assertTrue(result.table.isEmpty())
    }

    /**
     * Verifica che nell'immagine la label del nucleo sia diversa da quella del genitore
     */
    @Test
    @DisplayName("buildMaskAndTable con nucleo dentro citoplasma deve creare il buco")
    void testBucoIntegrazione() {
        def nucleus = createAnnotation("nucleo", new Rectangle(25, 25, 10, 10))
        def cytoplasmAnn = createAnnotationWithChildren(
                "positive",
                new Rectangle(10, 10, 50, 50),
                [nucleus]
        )
        def raster = LabelMaskBuilder.buildMaskAndTable([cytoplasmAnn, nucleus], W, H).image.getRaster()
        assertNotEquals(1, raster.getSample(30, 30, 0),
                "Il centro del nucleo non deve avere il label del citoplasma")
    }

    /**
     * Verifica che lo script lanciato con parametro separateNuclei = false, restituisca una label image
     * con i soli citoplasmi delle cellule annotate, scartando le annotazioni di tipo nucleo.
     */
    @Test
    @DisplayName("Con separateNuclei=false i nuclei non devono apparire nella tabella")
    void testSeparateNucleiFalse() {
        def nucleus = createAnnotation("nucleo", new Rectangle(25, 25, 10, 10))
        def cyto = createAnnotationWithChildren(
                "cellula cancerosa",
                new Rectangle(10, 10, 50, 50),
                [nucleus]
        )

        def result = LabelMaskBuilder.buildMaskAndTable([cyto, nucleus], W, H, false)

        // La tabella deve avere solo 1 riga — il citoplasma, non il nucleo
        assertEquals(1, result.table.size(),
                "Con separateNuclei=false i nuclei non devono apparire nella tabella")

        // Il centro del nucleo deve avere il label del citoplasma (nessun buco)
        assertEquals(1, result.image.getRaster().getSample(30, 30, 0),
                "Con separateNuclei=false l'area del nucleo deve far parte del citoplasma")
    }

    /**
     * Verifica che lo script lanciato con parametro separateNuclei = true, restituisca una label image
     * con anche i nuclei annotati sovra impressi alle cellule genitrici
     */
    @Test
    @DisplayName("Con separateNuclei=true i nuclei devono apparire nella tabella")
    void testSeparateNucleiTrueMantieneNuclei() {
        def nucleus = createAnnotation("nucleo", new Rectangle(25, 25, 10, 10))
        def cyto = createAnnotationWithChildren(
                "cellula cancerosa",
                new Rectangle(10, 10, 50, 50),
                [nucleus]
        )

        def result = LabelMaskBuilder.buildMaskAndTable([cyto, nucleus], W, H, true)

        // La tabella deve avere 2 righe — citoplasma e nucleo
        assertEquals(2, result.table.size(),
                "Con separateNuclei=true i nuclei devono apparire nella tabella")

        // Il centro del nucleo non deve avere il label del citoplasma (buco presente)
        assertNotEquals(1, result.image.getRaster().getSample(30, 30, 0),
                "Con separateNuclei=true ci deve essere il buco del nucleo")
    }
}