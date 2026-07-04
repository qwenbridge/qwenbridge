FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml ./pom.xml
COPY qwenbridge-server/pom.xml ./qwenbridge-server/pom.xml
COPY qwenbridge-java-sdk/pom.xml ./qwenbridge-java-sdk/pom.xml
COPY qwenbridge-spring-boot-starter/pom.xml ./qwenbridge-spring-boot-starter/pom.xml
COPY examples/java-sdk-example/pom.xml ./examples/java-sdk-example/pom.xml
COPY examples/spring-boot-starter-example/pom.xml ./examples/spring-boot-starter-example/pom.xml

RUN mvn -pl qwenbridge-server -am dependency:go-offline -B

COPY qwenbridge-server/src ./qwenbridge-server/src

RUN mvn -pl qwenbridge-server -am clean package -DskipTests

FROM gcr.io/distroless/java21-debian12:nonroot

WORKDIR /app

COPY --from=build --chown=nonroot:nonroot /app/qwenbridge-server/target/qwenbridge-*.jar /app/app.jar

USER nonroot:nonroot

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
