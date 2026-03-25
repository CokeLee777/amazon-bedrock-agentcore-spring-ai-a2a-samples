# order-agent container
#
# Build (from project root):
#   docker buildx build --platform linux/amd64 \
#     -f samples/order-agent/Dockerfile \
#     -t order-agent:latest --load .
#
# Runtime env:
#   BEDROCK_REGION, BEDROCK_MODEL_ID,
#   AGENT_URL,
#   AGENT_PORT

# ---------- Build stage ----------
FROM eclipse-temurin:25-jdk-jammy AS builder

WORKDIR /app

COPY . .

RUN chmod +x gradlew \
	&& ./gradlew :order-agent:bootJar --no-daemon -x test \
	&& cp samples/order-agent/build/libs/order-agent-*.jar /app/app.jar

# ---------- Runtime stage ----------
FROM eclipse-temurin:25-jre-jammy

WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app
USER app

COPY --from=builder --chown=app:app /app/app.jar /app/app.jar

EXPOSE 9000

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
