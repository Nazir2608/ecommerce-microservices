# ─────────────────────────────────────────────
# Stage 1: Build
# ─────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy full project (important for multi-module)
COPY . .

# Build only user-service module
RUN mvn -pl user-service -am clean package -DskipTests

# Extract layered JAR
RUN java -Djarmode=layertools \
    -jar user-service/target/*.jar \
    extract --destination user-service/target/extracted


# ─────────────────────────────────────────────
# Stage 2: Runtime
# ─────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

# Install wget for healthcheck
RUN apk add --no-cache wget

# Security user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser:appgroup

WORKDIR /app

# Copy layers
COPY --from=builder /build/user-service/target/extracted/dependencies/          ./
COPY --from=builder /build/user-service/target/extracted/spring-boot-loader/    ./
COPY --from=builder /build/user-service/target/extracted/snapshot-dependencies/ ./
COPY --from=builder /build/user-service/target/extracted/application/           ./

# JVM options
ENV JAVA_OPTS="\
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -Djava.security.egd=file:/dev/./urandom \
  -Dspring.profiles.active=default"

# Healthcheck
HEALTHCHECK \
  --interval=30s \
  --timeout=10s \
  --start-period=60s \
  --retries=3 \
  CMD wget --no-verbose --tries=1 --spider \
      http://localhost:8081/actuator/health || exit 1

EXPOSE 8081

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]