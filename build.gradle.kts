val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project
val exposed_version: String by project
val resilience4j_version: String by project
val cucumber_version: String by project

plugins {
    kotlin("jvm") version "1.9.0"
    id("io.ktor.plugin") version "2.3.2"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.8.0"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    jacoco
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    mergeServiceFiles()
    archiveBaseName.set("${project.name}-all")
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.5".toBigDecimal()
            }
        }
    }
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        csv.required.set(false)
    }
}

ktor {
    docker {
        jreVersion.set(io.ktor.plugin.features.JreVersion.JRE_17)
        localImageName.set("raiderio-ladder-backend-image")
        imageTag.set("0.0.1")
        portMappings.set(
            listOf(
                io.ktor.plugin.features.DockerPortMapping(
                    80,
                    8080,
                    io.ktor.plugin.features.DockerPortMappingProtocol.TCP
                )
            )
        )
        externalRegistry.set(
            io.ktor.plugin.features.DockerImageRegistry.dockerHub(
                appName = provider { "ktor-app" },
                username = providers.environmentVariable("DOCKER_HUB_USERNAME"),
                password = providers.environmentVariable("DOCKER_HUB_PASSWORD")
            )
        )
    }
}

group = "com.example"
version = "0.0.1"
application {
    mainClass.set("com.kos.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-netty-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-cors:$ktor_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.arrow-kt:arrow-core:1.2.0")
    implementation("io.arrow-kt:arrow-fx-coroutines:1.2.0")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("io.ktor:ktor-server-auth:$ktor_version")
    implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-dao:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-client-auth:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("io.ktor:ktor-server-swagger:$ktor_version")
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("org.flywaydb:flyway-database-postgresql:10.20.1")
    implementation("org.mindrot:jbcrypt:0.4")
    implementation("io.ktor:ktor-server-call-logging:$ktor_version")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("io.ktor:ktor-server-auth:$ktor_version")
    implementation("io.ktor:ktor-server-auth-jwt:$ktor_version")
    implementation("io.github.resilience4j:resilience4j-kotlin:${resilience4j_version}")
    implementation("io.github.resilience4j:resilience4j-ratelimiter:${resilience4j_version}")
    testImplementation("io.zonky.test:embedded-postgres:2.0.7")
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.0")
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:$kotlin_version")
    testImplementation("io.ktor:ktor-client-mock:$ktor_version")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("io.mockk:mockk:1.13.8")

    testImplementation("io.cucumber:cucumber-java:$cucumber_version")
    testImplementation("io.cucumber:cucumber-core:$cucumber_version")
    testImplementation("io.cucumber:cucumber-junit-platform-engine:$cucumber_version")
    testImplementation("org.junit.platform:junit-platform-suite:1.14.0")
}