# Official Eclipse Temurin images are published for linux/arm64 and linux/amd64.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
COPY src src
RUN chmod +x gradlew && ./gradlew --no-daemon bootJar

FROM eclipse-temurin:25-jre
RUN groupadd --system app \
    && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build /src/build/libs/app.jar app.jar
COPY healthcheck.sh /healthcheck.sh
RUN chmod 755 /healthcheck.sh
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
