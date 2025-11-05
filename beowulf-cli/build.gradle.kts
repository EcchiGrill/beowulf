plugins {
    application
}

dependencies {
    implementation(project(":beowulf-core"))
}

application {
    mainClass = "com.beowulf.cli.BeowulfCLI"
}
