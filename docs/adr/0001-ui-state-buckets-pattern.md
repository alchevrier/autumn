# ADR-0001: UI → State + Buckets Pattern

## Status

Proposed

## Context

Autumn needs a frontend architecture that separates rendering from the content and assets required to build an experience across iOS, Android, and Web. The model must also prevent the application from exposing content references a user is not allowed to access.

## Decision

Autumn adopts the **UI → State + Buckets** pattern as its core architecture.

- **State** is composed of documents that describe what the UI should show.
- **Buckets** are storage containers that hold documents and images referenced by state.
- References from state to buckets are considered valid only when the user has access to the referenced content.
- The UI layer renders from state and does not decide access to bucket content independently.

This makes authorization part of how state is produced, not an afterthought during rendering.

## Consequences

- Shared frontend logic can focus on producing valid state documents.
- Platform UI layers remain thin and native.
- Unauthorized content should never be exposed through state references.
- State production must be aware of access rules when generating bucket references.
