rootProject.name = "spring-ai-a2a"

// Spring AI A2A AutoConfiguration
include("spring-ai-a2a-autoconfigure-agent-common")
project(":spring-ai-a2a-autoconfigure-agent-common")
	.projectDir = file("auto-configurations/agent/common/spring-ai-a2a-autoconfigure-agent-common")

include("spring-ai-a2a-autoconfigure-model-chat-memory-repository-bedrock-agent-core")
project(":spring-ai-a2a-autoconfigure-model-chat-memory-repository-bedrock-agent-core")
	.projectDir = file("auto-configurations/models/chat/memory/repository/spring-ai-a2a-autoconfigure-model-chat-memory-repository-bedrock-agent-core")

include("spring-ai-a2a-autoconfigure-server")
project(":spring-ai-a2a-autoconfigure-server")
	.projectDir = file("auto-configurations/server/spring-ai-a2a-autoconfigure-server")

// Spring AI A2A Memory
include("spring-ai-a2a-model-chat-memory-repository-bedrock-agent-core")
project(":spring-ai-a2a-model-chat-memory-repository-bedrock-agent-core")
	.projectDir = file("memory/repository/spring-ai-a2a-model-chat-memory-repository-bedrock-agent-core")

// Samples
include("host-agent")
project(":host-agent").projectDir = file("samples/host-agent")

include("order-agent")
project(":order-agent").projectDir = file("samples/order-agent")

include("delivery-agent")
project(":delivery-agent").projectDir = file("samples/delivery-agent")

include("payment-agent")
project(":payment-agent").projectDir = file("samples/payment-agent")

// Spring AI A2A
include("spring-ai-a2a-agent-common")
include("spring-ai-a2a-integration-tests")
include("spring-ai-a2a-server")

include("spring-ai-a2a-starter-agent-common")
project(":spring-ai-a2a-starter-agent-common")
	.projectDir = file("spring-ai-a2a-starters/spring-ai-a2a-starter-agent-common")

include("spring-ai-a2a-starter-server")
project(":spring-ai-a2a-starter-server")
	.projectDir = file("spring-ai-a2a-starters/spring-ai-a2a-starter-server")

include("spring-ai-a2a-starter-model-chat-memory-repository-bedrock-agent-core")
project(":spring-ai-a2a-starter-model-chat-memory-repository-bedrock-agent-core")
	.projectDir = file("spring-ai-a2a-starters/spring-ai-a2a-starter-model-chat-memory-repository-bedrock-agent-core")
