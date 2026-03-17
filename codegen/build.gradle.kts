plugins {
    kotlin("jvm")
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

dependencies {
    implementation("com.squareup:kotlinpoet:2.2.0")
    implementation("io.github.classgraph:classgraph:4.8.181")
    compileOnly("org.jooq:jooq:3.19.16")

    testImplementation("org.jooq:jooq:3.19.16")
    testImplementation("com.h2database:h2:2.3.232")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("com.github.tschuchortdev:kotlin-compile-testing:1.6.0")
    testImplementation(project(":dsl-runtime"))
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}
