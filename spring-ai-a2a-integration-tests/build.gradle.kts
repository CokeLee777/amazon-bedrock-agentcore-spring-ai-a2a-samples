dependencies {
    testImplementation(project(":spring-ai-a2a-agent-common"))
    testImplementation(project(":spring-ai-a2a-autoconfigure-agent-common"))
    testImplementation(project(":host-agent"))
    testImplementation(project(":order-agent"))
    testImplementation(project(":delivery-agent"))
    testImplementation(project(":payment-agent"))
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.ai:spring-ai-client-chat")
}

tasks.named<Test>("test") {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

tasks.named<Jar>("jar") {
    enabled = false
}
