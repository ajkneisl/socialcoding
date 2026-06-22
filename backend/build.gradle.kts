plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(ktorLibs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.socialcoding"

version = "1.0.0-SNAPSHOT"

application { mainClass = "com.socialcoding.ApplicationKt" }

kotlin {
    jvmToolchain(21)
    // Exposed's uuid() columns and our user ids use kotlin.uuid.Uuid, still experimental in 2.3.
    compilerOptions { optIn.add("kotlin.uuid.ExperimentalUuidApi") }
}

tasks.test {
    environment("DATABASE_URL", "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
    environment("JWT_SECRET", "dev-only-secret-change-me")
}

dependencies {
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(ktorLibs.client.cio)
    implementation(ktorLibs.client.core)
    implementation(ktorLibs.server.auth)
    implementation(ktorLibs.server.auth.jwt)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.cors)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.statusPages)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.h2database.h2)
    implementation(libs.logback.classic)
    implementation(libs.postgresql)
    implementation(libs.dotenv)

    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
}
