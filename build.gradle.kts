
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(ktorLibs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.example"
version = "1.0.0-SNAPSHOT"

application {
    mainClass.set("com.example.MainKt")
}

kotlin {
    jvmToolchain(21)
}
dependencies {
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(ktorLibs.server.contentNegotiation)
    implementation("io.ktor:ktor-server-status-pages")
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.auth)
    implementation(ktorLibs.server.auth.jwt)
    implementation(libs.logback.classic)
    
    // Client for AI API calls
    implementation(ktorLibs.client.core)
    implementation(ktorLibs.client.cio)
    implementation(ktorLibs.client.contentNegotiation)
    implementation(ktorLibs.serialization.kotlinx.json)
    
    // Database
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.javatime)
    implementation(libs.mysql.connector)
    implementation(libs.hikaricp)
    
    // Utils
    implementation(libs.bcrypt)
    implementation(libs.dotenv.kotlin)
    
    // Migrations
    implementation(libs.flyway.core)
    implementation(libs.flyway.mysql)

    // PDF Generation
    implementation("com.github.librepdf:openpdf:1.3.30")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.zxing:javase:3.5.3")
    
    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
}
