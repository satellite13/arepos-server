import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    kotlin("plugin.jpa") version "2.2.21"
    id("org.springframework.boot") version "3.5.7"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "ru.kvader"
version = "0.0.1-SNAPSHOT"

val mockitoAgent = configurations.create("mockitoAgent")

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(24)
    }
    sourceCompatibility = JavaVersion.VERSION_24
    targetCompatibility = JavaVersion.VERSION_24
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.liquibase:liquibase-core")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.postgresql:postgresql")
    implementation("com.vladmihalcea:hibernate-types-60:2.21.1")
    implementation("jakarta.persistence:jakarta.persistence-api:3.1.0")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.mockito.core)
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    mockitoAgent("net.bytebuddy:byte-buddy-agent:1.18.1")
}

kotlin {
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(24)
    }
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget("24")
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks {
    bootBuildImage {
        imageName.set("arch/${rootProject.name}:${rootProject.version}")
        environment.set(mapOf(
            "BP_JVM_VERSION" to "25",
            "BP_IMAGE_TAG" to "${rootProject.version}"
        ))
        // Используем paketobuildpacks/builder-jammy-base для более легкого образа
        builder.set("paketobuildpacks/builder-jammy-base:latest")
        // Или paketobuildpacks/builder-jammy-tiny для минимального размера
        // builder.set("paketobuildpacks/builder-jammy-tiny:latest")
    }
    test {
        useJUnitPlatform()
        val agentFile = mockitoAgent.singleFile
        jvmArgs(
            "-XX:+EnableDynamicAgentLoading",
            "-javaagent:${agentFile.absolutePath}"
        )
    }
}