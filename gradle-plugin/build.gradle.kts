plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `maven-publish`
}

dependencies {
    implementation(project(":codegen"))
    // jOOQ is needed at runtime so the codegen task can load user-compiled TableImpl subclasses
    // via URLClassLoader with the Gradle worker's context classloader as parent
    implementation("org.jooq:jooq:3.19.16")
    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

gradlePlugin {
    plugins {
        create("declarativeJooq") {
            id = "com.nickanderssohn.declarative-jooq"
            implementationClass = "com.nickanderssohn.declarativejooq.gradle.DeclarativeJooqPlugin"
        }
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}
