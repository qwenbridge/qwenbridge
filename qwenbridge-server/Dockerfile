FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /app

COPY pom.xml ./pom.xml
COPY qwenbridge-core/pom.xml ./qwenbridge-core/pom.xml
COPY qwenbridge-plugin-sdk/pom.xml ./qwenbridge-plugin-sdk/pom.xml
COPY qwenbridge-commerce-pack/pom.xml ./qwenbridge-commerce-pack/pom.xml
COPY qwenbridge-library-pack/pom.xml ./qwenbridge-library-pack/pom.xml
COPY qwenbridge-server/pom.xml ./qwenbridge-server/pom.xml
COPY qwenbridge-java-sdk/pom.xml ./qwenbridge-java-sdk/pom.xml

RUN mvn -pl qwenbridge-server -am dependency:go-offline -B

COPY qwenbridge-core/src/main ./qwenbridge-core/src/main
COPY qwenbridge-commerce-pack/src ./qwenbridge-commerce-pack/src
COPY qwenbridge-library-pack/src ./qwenbridge-library-pack/src
COPY qwenbridge-server/src ./qwenbridge-server/src
COPY qwenbridge-plugin-sdk/src ./qwenbridge-plugin-sdk/src

RUN mvn -pl qwenbridge-server -am clean package -DskipTests

FROM eclipse-temurin:25-jre AS runtime

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 65532 nonroot \
    && useradd --uid 65532 --gid 65532 --no-create-home nonroot

WORKDIR /app

COPY --from=build --chown=65532:65532 /app/qwenbridge-server/target/quarkus-app/ /app/

# Platform state directory for qwenbridge.platform.store=file (QB-293); a volume mounted here
# inherits this ownership.
RUN mkdir -p /app/state && chown 65532:65532 /app/state

USER 65532:65532

EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
