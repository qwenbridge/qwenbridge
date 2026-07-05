# Spring Boot Starter Example

This example shows how to add the QwenBridge Spring Boot Starter to a Spring Boot application and expose a small controller that calls QwenBridge.

Location:

```text
examples/spring-boot-starter-example
```

## Run

Install the starter locally:

```bash
mvn -pl qwenbridge-java-sdk,qwenbridge-spring-boot-starter install
```

Run the example:

```bash
mvn -f examples/spring-boot-starter-example/pom.xml spring-boot:run
```

The example configuration is in:

```text
examples/spring-boot-starter-example/src/main/resources/application.yml
```

Set the QwenBridge base URL to the server instance you want to call.
