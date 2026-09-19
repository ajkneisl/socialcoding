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
    compilerOptions { optIn.add("kotlin.uuid.ExperimentalUuidApi") }
}

val generateVersionProperties by
    tasks.registering(WriteProperties::class) {
        destinationFile = layout.buildDirectory.file("generated/version/version.properties")
        property("version", project.version.toString())
    }

tasks.processResources { from(generateVersionProperties) }

tasks.test {
    // Read settings from the environment below rather than Parameter Store.
    environment("ENV", "TEST")
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
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.h2database.h2)
    implementation(libs.kord.rest)
    implementation(libs.logback.classic)
    implementation(libs.postgresql)
    implementation(libs.dotenv)
    implementation(libs.reflections)
    // Parameter Store and S3, over the JDK's HTTP client rather than the SDK's default
    // Apache/Netty ones.
    implementation(libs.awssdk.ssm) {
        exclude(group = "software.amazon.awssdk", module = "apache-client")
        exclude(group = "software.amazon.awssdk", module = "apache5-client")
        exclude(group = "software.amazon.awssdk", module = "netty-nio-client")
    }
    implementation(libs.awssdk.s3) {
        exclude(group = "software.amazon.awssdk", module = "apache-client")
        exclude(group = "software.amazon.awssdk", module = "apache5-client")
        exclude(group = "software.amazon.awssdk", module = "netty-nio-client")
    }
    implementation(libs.awssdk.url.connection.client)

    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
}
