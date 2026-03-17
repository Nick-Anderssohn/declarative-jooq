import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.spring") version "2.1.20"
    id("org.springframework.boot") version "3.4.3"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.nickanderssohn.declarative-jooq") version "0.1.0-SNAPSHOT"
}

group = "com.nickanderssohn"
version = "0.1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("com.nickanderssohn:dsl-runtime:0.1.0-SNAPSHOT")

    // jOOQ (version managed by Spring Boot BOM)
    implementation("org.jooq:jooq")

    // Jackson for Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:testcontainers:1.20.6")
    testImplementation("org.testcontainers:postgresql:1.20.6")
    testImplementation("org.testcontainers:junit-jupiter:1.20.6")
}

declarativeJooq {
    classesDir.set(layout.buildDirectory.dir("classes/kotlin/main"))
    outputPackage.set("com.nickanderssohn.todolist.generated")
    packageFilter.set("com.nickanderssohn.todolist.jooq")
}

tasks.named("generateDeclarativeJooqDsl") {
    dependsOn("compileKotlin")
}

tasks.named("compileTestKotlin") {
    dependsOn("generateDeclarativeJooqDsl")
}

tasks.test {
    useJUnitPlatform()

    // Help Testcontainers find Docker daemon on macOS with Docker Desktop
    val dockerHost = System.getenv("DOCKER_HOST")
        ?: run {
            val rawSock = file("${System.getProperty("user.home")}/Library/Containers/com.docker.docker/Data/docker.raw.sock")
            val runSock = file("${System.getProperty("user.home")}/.docker/run/docker.sock")
            when {
                rawSock.exists() -> "unix://${rawSock.absolutePath}"
                runSock.exists() -> "unix://${runSock.absolutePath}"
                else -> "unix:///var/run/docker.sock"
            }
        }
    systemProperty("docker.host", dockerHost)
    environment("DOCKER_HOST", dockerHost)
    environment("DOCKER_API_VERSION", "1.44")
    systemProperty("api.version", "1.44")
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")

    testLogging {
        events("passed", "failed", "skipped")
    }
}
