plugins {
	`java-library`
}

dependencies {
	api(project(":spring-ai-a2a-model-chat-memory-repository-bedrock-agent-core"))
	api(project(":spring-ai-a2a-autoconfigure-model-chat-memory-repository-bedrock-agent-core"))
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
	enabled = false
}

tasks.named<Jar>("jar") {
	enabled = true
}
