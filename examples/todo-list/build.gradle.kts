import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.spring") version "2.1.20"
    id("org.springframework.boot") version "3.4.3"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.nickanderssohn.declarative-jooq") version "1.0.0"
}

group = "com.nickanderssohn"
version = "1.0.0"

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

    // jOOQ (version managed by Spring Boot BOM)
    implementation("org.jooq:jooq")

    // Jackson for Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:testcontainers:1.20.6")
    testImplementation("org.testcontainers:postgresql:1.20.6")
    testImplementation("org.testcontainers:junit-jupiter:1.20.6")
    testImplementation("com.nickanderssohn:declarative-jooq-dsl-runtime:1.0.0")
}

declarativeJooq {
    // Where the compiled application classes live; the plugin scans these to find jOOQ table classes.
    classesDir.set(layout.buildDirectory.dir("classes/kotlin/main"))

    // Root package for the generated DSL files.
    outputPackage.set("com.nickanderssohn.generated.dsl")

    // Only generate DSL for classes whose package matches this prefix (filters out unrelated tables).
    packageFilter.set("com.nickanderssohn.todolist.jooq")

    // If we want the generated code to live in source control, rather than only exist in the build,
    // then you can use outputDir. If you omit this, then the generated classes will only exist in the build dir.
    outputDir.set(layout.projectDirectory.dir("src/test/kotlin"))

    // We want the generated code to live with test classes. Shouldn't exist in the production jar.
    sourceSet.set("test")
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
