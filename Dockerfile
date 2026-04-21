# ── Stage 1: Build ────────────────────────────────────────────────────────────
# Uses the full JDK to compile and package the app into a fat JAR.
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app

# Copy Gradle wrapper and dependency declarations first.
# Docker caches this layer — dependencies are only re-downloaded when
# build.gradle.kts or settings.gradle.kts change, not on every code change.
COPY gradlew ./
COPY gradle/ gradle/
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# Now copy source and build the executable JAR
COPY src/ src/
RUN ./gradlew bootJar --no-daemon

# ── Stage 2: Run ──────────────────────────────────────────────────────────────
# Uses only the JRE (smaller image — no compiler, no Gradle, no source code).
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Create the log directory — docker-compose mounts a named volume here
# so logs persist across container restarts.
RUN mkdir -p /var/log/memento

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
