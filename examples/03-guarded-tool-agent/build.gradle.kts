plugins {
    kotlin("jvm") version "2.3.0"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("ai.koog:koog-agents:1.0.0")
    implementation("ai.koog:koog-agents-additions:1.0.0-beta")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("dev.karan.koog.guardedtoolagent.MainKt")
}

tasks.test {
    useJUnitPlatform()
}
