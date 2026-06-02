# Build stage
FROM eclipse-temurin:24-jdk-alpine AS builder
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts gradlew ./
COPY gradle gradle
RUN --mount=type=cache,id=arepos-gradle-cache,target=/root/.gradle \
    ./gradlew --no-daemon dependencies
COPY src src
RUN --mount=type=cache,id=arepos-gradle-cache,target=/root/.gradle \
    ./gradlew --no-daemon bootJar

# Runtime stage
FROM eclipse-temurin:24-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=builder /app/build/libs/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1
