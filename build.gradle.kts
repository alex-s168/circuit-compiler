plugins {
    kotlin("jvm") version "1.9.23"
}

group = "me.alex_s168"
version = "1.0-SNAPSHOT"

repositories {
    maven {
        name = "alex's repo"
        url = uri("http://207.180.202.42:8080/libs")
        isAllowInsecureProtocol = true
    }

    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("me.alex_s168:blitz:0.17")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}