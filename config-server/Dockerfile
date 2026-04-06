FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Copy root pom
COPY pom.xml .
# Strip <modules> to prevent Maven from looking for other modules
RUN sed -i '/<modules>/,/<\/modules>/d' pom.xml

# Copy submodule pom
COPY config-server/pom.xml config-server/

# Resolve dependencies for the submodule
RUN mvn -f config-server/pom.xml dependency:go-offline -q

# Copy submodule source
COPY config-server/src config-server/src

# Build the submodule
RUN mvn -f config-server/pom.xml package -DskipTests -q

# Extract layered JAR
RUN java -Djarmode=layertools -jar config-server/target/*.jar extract --destination config-server/target/extracted

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser:appgroup
WORKDIR /app
COPY --from=builder /build/config-server/target/extracted/dependencies/          ./
COPY --from=builder /build/config-server/target/extracted/spring-boot-loader/    ./
COPY --from=builder /build/config-server/target/extracted/snapshot-dependencies/ ./
COPY --from=builder /build/config-server/target/extracted/application/           ./
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"
HEALTHCHECK --interval=15s --timeout=10s --start-period=60s --retries=5 \
  CMD wget --no-verbose --tries=1 --spider \
      http://configuser:configpassword@localhost:8888/actuator/health || exit 1
EXPOSE 8888
ENTRYPOINT ["sh","-c","java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
