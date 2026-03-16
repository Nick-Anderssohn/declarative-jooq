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
    testImplementation("org.testcontainers:testcontainers:1.20.4")
    testImplementation("org.testcontainers:postgresql:1.20.4")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
    testImplementation("org.postgresql:postgresql:42.7.4")

    // kotlin-compile-testing for compiling generated code
    testImplementation("com.github.tschuchortdev:kotlin-compile-testing:1.6.0")

    // Test framework
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

tasks.test {
    useJUnitPlatform()
    // Integration tests are slow, don't run in normal build
    // Run explicitly with: ./gradlew :integration-tests:test
}
