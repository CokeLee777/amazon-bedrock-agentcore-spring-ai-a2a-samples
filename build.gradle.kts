plugins {
    java
    id("org.springframework.boot") version "3.5.0" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("io.spring.javaformat") version "0.0.47" apply false
}

val springAiVersion by extra("1.1.2")
val awsSdkVersion by extra("2.42.9")
val a2aVersion by extra("0.3.2.Final")
val gsonVersion by extra("2.13.2")

subprojects {
    apply(plugin = "java")
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "io.spring.javaformat")

    group = "io.github.cokelee777"
    version = "0.0.1-SNAPSHOT"

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

    val springAiVer = rootProject.extra["springAiVersion"] as String
    val awsSdkVer = rootProject.extra["awsSdkVersion"] as String

    the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
        imports {
            mavenBom("org.springframework.ai:spring-ai-bom:$springAiVer")
            mavenBom("software.amazon.awssdk:bom:$awsSdkVer")
        }
    }

    dependencies {
        compileOnly("org.projectlombok:lombok")
        annotationProcessor("org.projectlombok:lombok")
        testImplementation("org.springframework.boot:spring-boot-starter-test")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }

    tasks.named<Test>("test") {
        useJUnitPlatform()
    }
}
