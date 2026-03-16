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
            id = "com.example.declarative-jooq"
            implementationClass = "com.example.declarativejooq.gradle.DeclarativeJooqPlugin"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
