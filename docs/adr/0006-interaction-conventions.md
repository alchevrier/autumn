# ADR-0006: Interaction Conventions

## Status

Proposed

## Context

Autumn needs a predictable convention for how configuration-driven frontends describe user and system interactions. Without shared conventions, each feature could model create, update, stream, poll, or redirect behavior differently, making state harder to understand and UI bridges harder to implement consistently across platforms.

## Decision

Autumn defines a shared set of interaction conventions for common frontend flows.

- Workflows are defined in documents and interpreted by the UI layer rather than hardcoded separately per platform.
- **Create** conventions describe actions that produce a new document, resource, or workflow instance.
- **Update** conventions describe actions that mutate an existing document or stateful resource.
- **Stream** conventions describe long-lived or incremental updates delivered continuously to the client.
- **Poll** conventions describe repeated reads used when push or stream delivery is unavailable or unnecessary.
- **Redirect** conventions describe navigation or handoff flows where control moves to another route, screen, or external system.

These conventions should be represented explicitly in shared models and configuration so platform UI layers can interpret them consistently without inventing platform-specific semantics.

## Consequences

- State and configuration gain a common language for interaction patterns.
- Platform-specific UI bridges can implement consistent behavior for the same interaction type.
- New features should align with existing conventions before introducing new interaction categories.
- Future ADRs or module APIs may refine payload shapes and lifecycle details for each convention.
