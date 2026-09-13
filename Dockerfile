FROM gradle:8.8-jdk21 AS build
WORKDIR /workspace
COPY build.gradle.kts settings.gradle.kts ./
COPY src ./src
RUN gradle --no-daemon check installDist

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/build/install/PlantStorage/ /app/
ENV WEB_ROOT=/app/webapp
ENV APP_HOST=0.0.0.0
USER 10001:10001
EXPOSE 8080
CMD ["java", "-XX:MaxRAMPercentage=70.0", "-cp", "/app/lib/*", "com.plantstorage.Main"]
