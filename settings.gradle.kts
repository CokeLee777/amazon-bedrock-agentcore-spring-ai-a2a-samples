rootProject.name = "amazon-bedrock-agentcore-spring-ai-a2a-samples"

include("spring-ai-a2a-server")
include("spring-ai-a2a-server-autoconfigure")
include("agent-common")
include("host-agent")
include("order-agent")
include("delivery-agent")
include("payment-agent")
include("integration-tests")

include("spring-ai-a2a-model-chat-memory-repository-bedrock-agent-core")
project(":spring-ai-a2a-model-chat-memory-repository-bedrock-agent-core")
	.projectDir = file("memory/repository/spring-ai-a2a-model-chat-memory-repository-bedrock-agent-core")

include("spring-ai-a2a-autoconfigure-model-chat-memory-repository-bedrock-agent-core")
project(":spring-ai-a2a-autoconfigure-model-chat-memory-repository-bedrock-agent-core")
	.projectDir = file("auto-configurations/models/chat/memory/repository/spring-ai-a2a-autoconfigure-model-chat-memory-repository-bedrock-agent-core")

project(":host-agent").projectDir = file("agents/host-agent")
project(":order-agent").projectDir = file("agents/order-agent")
project(":delivery-agent").projectDir = file("agents/delivery-agent")
project(":payment-agent").projectDir = file("agents/payment-agent")
