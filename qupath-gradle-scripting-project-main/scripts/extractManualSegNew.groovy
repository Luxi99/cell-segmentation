import qupath.lib.gui.scripting.QPEx

import java.awt.image.BufferedImage
import java.awt.Color
import java.awt.Graphics2D
import javax.imageio.ImageIO
import java.awt.image.WritableRaster

// Get current image
def imageData = QPEx.getCurrentImageData()
def server = imageData.getServer()
def name = QPEx.getProjectEntry()?.getImageName() ?: "Unnamed"
def w = server.getWidth()
def h = server.getHeight()

print "📂 Processing image: $name (${w}x${h})"

// Get manual annotations
//def annotations = QPEx.getAnnotationObjects()
//print "🖍️ Found ${annotations.size()} manual annotations"

def allAnnotations = QPEx.getAnnotationObjects()
print "🖍️ Found ${allAnnotations.size()} manual annotations"

// Put nuclei last
def annotations = allAnnotations.sort { a, b ->
    def aName = a.getPathClass()?.getName()?.toLowerCase() ?: ""
    def bName = b.getPathClass()?.getName()?.toLowerCase() ?: ""

    def aIsNucleus = aName.contains("nucleo") ? 1 : 0
    def bIsNucleus = bName.contains("nucleo") ? 1 : 0

    return aIsNucleus <=> bIsNucleus
}



if (annotations.isEmpty()) {
    print "⚠️ No annotations found! Draw some ROIs or ask your colleague to share them."
    return
}

// Create 16-bit grayscale image
def labelImage = new BufferedImage(w, h, BufferedImage.TYPE_USHORT_GRAY)
WritableRaster raster = labelImage.getRaster()

// Output paths
def outputDir = buildFilePath(PROJECT_BASE_DIR, "exports")
mkdirs(outputDir)
def labelImagePath = buildFilePath(outputDir, name + "_manual_labels_16bit.tif")
def tablePath = buildFilePath(outputDir, name + "_manual_labels.tsv")

def label = 1
def labelTable = []

Graphics2D g2d = labelImage.createGraphics()


import java.awt.geom.Area

for (annotation in annotations) {
    if (label > 65535) {
        print "⚠️ Label count exceeds 65535 (16-bit limit), stopping at 65535."
        break
    }

    def roi = annotation.getROI()
    def shape = roi?.getShape()
    if (shape == null) {
        print "⚠️ Skipping annotation with null shape"
        continue
    }

    // Start from the current annotation shape
    def area = new Area(shape)

    // Subtract child annotations, e.g. nuclei inside a cell
    def children = annotation.getChildObjects() // Qualsiasi oggetto dentro l'annotazione o gli oggetti specificatamente sottostanti nella gerarchia?
    for (child in children) {
        def childROI = child.getROI()
        def childShape = childROI?.getShape()
        if (childShape != null) {
            print "⚠️ child found"
            area.subtract(new Area(childShape))
        }
    }

    // Paint only the remaining area
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

    def cx = roi.getCentroidX()
    def cy = roi.getCentroidY()
    def cls = annotation.getPathClass()?.getName() ?: "Unclassified"

    labelTable << "${label}\t${cx}\t${cy}\t${cls}"
    label++
}

g2d.dispose()

// Save 16-bit image
try {
    ImageIO.write(labelImage, "TIFF", new File(labelImagePath))
    print "✅ Manual segmentation label image saved (16-bit) to: $labelImagePath"
} catch (Exception e) {
    print "❌ Failed to save label image: ${e.message}"
}

// Save table
try {
    def header = "LabelID\tCentroidX\tCentroidY\tClass"
    new File(tablePath).text = ([header] + labelTable).join("\n")
    print "✅ Manual segmentation label table saved to: $tablePath"
} catch (Exception e) {
    print "❌ Failed to save table: ${e.message}"
}
