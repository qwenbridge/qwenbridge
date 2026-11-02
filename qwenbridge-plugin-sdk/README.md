# QwenBridge Plugin and Pack SDK (Java 25)

This artifact depends only on the JDK. Existing `QwenBridgePlugin` remains the
trusted extension SPI. New `io.qwenbridge.pack` contracts provide versioned
Domain, Connector and Policy Packs. A pack implements `QwenBridgePack` and its
kind interface, and registers its implementation in
`META-INF/services/io.qwenbridge.pack.QwenBridgePack`.

Build a separate JAR against this SDK, without a server dependency. Declare its
manifest, compatible API version, dependencies, capabilities and typed config.
Configuration secrets use the supplied SecretResolver. Release all resources in
`close()`; the runtime keeps in-flight leases alive during upgrades/disabling.

The Commerce and Library modules are independently compiled domain examples.
`PackAcceptanceTest` additionally compiles a separate domain/policy/connector JAR
against only the SDK, signs it with an ephemeral key and exercises lifecycle.
The connector example opens a real socket and proves closure after its final lease.

See [platform contracts](../docs/platform/table-1-api.md) for signing formats,
installation, policies, license entitlements and the local marketplace API.
