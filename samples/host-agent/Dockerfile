# Amazon Bedrock AgentCore Runtime — host-agent container
#
# A2A protocol contract (see AWS docs runtime-a2a-protocol-contract):
#   - Platform: linux/arm64 (required)
#   - Listen: 0.0.0.0:8080
#   - POST /           → JSON-RPC 2.0 (message/send, etc.)
#   - GET /.well-known/agent-card.json → Agent discovery
#   - GET /ping        → health (200 + JSON)
#
# Build (from project root — ARM64 for AgentCore):
#   docker buildx build --platform linux/arm64 \
#     -f samples/host-agent/Dockerfile \
#     -t host-agent:arm64 --load .
#
# Runtime env (set in AgentCore / ECS task definition):
#   BEDROCK_REGION, BEDROCK_MODEL_ID, ORCHESTRATOR_URL,
#   ORDER_AGENT_URL, DELIVERY_AGENT_URL, PAYMENT_AGENT_URL,
#   A2A_CLIENT_TIMEOUT_SECONDS

# ---------- Build stage (JAR is bytecode; any arch) ----------
FROM eclipse-temurin:25-jdk-jammy AS builder

WORKDIR /app

# Full tree: settings.gradle.kts includes all subprojects; starter/memory modules must be present.
COPY . .

RUN chmod +x gradlew \
	&& ./gradlew :host-agent:bootJar --no-daemon -x test \
	&& cp samples/host-agent/build/libs/host-agent-*.jar /app/app.jar

# ---------- Runtime stage (must be ARM64 for AgentCore) ----------
FROM --platform=linux/arm64 eclipse-temurin:25-jre-jammy

WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app
USER app

COPY --from=builder --chown=app:app /app/app.jar /app/app.jar

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
