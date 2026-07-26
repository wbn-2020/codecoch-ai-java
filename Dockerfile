# syntax=docker/dockerfile:1.7

ARG JAVA_VERSION=17

FROM eclipse-temurin:${JAVA_VERSION}-jdk-alpine AS health-probe-builder
WORKDIR /health-probe
COPY scripts/docker/HealthProbe.java .
RUN javac --release 17 -d classes HealthProbe.java

FROM maven:3.9.9-eclipse-temurin-17-alpine AS source-builder
ARG SERVICE
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

RUN case "${SERVICE}" in \
      codecoachai-gateway|codecoachai-auth|codecoachai-user|codecoachai-ai|\
      codecoachai-resume|codecoachai-interview|codecoachai-question|\
      codecoachai-file|codecoachai-system|codecoachai-task|codecoachai-search) ;; \
      *) echo "Unsupported SERVICE: ${SERVICE}" >&2; exit 2 ;; \
    esac \
    && mvn -B -ntp -DskipTests -pl "${SERVICE}" -am package \
    && set -- "${SERVICE}"/target/*.jar \
    && [ "$#" -eq 1 ] \
    && cp "$1" /tmp/app.jar

FROM scratch AS prebuilt-artifact
ARG JAR_FILE=__prebuilt_jar_not_provided__
COPY ${JAR_FILE} /app.jar

FROM eclipse-temurin:${JAVA_VERSION}-jre-alpine AS runtime-base
ARG SERVICE_PORT=8080

ENV TZ=Asia/Shanghai \
    APP_JAVA_TOOL_OPTIONS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app" \
    HEALTHCHECK_URL="http://127.0.0.1:${SERVICE_PORT}/actuator/health" \
    HEALTHCHECK_TIMEOUT_MILLIS=3000 \
    HEALTHCHECK_EXPECTED_STATUS=UP \
    HEALTH_STARTUP_TIMEOUT_SECONDS=180 \
    HEALTH_MONITOR_INTERVAL_SECONDS=10 \
    HEALTH_MONITOR_FAILURE_THRESHOLD=6

RUN apk add --no-cache tzdata \
    && addgroup -S -g 10001 codecoachai \
    && adduser -S -D -H -u 10001 -G codecoachai codecoachai \
    && mkdir -p /app /opt/codecoachai/health-probe \
    && chown codecoachai:codecoachai /app \
    && chmod 0750 /app \
    && chmod 0755 /opt/codecoachai/health-probe

WORKDIR /app
COPY --from=health-probe-builder /health-probe/classes/ /opt/codecoachai/health-probe/
COPY --chmod=0555 scripts/docker/entrypoint.sh /usr/local/bin/codecoachai-entrypoint
RUN sed -i 's/\r$//' /usr/local/bin/codecoachai-entrypoint

USER 10001:10001
EXPOSE ${SERVICE_PORT}

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD JAVA_TOOL_OPTIONS='' java -cp /opt/codecoachai/health-probe HealthProbe \
    "$HEALTHCHECK_URL" "$HEALTHCHECK_TIMEOUT_MILLIS" "$HEALTHCHECK_EXPECTED_STATUS"

ENTRYPOINT ["/usr/local/bin/codecoachai-entrypoint"]

FROM runtime-base AS runtime-prebuilt
COPY --from=prebuilt-artifact --chown=10001:10001 /app.jar /app/app.jar

FROM runtime-base AS runtime
COPY --from=source-builder --chown=10001:10001 /tmp/app.jar /app/app.jar
