plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    id("com.vanniktech.maven.publish") version "0.35.0"
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = false)
    signAllPublications()

    coordinates("com.nickanderssohn", "declarative-jooq-gradle-plugin", project.version.toString())

    pom {
        name.set("declarative-jooq Gradle Plugin")
        description.set("Gradle plugin that generates type-safe Kotlin DSL builders from jOOQ TableImpl classes for declarative test data creation.")
        inceptionYear.set("2024")
        url.set("https://github.com/nickanderssohn/declarative-jooq")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        developers {
            developer {
                id.set("nickanderssohn")
                name.set("Nick Anderssohn")
                url.set("https://github.com/nickanderssohn/")
            }
        }

        scm {
            url.set("https://github.com/nickanderssohn/declarative-jooq/")
            connection.set("scm:git:git://github.com/nickanderssohn/declarative-jooq.git")
            developerConnection.set("scm:git:ssh://git@github.com/nickanderssohn/declarative-jooq.git")
        }
    }
}

tasks.withType<Sign>().configureEach {
    enabled = project.findProperty("signingInMemoryKey") != null
}

gradlePlugin {
    plugins {
        create("declarativeJooq") {
            id = "com.nickanderssohn.declarative-jooq"
            implementationClass = "com.nickanderssohn.declarativejooq.gradle.DeclarativeJooqPlugin"
        }
    }
}

dependencies {
    implementation(project(":codegen"))
    implementation("org.jooq:jooq:3.19.16")
    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}
