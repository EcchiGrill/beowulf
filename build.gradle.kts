allprojects {
    group = "com.beowulf"
    version = "0.0.1"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    dependencies {
        add("testImplementation", "org.junit.jupiter:junit-jupiter:5.10.2")
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher:1.10.2")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events ("passed", "skipped", "failed")
        }
    }
}

