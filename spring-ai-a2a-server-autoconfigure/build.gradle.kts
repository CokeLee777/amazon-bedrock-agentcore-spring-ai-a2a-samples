dependencies {
	implementation(project(":spring-ai-a2a-server"))

	// Spring AI Chat Client (for @ConditionalOnClass)
	implementation("org.springframework.ai:spring-ai-client-chat")

	// Spring Boot Web Starter (needed for embedded server in tests)
	implementation("org.springframework.boot:spring-boot-autoconfigure")

	// Spring Boot Configuration Processor (for @ConfigurationProperties)
	implementation("org.springframework.boot:spring-boot-configuration-processor")

	// Spring Boot Web Starter (needed for embedded server in tests)
	testImplementation("org.springframework.boot:spring-boot-starter-web")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
	enabled = false
}

tasks.named<Jar>("jar") {
	enabled = true
}
