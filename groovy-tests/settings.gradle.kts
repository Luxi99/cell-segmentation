/*
 * Gradle settings, including the QuPath version to use with scripts.
 */

rootProject.name = "qupath-scripts"

// TODO: Define the QuPath version to use here (e.g. 0.5.1, 0.6.0-SNAPSHOT...)
var qupathVersion by gradle.extra("0.7.0")

dependencyResolutionManagement {

    repositories {

        // Use Maven Central for dependencies
        mavenCentral()

        // Use SciJava for QuPath and other dependency jars
        maven("https://maven.scijava.org/content/groups/public/")

    }

    // Access the QuPath version catelog so that extra dependencies
    // can be added, using the same versions as for QuPath itself
    versionCatalogs {
        create("libs") {
            from("io.github.qupath:qupath-catalog:$qupathVersion")
        }
    }

}
