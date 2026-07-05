# Java SDK Example

The Java SDK example demonstrates synchronous analysis, asynchronous analysis, and typed streaming consumption.

Location:

```text
examples/java-sdk-example
```

## Run

Build the SDK first:

```bash
mvn -pl qwenbridge-java-sdk install
```

Then run the example module:

```bash
mvn -f examples/java-sdk-example/pom.xml compile exec:java
```

Review the source files for focused examples:

- `SyncSearchAnalyzeExample.java`
- `AsyncSearchAnalyzeExample.java`
- `TypedStreamingExample.java`

Configure the example to point to a running QwenBridge server before execution.
