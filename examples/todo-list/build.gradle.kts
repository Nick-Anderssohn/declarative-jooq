import org.jetbrains.kotlin.gradle.dsl.JvmTarget

buildscript {
    dependencies {
        classpath("org.flywaydb:flyway-database-postgresql:10.20.1")
    }
}

plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.spring") version "2.1.20"
    id("org.springframework.boot") version "3.4.3"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.nickanderssohn.declarative-jooq") version "1.0.1"
    id("org.flywaydb.flyway") version "10.20.1"
    id("nu.studer.jooq") version "10.2.1"
}

group = "com.nickanderssohn"
version = "1.0.1"

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

sourceSets {
    main {
        kotlin.srcDir("src/generated/jooq")
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

    // jOOQ code generation needs the PostgreSQL driver
    jooqGenerator("org.postgresql:postgresql:42.7.4")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:testcontainers:1.20.6")
    testImplementation("org.testcontainers:postgresql:1.20.6")
    testImplementation("org.testcontainers:junit-jupiter:1.20.6")
    testImplementation("com.nickanderssohn:declarative-jooq-dsl-runtime:1.0.1")
}

flyway {
    url = "jdbc:postgresql://localhost:5432/todolist"
    user = "postgres"
    password = "postgres"
}

jooq {
    version.set("3.19.19")
    configurations {
        create("main") {
            generateSchemaSourceOnCompilation.set(false)
            jooqConfiguration.apply {
                jdbc.apply {
                    driver = "org.postgresql.Driver"
                    url = "jdbc:postgresql://localhost:5432/todolist"
                    user = "postgres"
                    password = "postgres"
                }
                generator.apply {
                    name = "org.jooq.codegen.KotlinGenerator"
                    database.apply {
                        name = "org.jooq.meta.postgres.PostgresDatabase"
                        inputSchema = "public"
                        excludes = "flyway_schema_history"
                    }
                    generate.apply {
                        isDeprecated = false
                        isRecords = true
                        isFluentSetters = true
                    }
                    target.apply {
                        packageName = "com.nickanderssohn.todolist.jooq"
                        directory = "${project.projectDir}/src/generated/jooq"
                    }
                }
            }
        }
    }
}

declarativeJooq {
    classesDir.set(layout.buildDirectory.dir("classes/kotlin/main"))
    outputPackage.set("com.nickanderssohn.generated.dsl")
    packageFilter.set("com.nickanderssohn.todolist.jooq.tables")
    outputDir.set(layout.projectDirectory.dir("src/test/kotlin"))
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
