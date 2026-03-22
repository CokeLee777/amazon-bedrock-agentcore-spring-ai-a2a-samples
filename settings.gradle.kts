rootProject.name = "amazon-bedrock-agentcore-spring-ai-a2a-samples"

include("spring-ai-a2a-server")
include("spring-ai-a2a-server-autoconfigure")
include("agent-common")
include("host-agent")
include("order-agent")
include("delivery-agent")
include("payment-agent")
include("integration-tests")

project(":host-agent").projectDir = file("agents/host-agent")
project(":order-agent").projectDir = file("agents/order-agent")
project(":delivery-agent").projectDir = file("agents/delivery-agent")
project(":payment-agent").projectDir = file("agents/payment-agent")
