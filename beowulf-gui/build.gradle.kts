plugins {
    application
}

dependencies {
    implementation(project(":beowulf-core"))
}

application {
    mainClass = "com.beowulf.gui.Beowulf"
}
