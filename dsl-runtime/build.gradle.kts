plugins {
    kotlin("jvm")
    id("com.vanniktech.maven.publish") version "0.35.0"
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = false)
    signAllPublications()

    coordinates("com.nickanderssohn", "declarative-jooq-dsl-runtime", project.version.toString())

    pom {
        name.set("declarative-jooq DSL Runtime")
        description.set("Declarative test data creation for jOOQ — handles insertion order, FK assignment, and result assembly.")
        inceptionYear.set("2026")
        url.set("https://github.com/Nick-Anderssohn/declarative-jooq")

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
                url.set("https://github.com/Nick-Anderssohn")
            }
        }

        scm {
            url.set("https://github.com/Nick-Anderssohn/declarative-jooq/")
            connection.set("scm:git:git://github.com/Nick-Anderssohn/declarative-jooq.git")
            developerConnection.set("scm:git:ssh://git@github.com/Nick-Anderssohn/declarative-jooq.git")
        }
    }
}

tasks.withType<Sign>().configureEach {
    enabled = project.findProperty("signingInMemoryKey") != null
}

dependencies {
    compileOnly("org.jooq:jooq:3.19.16")
    testImplementation("org.jooq:jooq:3.19.16")
    testImplementation("com.h2database:h2:2.3.232")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}
