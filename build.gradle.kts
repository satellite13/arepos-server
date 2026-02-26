plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    kotlin("plugin.jpa") version "2.2.21"
    id("org.springframework.boot") version "3.5.7"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "ru.kvader"
version = "0.2.0"

val mockitoAgent = configurations.create("mockitoAgent")

java {
    sourceCompatibility = JavaVersion.VERSION_24
    targetCompatibility = JavaVersion.VERSION_24
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.liquibase:liquibase-core")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.postgresql:postgresql")
    implementation("com.vladmihalcea:hibernate-types-60:2.21.1")
    implementation("jakarta.persistence:jakarta.persistence-api:3.1.0")
    implementation("io.minio:minio:8.5.13")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.mockitoCore)
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    mockitoAgent("net.bytebuddy:byte-buddy-agent:1.18.1")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks {
    bootBuildImage {
        imageName.set("arch/${rootProject.name}:${rootProject.version}")
        environment.set(
            mapOf(
                "BP_JVM_VERSION" to "25",
                "BP_IMAGE_TAG" to "${rootProject.version}"
            )
        )
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

springBoot {
    buildInfo()
}