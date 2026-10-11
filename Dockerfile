# Build stage: a JDK 25 image runs the Maven wrapper. Tests are skipped here:
# they run against a real PostgreSQL in Testcontainers, which needs Docker and a
# Docker socket the image build does not have. The full suite runs outside the
# image, with ./mvnw verify.
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# Dependencies first, so source changes alone do not re-download them.
RUN ./mvnw --batch-mode dependency:go-offline
COPY src/ src/
RUN ./mvnw --batch-mode package -DskipTests

# Runtime stage: only a JRE, a non-root user and the jar.
FROM eclipse-temurin:25-jre-noble AS runtime
# curl exists only for the health check below.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system wallet \
    && useradd --system --gid wallet --create-home wallet
WORKDIR /app
COPY --from=build /build/target/wallet-service-*.jar app.jar
USER wallet
EXPOSE 8080
# Readiness includes the database, so "healthy" means the service can serve.
HEALTHCHECK --interval=5s --timeout=3s --start-period=90s --retries=12 \
    CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
