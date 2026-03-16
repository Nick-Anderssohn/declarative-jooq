plugins {
    kotlin("jvm")
}

dependencies {
    compileOnly("org.jooq:jooq:3.19.16")
    testImplementation("org.jooq:jooq:3.19.16")
    testImplementation("com.h2database:h2:2.3.232")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

tasks.test {
    useJUnitPlatform()
}
