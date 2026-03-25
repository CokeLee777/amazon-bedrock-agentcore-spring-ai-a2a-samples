plugins {
	`java-library`
}

dependencies {
	api(project(":spring-ai-a2a-agent-common"))
	api(project(":spring-ai-a2a-autoconfigure-agent-common"))
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
	enabled = false
}

tasks.named<Jar>("jar") {
	enabled = true
}
