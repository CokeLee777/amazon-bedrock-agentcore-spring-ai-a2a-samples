dependencies {
	implementation("org.springframework.ai:spring-ai-client-chat")
	implementation("software.amazon.awssdk:bedrockagentcore")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
	enabled = false
}

tasks.named<Jar>("jar") {
	enabled = true
}
