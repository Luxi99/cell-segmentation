import qupath.lib.gui.scripting.QPEx
import qupath.lib.objects.PathAnnotationObject
import java.awt.image.BufferedImage
import java.awt.Color
import java.awt.Graphics2D
import javax.imageio.ImageIO
import qupath.lib.images.servers.ImageServer
import qupath.lib.images.servers.TransformedServerBuilder
import java.io.File
import java.awt.image.WritableRaster

// Get current image
def imageData = QPEx.getCurrentImageData()
def server = imageData.getServer()
def name = QPEx.getProjectEntry()?.getImageName() ?: "Unnamed"
def w = server.getWidth()
def h = server.getHeight()

print "📂 Processing image: $name (${w}x${h})"

// Get manual annotations
def annotations = QPEx.getAnnotationObjects()
print "🖍️ Found ${annotations.size()} manual annotations"

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

    // Create a temporary binary mask for this annotation
    def temp = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_BINARY)
    def g = temp.createGraphics()
    g.setColor(Color.WHITE)
    g.fill(shape)
    g.dispose()

    def binaryRaster = temp.getRaster()

    // Copy mask to 16-bit raster with current label value
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            if (binaryRaster.getSample(x, y, 0) > 0) {
                raster.setSample(x, y, 0, label)
            }
        }
    }

    // Metadata
    def cx = roi.getCentroidX()
    def cy = roi.getCentroidY()
    
    // here compute all shape descriptors for the region as well as texture descriptors
    
    def cls = annotation.getPathClass()?.getName() ?: "Unclassified"
    
    def coords = roi.getAllPoints().collect { point ->
        "${point.getX().round(2)},${point.getY().round(2)}"
    }.join(";")
    
    labelTable << "${label}\t${cx}\t${cy}\t${cls}\t${coords}"
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
    def header = "LabelID\tCentroidX\tCentroidY\tClass\tCoordinates"
    new File(tablePath).text = ([header] + labelTable).join("\n")
    print "✅ Manual segmentation label table saved to: $tablePath"
} catch (Exception e) {
    print "❌ Failed to save table: ${e.message}"
}
