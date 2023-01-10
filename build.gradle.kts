plugins {
    id("org.jetbrains.kotlin.jvm") version "1.8.0"
    id("org.jetbrains.kotlin.kapt") version "1.8.0"
    id("org.jetbrains.kotlin.plugin.allopen") version "1.8.0"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("io.micronaut.application") version "3.6.7"
    id("com.google.cloud.tools.jib") version "3.3.1"
    id("org.sonarqube") version "3.5.0.2730"
    jacoco
}

version = "0.3"
group = "com.neutrine.knostr"

val kotlinVersion = project.properties["kotlinVersion"]
repositories {
    mavenCentral()
}

dependencies {
    kapt("io.micronaut.data:micronaut-data-processor")
    kapt("io.micronaut:micronaut-http-validation")
    implementation("io.micronaut:micronaut-http-client")
    implementation("io.micronaut:micronaut-jackson-databind")
    implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")

    implementation("io.micronaut.data:micronaut-data-jdbc")
    runtimeOnly("io.micronaut.sql:micronaut-jdbc-hikari") // ?
    annotationProcessor("io.micronaut.data:micronaut-data-processor")
    runtimeOnly("org.postgresql:postgresql")
    implementation("io.micronaut.flyway:micronaut-flyway")

    implementation("io.micronaut.tracing:micronaut-tracing-opentelemetry-http")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    implementation("io.micronaut:micronaut-management")
    implementation("io.micronaut.micrometer:micronaut-micrometer-core")
    annotationProcessor("io.micronaut.micrometer:micronaut-micrometer-annotation")
    annotationProcessor("io.micronaut.tracing:micronaut-tracing-opentelemetry-annotation")

    implementation("jakarta.annotation:jakarta.annotation-api")
    implementation("jakarta.persistence:jakarta.persistence-api:3.1.0")

    implementation("jakarta.annotation:jakarta.annotation-api")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.6.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.6.4")

    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")

    implementation("io.github.microutils:kotlin-logging:1.6.22")
    implementation("org.apache.logging.log4j:log4j-to-slf4j:2.19.0")

    api("net.logstash.logback:logstash-logback-encoder:7.2")
    runtimeOnly("ch.qos.logback:logback-classic")

    compileOnly("org.graalvm.nativeimage:svm")

    implementation("io.micronaut:micronaut-validation")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    testCompileOnly("org.junit.jupiter:junit-jupiter-params:5.9.0")
    testImplementation("io.mockk:mockk:1.13.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.4")
    testImplementation("org.testcontainers:postgresql:1.17.6")
}

application {
    mainClass.set("com.neutrine.knostr.ApplicationKt")
}
java {
    sourceCompatibility = JavaVersion.toVersion("17")
}

allOpen {
    annotations("io.micronaut.aop.Around", "jakarta.inject.Singleton", "io.micronaut.websocket.annotation.ServerWebSocket")
}

tasks {
    compileKotlin {
        kotlinOptions {
            jvmTarget = "17"
        }
    }
    compileTestKotlin {
        kotlinOptions {
            jvmTarget = "17"
        }
    }
    jacocoTestReport {
        reports {
            xml.required.set(true)
        }
    }
}
graalvmNative.toolchainDetection.set(false)
micronaut {
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("com.neutrine.knostr.*")
    }
}
