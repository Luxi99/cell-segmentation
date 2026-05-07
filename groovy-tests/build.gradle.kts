import org.gradle.kotlin.dsl.invoke

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

    // Junit 5 for testing
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")

    // Mockito
    testImplementation("org.mockito:mockito-core:5.14.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.14.0")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")

    // Groovy nei test
    testImplementation(libs.bundles.groovy)
}

sourceSets {
    main {
        groovy {
            setSrcDirs(listOf("../QuPath Scripts/"))
            include(
                "LabelMaskBuilder.groovy"
            )
        }
    }

    test {
        groovy {
            setSrcDirs(listOf("test/code"))
        }
        resources {
            setSrcDirs(listOf("test/resources/"))
        }
    }

    create("integrationTest") {
        groovy {
            setSrcDirs(listOf("test/integration"))
        }
        resources {
            setSrcDirs(listOf("test/resources"))
        }

        compileClasspath += sourceSets["main"].output + configurations["testRuntimeClasspath"]
        runtimeClasspath += output + compileClasspath
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}


val integrationTestImplementation by configurations.getting {
    extendsFrom(configurations["testImplementation"])
}

tasks.register<Test>("integrationTest") {
    description = "Esegue i test di integrazione"
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
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
