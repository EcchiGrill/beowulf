plugins {
    application
     id("org.openjfx.javafxplugin") version "0.1.0"
}

javafx {
    version = "21.0.5"
    modules = listOf("javafx.controls", "javafx.graphics")
}

dependencies {
    implementation(project(":beowulf-core"))
}

application {
    mainClass = "com.beowulf.gui.BeowulfGUI"
}
