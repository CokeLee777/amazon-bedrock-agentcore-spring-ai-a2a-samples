dependencies {
	// Spring Web MVC
	implementation("org.springframework.boot:spring-boot-starter-web")

	// Spring WebFlux (for reactive streaming support)
	implementation("org.springframework.boot:spring-boot-starter-webflux")

	// Spring AI Chat Client (optional for SpringAIAgentExecutor)
	implementation("org.springframework.ai:spring-ai-client-chat")

	// Spring Boot Web Starter (needed for embedded server in tests)
	implementation("org.springframework.boot:spring-boot-autoconfigure")

	// Spring Boot Configuration Processor (for @ConfigurationProperties)
	implementation("org.springframework.boot:spring-boot-configuration-processor")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
	enabled = false
}

tasks.named<Jar>("jar") {
	enabled = true
}
