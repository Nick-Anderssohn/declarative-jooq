plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `maven-publish`
}

dependencies {
    implementation(project(":codegen"))
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
}
