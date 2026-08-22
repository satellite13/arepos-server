import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    kotlin("plugin.jpa") version "2.2.21"
    id("org.springframework.boot") version "3.5.9"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "ru.kvader"
version = "0.14.1"

val mockitoAgent = configurations.create("mockitoAgent")
val reflectiveAccessOpens = listOf(
    "--enable-final-field-mutation=ALL-UNNAMED",
    "--add-opens=java.base/java.lang=ALL-UNNAMED",
    "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
    "--add-opens=java.base/java.util=ALL-UNNAMED",
    "--add-opens=java.base/java.time=ALL-UNNAMED"
)
// Ehcache на JDK 24+ и CDS при -javaagent (Mockito) — косметические предупреждения в test output.
val testNoiseSuppressionJvmArgs = listOf(
    "--sun-misc-unsafe-memory-access=allow",
    "-Xshare:off"
)

java {
    sourceCompatibility = JavaVersion.VERSION_24
    targetCompatibility = JavaVersion.VERSION_24
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.liquibase:liquibase-core")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.postgresql:postgresql")
    implementation("com.vladmihalcea:hibernate-types-60:2.21.1")
    implementation("org.hibernate.orm:hibernate-jcache")
    implementation("org.ehcache:ehcache")
    runtimeOnly("org.glassfish.jaxb:jaxb-runtime:2.3.9")
    implementation("jakarta.persistence:jakarta.persistence-api:3.1.0")
    implementation("io.minio:minio:8.6.0")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    // Used by custom Keycloak-compatible OIDC client (ID token claims parsing)
    implementation("com.nimbusds:nimbus-jose-jwt:9.48")
    implementation("com.squareup.okhttp3:okhttp")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.4.0")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6")
    implementation("net.javacrumbs.shedlock:shedlock-spring:7.7.0")
    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:7.7.0")
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
        jvmTarget.set(JvmTarget.JVM_24)
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xannotation-default-target=param-property",
            "-Xemit-jvm-type-annotations"
        )
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
        jvmArgs(reflectiveAccessOpens)
        jvmArgs(testNoiseSuppressionJvmArgs)
    }
    withType<JavaExec>().configureEach {
        jvmArgs(reflectiveAccessOpens)
    }
}

springBoot {
    buildInfo()
}