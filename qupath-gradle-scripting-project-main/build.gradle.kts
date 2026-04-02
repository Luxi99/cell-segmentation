plugins {
    // Apply the groovy Plugin to add support for Groovy
    `groovy`
    // To manage included native libraries, limiting to the current platform
    alias(libs.plugins.javacpp)
    // For JavaFX support
    id("org.openjfx.javafxplugin") version "0.1.0"
}

val qupathVersion: String by gradle.extra

javafx {
    version = libs.versions.javafx.get()
    modules = listOf(
        "javafx.base",
        "javafx.controls",
        "javafx.graphics",
        "javafx.media",
        "javafx.fxml",
        "javafx.web",
        "javafx.swing"
    )
}

dependencies {
    // Get QuPath GUI & core (version declared in settings.gradle.kts)
    implementation("io.github.qupath:qupath-gui-fx:$qupathVersion")

    // Other core dependencies
    implementation(libs.qupath.fxtras)

    // Get SLF4J and Groovy, using the versions associated with QuPath
    implementation(libs.bundles.logging)
    implementation(libs.bundles.groovy)
}

// We aren't structuring things 'properly' because we just want a flat directory of scripts
sourceSets {
    main {
        groovy {
            setSrcDirs(listOf("scripts/"))
        }
    }
}

/*
 * Ensure Java compatibility matches QuPath, and include sources and javadocs if building jars.
 */
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.versions.jdk.get())
    }
    withSourcesJar()
    withJavadocJar()
}
