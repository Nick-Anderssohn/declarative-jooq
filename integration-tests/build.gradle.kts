plugins {
    kotlin("jvm")
}

dependencies {
    testImplementation(project(":dsl-runtime"))
    testImplementation(project(":codegen"))

    // jOOQ runtime + codegen
    testImplementation("org.jooq:jooq:3.19.16")
    testImplementation("org.jooq:jooq-codegen:3.19.16")
    testImplementation("org.jooq:jooq-meta:3.19.16")

    // Testcontainers Postgres
    testImplementation("org.testcontainers:testcontainers:1.20.6")
    testImplementation("org.testcontainers:postgresql:1.20.6")
    testImplementation("org.testcontainers:junit-jupiter:1.20.6")
    testImplementation("org.postgresql:postgresql:42.7.4")

    // kotlin-compile-testing for compiling generated code
    testImplementation("com.github.tschuchortdev:kotlin-compile-testing:1.6.0")

    // Test framework
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")

    // SLF4J simple to see Testcontainers log output (diagnose Docker detection)
    testImplementation("org.slf4j:slf4j-simple:2.0.16")
}

tasks.test {
    useJUnitPlatform()
    // Integration tests are slow, don't run in normal build
    // Run explicitly with: ./gradlew :integration-tests:test

    // Help Testcontainers find the working Docker daemon socket on macOS with Docker Desktop.
    // Docker Desktop on macOS exposes docker.raw.sock (real daemon) and docker-cli.sock (proxy).
    // Testcontainers needs the real daemon socket.
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
    // Docker Desktop requires API version >= 1.44; Testcontainers bundles docker-java which defaults to 1.32
    environment("DOCKER_API_VERSION", "1.44")
    systemProperty("api.version", "1.44")
    // Disable Ryuk (resource reaper) to avoid socket-mounting issue with Docker Desktop on macOS.
    // Resources are cleaned up naturally after the JVM process exits.
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")
}
