plugins {
    kotlin("jvm") version "1.9.20"
    kotlin("plugin.serialization") version "1.9.20"
    id("maven-publish")
    id("signing")
    id("org.jetbrains.dokka") version "1.9.10"
}

group = "com.oddsockets"
version = "0.1.0-beta.1"

repositories {
    mavenCentral()
    google()
}

dependencies {
    // Kotlin standard library
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.20")
    
    // Coroutines for async programming
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Serialization for JSON handling
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // Networking
    implementation("io.ktor:ktor-client-core:2.3.5")
    implementation("io.ktor:ktor-client-cio:2.3.5")
    implementation("io.ktor:ktor-client-websockets:2.3.5")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.5")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.5")
    implementation("io.ktor:ktor-client-logging:2.3.5")

    // Logging
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("ch.qos.logback:logback-classic:1.4.11")
    
    // Testing
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(11)
    
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-Xopt-in=kotlinx.serialization.ExperimentalSerializationApi"
        )
    }
}

tasks.test {
    useJUnitPlatform()
}

// Documentation
tasks.dokkaHtml.configure {
    outputDirectory.set(buildDir.resolve("dokka"))
}

// Publishing configuration
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            
            pom {
                name.set("OddSockets Kotlin SDK")
                description.set("Official Kotlin SDK for OddSockets real-time messaging platform")
                url.set("https://github.com/jyswee/oddsockets-kotlin-sdk")
                
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                
                developers {
                    developer {
                        id.set("oddsockets")
                        name.set("OddSockets Team")
                        email.set("support@oddsockets.com")
                    }
                }
                
                scm {
                    connection.set("scm:git:git://github.com/jyswee/oddsockets-kotlin-sdk.git")
                    developerConnection.set("scm:git:ssh://github.com/jyswee/oddsockets-kotlin-sdk.git")
                    url.set("https://github.com/jyswee/oddsockets-kotlin-sdk")
                }
            }
        }
    }
    
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/jyswee/oddsockets-kotlin-sdk")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN")
            }
        }
    }
}

// Signing for Maven Central
signing {
    sign(publishing.publications["maven"])
}
