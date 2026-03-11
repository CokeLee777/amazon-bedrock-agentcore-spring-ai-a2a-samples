val a2aVersion = rootProject.extra["a2aVersion"] as String
val gsonVersion = rootProject.extra["gsonVersion"] as String

dependencies {
    implementation("io.github.a2asdk:a2a-java-sdk-client:$a2aVersion")
    implementation("io.github.a2asdk:a2a-java-sdk-client-transport-jsonrpc:$a2aVersion")
    implementation("com.google.code.gson:gson:$gsonVersion")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

tasks.named<Jar>("jar") {
    enabled = true
}
