plugins {
    kotlin("jvm") version "1.9.22"
    application
    id("com.gradleup.shadow") version "8.3.6"
}

group = "com.eventb"
version = "1.0.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation("de.hhu.stups:rodin-eventb-ast:3.8.0")
    implementation("com.github.ajalt.clikt:clikt:4.2.2")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testImplementation("org.assertj:assertj-core:3.27.7")
    implementation("org.json:json:20251224")
}

application {
    mainClass.set("com.eventb.checker.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveClassifier.set("all")
    manifest {
        attributes("Main-Class" to "com.eventb.checker.MainKt")
    }
}
