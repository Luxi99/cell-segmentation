import qupath.ext.stardist.StarDist2D

def modelPath = "/home/samu/Documents/Laurea/models/dsb2018_heavy_augment.pb"

def stardist = StarDist2D
    .builder(modelPath)
    .preprocessGlobal(
        StarDist2D.imageNormalizationBuilder()
            .maxDimension(4096)
            .percentiles(0, 99.8)
            .build()
    )
    .channels(0)
    .threshold(0.3)
    .tileSize(1024)
    .cellExpansion(5.0)
    .cellConstrainScale(1.5)
    .measureShape()
    .measureIntensity()
    .includeProbability(true)
    .simplify(1)
    .doLog()
    .build()

def imageData = getCurrentImageData()

// Usa le tue annotazioni manuali come regioni padre
def annotations = imageData.getHierarchy().getAnnotationObjects()

if (annotations.isEmpty()) {
    print "Nessuna annotazione trovata! Disegna prima le regioni manualmente."
    return
}

print "Trovate ${annotations.size()} annotazioni manuali, eseguo StarDist al loro interno..."
stardist.detectObjects(imageData, annotations)
stardist.close()
println('Done!')