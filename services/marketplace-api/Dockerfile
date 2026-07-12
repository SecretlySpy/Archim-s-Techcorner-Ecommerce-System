# Multi-stage build. Build context is the repo root (see compose.yaml), so that the
# module and the canonical database/migrations directory keep their relative layout.

# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Copy the migrations first (the module's maven-resources step reads ../../database).
COPY database ./database
COPY services/marketplace-api ./services/marketplace-api
WORKDIR /build/services/marketplace-api
RUN mvn -q -B -DskipTests package

# --- Runtime stage (non-root, JRE only) ---
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
RUN useradd --uid 10001 --create-home appuser
COPY --from=build /build/services/marketplace-api/target/marketplace-api-*.jar /app/app.jar
USER 10001
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --retries=10 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q UP || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
