import qupath.lib.objects.PathObject

import static qupath.lib.gui.scripting.QPEx.*
import qupath.lib.io.GsonTools

def imageData = getCurrentImageData()
def hierarchy = imageData.getHierarchy()
def annotations = hierarchy.getAnnotationObjects()

print getCurrentImageName() + '\t' + annotations.size()

boolean prettyPrint = true
def gson = GsonTools.getInstance(prettyPrint)

println gson.toJson(annotations, Collection<PathObject>)