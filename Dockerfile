FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -q package -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 1001 nearby
COPY --from=build --chown=nearby:nearby /build/target/*.jar /app/app.jar
# Spring importa este archivo automáticamente desde el directorio de trabajo.
# Contiene secretos: esta imagen debe distribuirse únicamente de forma privada.
COPY --chown=nearby:nearby --chmod=0400 .env /app/.env
USER nearby
ENV TZ=America/Lima PORT=8080 DOCKER_COMPOSE_ENABLED=false
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
  CMD curl --fail --silent "http://localhost:${PORT:-8080}/actuator/health" > /dev/null || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

