# ADR-0001: UI → State + Buckets Pattern

## Status

Proposed

## Date

2026-06-17

## Context

Autumn needs a frontend architecture that separates rendering from the content and assets required to build an experience across iOS, Android, and Web. The model must also prevent the application from exposing content references a user is not allowed to access.

## Decision

Autumn adopts the **UI → State + Buckets** pattern as its core architecture.

- The application is modeled as a finite-state machine.
- **State** is composed of documents that describe what the UI should show.
- Each document defines the current state and the transitions that may follow from it.
- The origin of state is intentionally source-agnostic: state may be produced by a BFF pipeline or by application-provided data providers, as long as the resulting document follows the shared state contract.
- Workflows are also represented as documents so frontend behavior can be defined declaratively.
- **Buckets** are storage containers that hold documents and images referenced by state.
- Bucket resources are identified by a stable identity so the same resource can be referenced by multiple state documents across different screens.
- References from state to buckets are considered valid only when the user has access to the referenced content.
- The UI layer renders from state and does not decide access to bucket content independently.

This makes authorization part of how state is produced, not an afterthought during rendering. It also means the UI can render document-defined workflows and state transitions without embedding workflow logic in each platform implementation. Because the UI renders from state documents rather than from hardcoded screens, entirely new screens can be introduced by delivering new state documents — no app update is required as long as the new screen stays within the interaction conventions and resource model the app already understands.

## Consequences

- Shared frontend logic can focus on producing valid state documents.
- Platform UI layers remain thin and native.
- Unauthorized content should never be exposed through state references.
- State production must be aware of access rules when generating bucket references.
- The same bucket resource fetched on one screen is available to any other screen that references it by identity, without re-fetching.
- New screens can be added without a new app release by delivering new state documents that compose existing resource identifiers and interaction conventions the app already knows how to render.
