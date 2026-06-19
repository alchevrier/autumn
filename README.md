# autumn

Configuration driven frontend skeleton for mobile and web.

## What is Autumn?

Autumn is a Kotlin Multiplatform framework built around the **UI → State + Buckets** pattern, with country-aware remote configuration and native UI rendering. It decouples frontend logic from platform-specific rendering while enforcing security by design and compliance through configuration.

## Core pattern: UI → State + Buckets

- Autumn models the application as a finite-state machine where each document defines the current state and available transitions.
- **UI** renders native views for iOS, Android, and Web.
- **State** represents the documents describing what the UI should show.
- **Workflows** are also modeled as documents, so create, update, delta, stream, poll, and redirect flows can be defined declaratively and rendered by the UI.
- **Buckets** hold the documents and assets referenced by state.
- **Configuration** resolves which bucket sources, features, and country-specific behavior are available.
- **Delivery backends** can validate API keys before returning reduced state or configuration documents.

The key security rule is simple: state must only reference bucket content the user is authorized to access.

## Key features

- **Configuration driven**: supports bundled defaults plus remotely updated configuration, caching, and version-aware delivery.
- **Country-aware configuration**: resolves country-specific behavior and infrastructure from configuration.
- **Security by design**: unauthorized content is never exposed through state references.
- **Centralized key management**: API key issuance, validation, rotation, and revocation can live in a dedicated backend.
- **Native UI rendering**: keeps rendering close to each platform while sharing the application model.
- **Country Resolver abstraction**: uses a shared interface with platform-specific implementations.
- **Array-based, pointer-free data structures**: all internal data — configuration tables, resource registries, list items, and form state — is stored in flat pre-allocated arrays accessed by integer index and byte offset. There are no object graphs, no pointer chains, and no GC-visible references between data items. This eliminates pointer indirection overhead, improves cache locality, and removes allocation pressure across the entire data layer.

## Module overview

- `autumn-core` — core interfaces, shared domain models, and compiler pacts (`@LongLived`, `@InjectBudget`).
- `autumn-compiler-plugin` — K2 compiler plugin enforcing strictly bounded allocations at build time.
- `autumn-gradle-plugin` — Gradle hook required to execute the compiler plugin across platforms.
- `autumn-state` — hardware-sympathetic reactivity engine (`EpochStateEngine`) replacing traditional Flow observers.
- `autumn-buckets` — bucket abstractions mapping configuration pointers to raw image/document strings.
- `autumn-resolver` — deterministic network boundary (`AutumnNetworkEngine`) executing in-place handoffs.
- `autumn-bff` — backend-for-frontend resolving network bounds, A/B logic, and API key validation.
- `autumn-admin` — internal REST API microservice to issue and revoke the API keys consumed by the BFF.
- `autumn-config` — zero-allocation payload string registry and hardware matrix limit calculator (`JsonConfigParser`).
- `autumn-ui` — native rendering bridge linking platform Canvas text exactly to byte indices (`AutumnCircuitBinder`, `AutumnMotherboard`).

## Architecture

```text
                        +----------------------+
                        | Remote Config API    |
                        | version + country    |
                        +----------+-----------+
                                   |
                                   v
+-------------------+    +----------------------+    +----------------------+
| CountryResolver   +--->| autumn-config        +--->| Bucket source config |
| chain             |    | bundled + cached     |    | per country          |
+---------+---------+    +----------+-----------+    +----------+-----------+
          |                           |                           |
          |                           v                           v
          |                +----------------------+    +----------------------+
          |                | autumn-state         +--->| autumn-buckets       |
          |                | UI documents         |    | docs + images        |
          |                +----------+-----------+    +----------+-----------+
          |                           |
          v                           v
                    +----------------------+
                    | autumn-ui            |
                    | native rendering     |
                    | iOS / Android / Web  |
                    +----------------------+
```

## Repository structure

```text
docs/adr/
autumn-core/               # Compiler pacts and limits
autumn-compiler-plugin/    # K2 AST visitor enforcing allocations
autumn-gradle-plugin/      # Gradle hooks for plugin injection
autumn-resolver/           # In-place Network Handoff API
autumn-admin/              # Internal Key Issuance REST API
autumn-bff/                # Country, cohort resolution, API Keys
autumn-config/             # Zero-alloc JSON parsing and registries
autumn-buckets/            # Content offset mappers
autumn-state/              # Circuit-based Epoch observer
autumn-ui/                 # SoC Motherboard and Native UI Compose Binder
```

## Getting started

Getting started guidance will be added as the project skeleton evolves. For now, this repository establishes the architectural decisions and module boundaries for the framework.

## ADRs

Architectural decisions live in [`docs/adr/`](docs/adr) and capture the initial shape of Autumn:

- ADR-0001 — UI → State + Buckets Pattern
- ADR-0002 — Bucket Source Decoupling via Configuration
- ADR-0003 — Remote Configuration Versioning
- ADR-0004 — Country-Aware Configuration API
- ADR-0005 — Country Resolver Abstraction
- ADR-0006 — Interaction Conventions
- ADR-0007 — API Key Validation and Lifecycle Management
- ADR-0008 — Client Authentication and Token Management
- ADR-0009 — IoC Container and Lazy Initialization
- ADR-0010 — Zero-Allocation JSON Data Model
- ADR-0011 — Paginated List Rendering and GC Reduction
- ADR-0012 — Form State Management via Pre-Allocated Slots
- ADR-0013 — Backend-for-Frontend Routing Configuration
- ADR-0014 — Event Loop Model and Context Switch Minimisation
- ADR-0015 — Configuration-Derived Allocation Budget and Compiler Enforcement
- ADR-0016 — Interaction and Entity Schema Contract
- ADR-0017 — Circuit-Based Data Pipeline and Interrupt Moderation

## Integration example (Jetpack Compose)

Autumn bypasses standard object allocation by replacing DTO flows with an emulated hardware interrupt wire. Here is how you bind Autumn to a Compose UI:

```kotlin
// 1. The Circuit Binder
// This adapts Autumn's memory matrices to platform-specific graphics.
class MyScreenBinder(
    stateEngine: EpochStateEngine,
    stringRegistry: StringRegistry
) : AutumnCircuitBinder(stateEngine, stringRegistry) {
    // Expose specific coordinates statically configured by @InjectBudget
    fun getHeroTitle() = resolveTextPrimitive(coordinateId = 0)
    fun getActionLabel() = resolveTextPrimitive(coordinateId = 1)
}

// 2. The Native UI
@Composable
fun AutumnScreen(binder: MyScreenBinder) {
    // A single state trigger. When the global hardware wire pulses, 
    // this increments, causing Compose to redraw the screen.
    var epochTick by remember { mutableStateOf(0) }
    
    LaunchedEffect(binder) {
        // Suspend the UI completely until the batch finishes
        binder.attachToInterruptWire(this) {
            epochTick++ // Emulates an interrupt wakeup
        }
    }

    // Rely on the tick to trigger recomposition, 
    // then read strictly from the hardware-sympathetic registry
    Column {
        Text(text = binder.getHeroTitle())
        Button(onClick = { /* Fire NetworkHandoff in-place */ }) {
            Text(text = binder.getActionLabel())
        }
    }
}
```

Because Autumn handles the payloads purely natively as bytes, making the network request does not fill the garbage collector. The OS socket bytes sit in `StringRegistry`, the `EpochStateEngine` evaluates the exact slot mutations, and Compose only executes a String allocation inside `resolveTextPrimitive` when drawing the physical pixel!
