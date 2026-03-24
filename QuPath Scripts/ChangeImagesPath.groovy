import qupath.lib.projects.ProjectIO

// -------------------------------------------------------
// CONFIGURA QUESTI DUE PARAMETRI
def oldBase = '/C:/DATI/Elab_Imgs_Mediche/Fase1adese'   // percorso attuale (da sostituire)
def newBase = '/home/samu/Documents/Laurea/Fase1adese/'        // nuovo percorso sulla tua macchina
// -------------------------------------------------------

def project = getProject()
int updated = 0
int failed = 0

project.getImageList().each { entry ->
    def uris = entry.getURIs()
    def newURIs = uris.collect { uri ->
        def path = new File(uri).absolutePath
        if (path.startsWith(oldBase)) {
            def relativePart = path.substring(oldBase.length())
            def newPath = newBase + relativePart
            def newFile = new File(newPath)
            if (newFile.exists()) {
                print "✓ Aggiornato: ${newFile}"
                updated++
                return newFile.toURI()
            } else {
                print "✗ File non trovato: ${newPath}"
                failed++
                return uri  // lascia invariato
            }
        }
        return uri
    }
    entry.updateURIs([(uris[0]): newURIs[0]])  // aggiorna nel progetto
}

project.syncChanges()  // salva il file .qpproj

print "==========================="
print "Aggiornati: ${updated}"
print "Non trovati: ${failed}"
print "Progetto salvato."