# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /build
COPY pom.xml .
# Download deps first (layer-cache friendly)
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -DskipTests -q

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy

# Non-root user for security
RUN groupadd --system appgroup && useradd --system --gid appgroup appuser

WORKDIR /app

COPY --from=builder /build/target/user-metadata-service-*.jar app.jar

# Writable tmp dir for JVM
RUN mkdir -p /tmp && chown appuser:appgroup /tmp

USER appuser

EXPOSE 8080

# JVM tuning: small container-aware heap + GC logging
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
