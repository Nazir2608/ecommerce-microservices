FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Copy root pom
COPY pom.xml .
# Strip <modules> to prevent Maven from looking for other modules
RUN sed -i '/<modules>/,/<\/modules>/d' pom.xml

# Copy submodule pom
COPY api-gateway/pom.xml api-gateway/

# Resolve dependencies for the submodule
RUN mvn -f api-gateway/pom.xml dependency:go-offline -q

# Copy submodule source
COPY api-gateway/src api-gateway/src

# Build the submodule
RUN mvn -f api-gateway/pom.xml package -DskipTests -q

# Extract layered JAR
RUN java -Djarmode=layertools -jar api-gateway/target/*.jar extract --destination api-gateway/target/extracted

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser:appgroup
WORKDIR /app

# Copy layers from the correct path in builder
COPY --from=builder /build/api-gateway/target/extracted/dependencies/          ./
COPY --from=builder /build/api-gateway/target/extracted/spring-boot-loader/    ./
COPY --from=builder /build/api-gateway/target/extracted/snapshot-dependencies/ ./
COPY --from=builder /build/api-gateway/target/extracted/application/           ./

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

ENTRYPOINT ["sh","-c","java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
