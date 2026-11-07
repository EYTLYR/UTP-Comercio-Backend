# Nearby API · imagen de producción (multi-stage, Java 21)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 nearby && mkdir -p /app/uploads && chown nearby /app/uploads
COPY --from=build /app/target/*.jar /app/app.jar
USER nearby
ENV SPRING_PROFILES_ACTIVE=prod \
    NEARBY_STORAGE_DIR=/app/uploads \
    TZ=America/Lima
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
