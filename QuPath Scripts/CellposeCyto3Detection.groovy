import qupath.ext.biop.cellpose.Cellpose2D

def cellpose = Cellpose2D.builder('cyto3')// modello per citoplasma intero
    .channels(0)          // canale da usare — per scala di grigi usa 0
    .normalizePercentiles(1, 99)
    .tileSize(1024)
    .cellExpansion(0)          // Cellpose trova già il citoplasma, non serve espandere
    .measureShape()
    .measureIntensity()
    .simplify(1)
    .build()

// Crea annotazione sull'intera immagine corrente
def imageData = getCurrentImageData()
def server = imageData.getServer()
def roi = ROIs.createRectangleROI(0, 0, server.getWidth(), server.getHeight(), ImagePlane.getDefaultPlane())
def annotation = PathObjects.createAnnotationObject(roi)
imageData.getHierarchy().addObject(annotation)
imageData.getHierarchy().getSelectionModel().setSelectedObject(annotation)

// Esegui Cellpose
def pathObjects = getSelectedObjects()
if (pathObjects.isEmpty()) {
    print "Nessun oggetto selezionato!"
    return
}

cellpose.detectObjects(imageData, pathObjects)
println('Done!')