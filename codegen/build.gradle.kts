plugins {
    kotlin("jvm")
    id("com.vanniktech.maven.publish") version "0.35.0"
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = false)
    signAllPublications()

    coordinates("com.nickanderssohn", "declarative-jooq-codegen", project.version.toString())

    pom {
        name.set("declarative-jooq Code Generator")
        description.set("Code generator that produces type-safe Kotlin DSL builders from jOOQ TableImpl classes for declarative test data creation.")
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
    dependsOn(":dsl-runtime:testClasses")
}
