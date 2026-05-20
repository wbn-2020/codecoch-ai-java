# ============================================================
# CodeCoachAI 统一 Dockerfile（多阶段构建）
# 用法：在根目录执行
#   docker build --build-arg SERVICE=codecoachai-auth -t codecoachai-auth:latest .
# ============================================================

# ---------- Stage 1: Build ----------
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build
COPY pom.xml .
COPY codecoachai-common ./codecoachai-common
COPY codecoachai-gateway ./codecoachai-gateway
COPY codecoachai-auth ./codecoachai-auth
COPY codecoachai-user ./codecoachai-user
COPY codecoachai-ai ./codecoachai-ai
COPY codecoachai-resume ./codecoachai-resume
COPY codecoachai-interview ./codecoachai-interview
COPY codecoachai-question ./codecoachai-question
COPY codecoachai-file ./codecoachai-file
COPY codecoachai-system ./codecoachai-system
COPY codecoachai-task ./codecoachai-task
COPY codecoachai-search ./codecoachai-search

# 先下载依赖（利用 Docker 缓存层）
RUN mvn dependency:go-offline -DskipTests -q || true

# 构建
RUN mvn clean package -DskipTests -pl ${SERVICE} -am -q

# ---------- Stage 2: Runtime ----------
FROM eclipse-temurin:21-jre-alpine

ARG SERVICE
ENV SERVICE_NAME=${SERVICE}
ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError"

RUN apk add --no-cache tzdata curl && \
    ln -sf /usr/share/zoneinfo/$TZ /etc/localtime

WORKDIR /app

# 从 builder 阶段复制 jar
COPY --from=builder /build/${SERVICE}/target/*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
