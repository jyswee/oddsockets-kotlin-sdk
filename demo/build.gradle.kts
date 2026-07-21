plugins {
    kotlin("jvm") version "1.9.20"
    kotlin("plugin.serialization") version "1.9.20"
    application
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    // Depend on the parent OddSockets Kotlin SDK via the composite build in
    // settings.gradle.kts (includeBuild("..")). The coordinate below is
    // substituted with the local SDK sources at build time.
    implementation("com.oddsockets:oddsockets-kotlin-sdk:0.1.0-beta.1")

    // Coroutines for runBlocking / suspend calls used by the SDK.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Serialization for building the message payload.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
}

kotlin {
    jvmToolchain(11)
}

application {
    mainClass.set("com.oddsockets.demo.DemoKt")
}
