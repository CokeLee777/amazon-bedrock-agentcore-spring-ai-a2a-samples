dependencies {
	implementation(project(":spring-ai-a2a-starter-agent-common"))
	implementation(project(":spring-ai-a2a-starter-model-chat-memory-repository-bedrock-agent-core"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.ai:spring-ai-starter-model-bedrock-converse")
    implementation("org.springframework.ai:spring-ai-client-chat")
}
