import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.10"
    `java-library`
    eclipse
    idea
    `maven-publish`
}

group = "com.github.bhlangonijr"
version = "1.0.0"

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://jitpack.io")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.10")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.10")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    implementation("org.python:jython:2.7.3b1")
    implementation("org.graalvm.js:js-scriptengine:24.1.2")
    implementation("org.graalvm.polyglot:js:24.1.2") {
        exclude(module = "truffle-runtime")
        exclude(module = "truffle-enterprise")
    }
    implementation("io.github.microutils:kotlin-logging:3.0.5")
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("org.slf4j:slf4j-simple:2.0.9")
    implementation("com.bazaarvoice.jolt:jolt-core:0.1.7")
    implementation("com.bazaarvoice.jolt:json-utils:0.1.7")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.0")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

tasks.test {
    when (System.getProperties()["test.type"]) {
        "integration" -> {
            include("**/*IntegrationTest.*")
            include("**/*IntegrationSpec.*")
        }
        "unit" -> {
            include("**/*Test.*")
            include("**/*Spec.*")
            exclude("**/*IntegrationTest.*")
            exclude("**/*IntegrationSpec.*")
        }
        "all" -> {
            include("**/*Test.*")
            include("**/*Spec.*")
        }
        else -> {
            //Default to unit
            include("**/*Test.*")
            include("**/*Spec.*")
            exclude("**/*IntegrationTest.*")
            exclude("**/*IntegrationSpec.*")
        }
    }

    useJUnitPlatform()
    afterTest(KotlinClosure2<TestDescriptor, TestResult, Any>({ descriptor, result ->
        val test = descriptor as org.gradle.api.internal.tasks.testing.TestDescriptorInternal
        println("\n${test.className} [${test.classDisplayName}] > ${test.name} [${test.displayName}]: ${result.resultType}")
    }))
}

task<Test>("integrationTest") {
    description = "Runs the integration tests"
    group = "verification"
    include("**/*IntegrationTest.*")
    include("**/*IntegrationSpec.*")

    useJUnitPlatform()
    afterTest(KotlinClosure2<TestDescriptor, TestResult, Any>({ descriptor, result ->
        val test = descriptor as org.gradle.api.internal.tasks.testing.TestDescriptorInternal
        println("\n${test.className} [${test.classDisplayName}] > ${test.name} [${test.displayName}]: ${result.resultType}")
    }))
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "17"
    }
}

publishing {
    publications {
        create<MavenPublication>("flubber") {
            from(components["java"])
        }
    }
}