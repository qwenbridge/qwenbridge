FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests

FROM gcr.io/distroless/java21-debian12:nonroot

WORKDIR /app

COPY --from=build --chown=nonroot:nonroot /app/target/qwenbridge-*.jar /app/app.jar

USER nonroot:nonroot

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]