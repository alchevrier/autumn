# ADR-0006: Interaction Conventions

## Status

Proposed

## Context

Autumn needs a predictable convention for how configuration-driven frontends describe user and system interactions. Without shared conventions, each feature could model create, update, next, done, stream, poll, or redirect behavior differently, making state harder to understand and UI bridges harder to implement consistently across platforms. The model also needs a shared way to describe lifecycle states such as loading, ready, submitting, success, error, and completed so applications can be treated consistently as finite-state machines.

## Decision

Autumn defines a shared set of interaction conventions for common frontend flows.

- Workflows are defined in documents and interpreted by the UI layer rather than hardcoded separately per platform.
- Those workflow documents act as finite-state machine nodes whose conventions describe the transitions available from the current state.
- Shared state documents should represent common lifecycle states such as loading, ready, empty, submitting, success, error, and completed when those states are relevant to the flow.
- **Create** conventions describe actions that produce a new document, resource, or workflow instance.
- **Update** conventions describe actions that mutate an existing document or stateful resource.
- **Next** and **Done** conventions describe explicit transitions between workflow states rather than introducing ad hoc navigation semantics.
- **Stream** conventions describe long-lived or incremental updates delivered continuously to the client.
- **Poll** conventions describe repeated reads used when push or stream delivery is unavailable or unnecessary.
- **Redirect** conventions describe navigation or handoff flows where control moves to another route, screen, or external system.
- Interaction documents may include platform, country, capability, or feature conditions, but those conditions should refine the shared model rather than replace the common conventions.

These conventions should be represented explicitly in shared models and configuration so platform UI layers can interpret them consistently without inventing platform-specific semantics.

## Consequences

- State and configuration gain a common language for interaction patterns.
- State documents gain a more consistent lifecycle vocabulary for common app flows.
- Platform-specific UI bridges can implement consistent behavior for the same interaction type.
- New features should align with existing conventions before introducing new interaction categories.
- Future ADRs or module APIs may refine payload shapes and lifecycle details for each convention.
