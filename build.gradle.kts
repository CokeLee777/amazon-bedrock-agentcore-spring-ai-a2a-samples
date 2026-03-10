plugins {
    java
    id("org.springframework.boot") version "3.5.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("io.spring.javaformat") version "0.0.47"
}

group = "com.github.cokelee777"
version = "0.0.1-SNAPSHOT"
description = "amazon-bedrock-agentcore-spring-boot-samples"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

val springAiVersion = "1.1.2"
val awsSdkVersion = "2.42.9"
val a2aVersion = "0.3.2.Final"

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:$springAiVersion")
        mavenBom("software.amazon.awssdk:bom:$awsSdkVersion")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Spring AI
    implementation("org.springframework.ai:spring-ai-client-chat")

    // AWS Bedrock AgentCore Runtime
    implementation("software.amazon.awssdk:bedrockagentcore")

    // A2A SDK
    implementation("io.github.a2asdk:a2a-java-sdk-client:$a2aVersion")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
