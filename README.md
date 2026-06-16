# autumn

Configuration driven frontend skeleton for mobile and web.

## What is Autumn?

Autumn is a Kotlin Multiplatform framework built around the **UI → State + Buckets** pattern, with country-aware remote configuration and native UI rendering. It decouples frontend logic from platform-specific rendering while enforcing security by design and compliance through configuration.

## Core pattern: UI → State + Buckets

- **UI** renders native views for iOS, Android, and Web.
- **State** represents the documents describing what the UI should show.
- **Workflows** are also modeled as documents, so create, update, stream, poll, and redirect flows can be defined declaratively and rendered by the UI.
- **Buckets** hold the documents and assets referenced by state.
- **Configuration** resolves which bucket sources, features, and country-specific behavior are available.

The key security rule is simple: state must only reference bucket content the user is authorized to access.

## Key features

- **Configuration driven**: supports bundled defaults plus remotely updated configuration.
- **Country-aware configuration**: resolves country-specific behavior and infrastructure from configuration.
- **Security by design**: unauthorized content is never exposed through state references.
- **Native UI rendering**: keeps rendering close to each platform while sharing the application model.
- **Country Resolver abstraction**: uses a shared interface with platform-specific implementations.

## Module overview

- `autumn-core` — core interfaces and shared domain models.
- `autumn-state` — state documents and state orchestration.
- `autumn-buckets` — bucket abstractions and bucket reference handling.
- `autumn-resolver` — country resolution interfaces and resolver composition.
- `autumn-config` — bundled and remote configuration management.
- `autumn-ui` — platform UI bridge built around expect/actual entry points.

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
autumn-core/
autumn-state/
autumn-buckets/
autumn-resolver/
autumn-config/
autumn-ui/
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
