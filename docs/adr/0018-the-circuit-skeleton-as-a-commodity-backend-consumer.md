# ADR-0018: The Circuit Skeleton as a Commodity Backend Consumer

## Status

Accepted

## Date

2026-06-20

## Context

Throughout the development of `autumn`, we recognized an architectural inflection point. The core of this repository—the `autumn-compiler-plugin`, `EpochStateEngine`, hardware-bound registries, and lock-free arrays—was built to answer a very specific, unsolved problem fundamentally mismanaged by traditional UI frameworks: **how to guarantee deterministic, zero-latency rendering using circuit-based programming models on commodity hardware.**

In an effort to provide a complete "End-to-End" example, we temporarily implemented an `autumn-bff` (Backend-for-Frontend) and an `autumn-admin` microservice. These modules demonstrated API key lifecycles, user authorization, and A/B cohort logic using traditional asynchronous REST semantics (Ktor).

However, introducing typical server-side REST concepts heavily polluted the central repository. Routing logic, JWT validation, database Postgres mappings, or content negotiation are not novel—they are commodity features understood universally by the backend ecosystem. 

By keeping these standard backend features inside `autumn`, the repository's messaging became muddied. Is `autumn` a server-client framework, or a native runtime environment constraint tool?

## Decision

We have fundamentally stripped `autumn` of all backend processing servers (`autumn-bff`, `autumn-admin`) and unassociated HTTP / API Key ADRs.

- `autumn` declares that **The Backend is a Commodity.**
- The framework accepts structured inbound network byte buffers (`AutumnNetworkEngine`) oblivious to whether they originated from a REST backend, a direct WebSocket, GraphQL, or a local mock array.
- Securing the payloads (JWTs, OAuth), dynamically aggregating cohorts (A/B testing tools), and handling edge location bindings (Cloudflare headers) are deliberately pushed out of the `autumn` ecosystem. App developers can build those using standard tooling outside of this monolithic UI enforcement boundary.
- The `autumn` framework will exist exclusively as an **E2E Client-side Circuit Skeleton**. Its absolute maximal boundary is the `AutumnNetworkEngine` deserialization phase, reaching straight down to the memory addresses backing the user's `ViewCanvas`.

## Consequences

- **Positives:** The repository is drastically cleaner. It now acts as a pure, focused SDK and whitepaper on hardware-sympathetic circuit programming. Developers integrating `autumn` will clearly understand that the project is an execution constraint model, not a web server.
- **Positives:** Removes bulky third-party JSON encoding libraries from our dependency tree. `autumn` can strictly enforce its own zero-allocation pipeline constraints.
- **Negatives:** Developers looking for a pre-baked, full-stack "App in a Box" template will need to bring their own BFF / Web servers. This is considered acceptable, as `autumn` targets high-performance app developers who already govern their backend layers.
