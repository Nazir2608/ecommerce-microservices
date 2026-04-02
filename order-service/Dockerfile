# ─────────────────────────────────────────────────────────────────────────────
# Multi-stage Dockerfile — user-service
#
#   Multi-stage build:
#   Stage 1 (builder) — Full JDK + Maven: compile + package the JAR
#   Stage 2 (runtime) — JRE only: copy and run the JAR
#   Result: final image ~180MB instead of ~600MB with full JDK
#
#   Layered JAR (Spring Boot 2.3+):
#   Spring Boot JARs can be "extracted" into layers:
#     1. dependencies       (changes rarely — Maven libs)
#     2. spring-boot-loader (changes rarely)
#     3. snapshot-dependencies (changes occasionally)
#     4. application        (changes on every commit — your code)
#
#   Each layer = one Docker layer with its own cache entry.
#   Rebuilding only your code? Only layer 4 is rebuilt.
#   → Docker push sends only the changed layer, not the full 100MB deps.
# ─────────────────────────────────────────────────────────────────────────────

# ── Stage 1: Build ─────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Copy parent POM first — Docker caches this layer until pom.xml changes
COPY pom.xml .

# Download all dependencies before copying source code.
# This layer is cached unless pom.xml changes → subsequent builds skip download.
RUN mvn dependency:go-offline -q

# Copy source
COPY src ./src

# Build JAR (skip tests — tests run in CI pipeline separately)
RUN mvn package -DskipTests -q

# Extract layered JAR into separate directories
RUN java -Djarmode=layertools \
    -jar target/*.jar \
    extract --destination target/extracted

# ── Stage 2: Runtime ───────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security: run as a non-root user
# Never run containers as root in production
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser:appgroup

WORKDIR /app

# Copy layered JAR contents — in order from least to most frequently changed.
# Docker only rebuilds layers that changed.
COPY --from=builder /build/target/extracted/dependencies/          ./
COPY --from=builder /build/target/extracted/spring-boot-loader/    ./
COPY --from=builder /build/target/extracted/snapshot-dependencies/ ./
COPY --from=builder /build/target/extracted/application/           ./

# ── JVM tuning for containers ──────────────────────────────────────────────────
# -XX:+UseContainerSupport      → respect Docker cgroup memory limits (not host RAM)
# -XX:MaxRAMPercentage=75.0     → use max 75% of container memory for heap
# -Djava.security.egd=...urandom → faster secure random (avoid entropy wait)
ENV JAVA_OPTS="\
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+OptimizeStringConcat \
  -Djava.security.egd=file:/dev/./urandom \
  -Dspring.profiles.active=default"

# ── Health check ──────────────────────────────────────────────────────────────
HEALTHCHECK \
  --interval=30s \
  --timeout=10s \
  --start-period=60s \
  --retries=3 \
  CMD wget --no-verbose --tries=1 --spider \
      http://localhost:${SERVER_PORT:-8081}/actuator/health || exit 1

EXPOSE 8081

# Use JarLauncher from the extracted layered JAR
ENTRYPOINT ["sh", "-c", \
  "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
