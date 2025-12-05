plugins {
    `java-library`
}

dependencies {
    implementation("org.apache.commons:commons-compress:1.27.0")
    implementation("org.postgresql:postgresql:42.7.3")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.flywaydb:flyway-database-postgresql:10.18.0")
    runtimeOnly("org.slf4j:slf4j-nop:2.0.16")
}
