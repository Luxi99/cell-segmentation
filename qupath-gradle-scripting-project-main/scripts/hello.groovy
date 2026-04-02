import qupath.lib.objects.PathObject

import static qupath.lib.gui.scripting.QPEx.*
import qupath.lib.io.GsonTools


writeStringToFile(getCurrentImageName(), getAnnotationCoordinatesGeoJSON())

public static String getAnnotationCoordinatesGeoJSON() {
    def annotations = getAnnotationObjects()

    boolean prettyPrint = true
    def gson = GsonTools.getInstance(prettyPrint)

    return gson.toJson(annotations, Collection<PathObject>)
}

public static void writeStringToFile(String fileName, String text) {
    def path = buildFilePath(PROJECT_BASE_DIR, "exports", fileName + ".json")
    def file = new File(path)
    file.getParentFile().mkdirs()
    file.write(text)
}