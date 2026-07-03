FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN groupadd --system qwenbridge \
    && useradd --system --gid qwenbridge --home-dir /app --shell /usr/sbin/nologin qwenbridge

COPY --from=build /app/target/qwenbridge-*.jar app.jar

RUN sh -c 'test -f app.jar' \
    && chown -R qwenbridge:qwenbridge /app

USER qwenbridge

EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD sh -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /actuator/health HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n" >&3 && grep -q "\"status\":\"UP\"" <&3'

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
