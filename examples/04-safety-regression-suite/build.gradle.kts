plugins {
    kotlin("jvm") version "2.3.0"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":guarded-tool-agent"))
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("dev.karan.koog.safetyregression.MainKt")
}

tasks.test {
    useJUnitPlatform()
}
