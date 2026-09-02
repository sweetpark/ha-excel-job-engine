FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app
COPY gradlew gradlew.bat build.gradle settings.gradle ./
COPY gradle/ gradle/
COPY src/ src/
RUN ./gradlew bootJar --no-daemon || ./gradlew jar --no-daemon

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]