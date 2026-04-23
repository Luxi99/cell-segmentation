plugins {
    `groovy`
    alias(libs.plugins.javacpp)
    id("org.openjfx.javafxplugin") version "0.1.0"
}

val qupathVersion: String by gradle.extra

javafx {
    version = libs.versions.javafx.get()
    modules = listOf(
        "javafx.base", "javafx.controls", "javafx.graphics",
        "javafx.media", "javafx.fxml", "javafx.web", "javafx.swing"
    )
}

dependencies {
    implementation("io.github.qupath:qupath-gui-fx:$qupathVersion")
    implementation(libs.qupath.fxtras)
    implementation(libs.bundles.logging)
    implementation(libs.bundles.groovy)

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
    testImplementation(libs.bundles.groovy)
}

sourceSets {
    test {
        groovy { setSrcDirs(listOf("test/code")) }
        resources { setSrcDirs(listOf("test/resources")) }
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.versions.jdk.get())
    }
}