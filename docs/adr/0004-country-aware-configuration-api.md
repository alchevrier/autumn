# ADR-0004: Country-Aware Configuration API

## Status

Proposed

## Context

Autumn needs to deliver configuration and workflow documents that respect privacy, performance, and country-specific rules. Sending every country's configuration to every client would increase payload size and unnecessarily expose irrelevant regional data. The same problem applies to platform-specific branches in shared documents, because clients should not need to receive or interpret irrelevant iOS, Android, or Web behavior when that reduction can happen centrally.

## Decision

Autumn uses a country-aware configuration API and companion backend-for-frontend delivery layer.

- A central configuration contains all country-specific configurations and shared workflow documents.
- When the client calls the API, it receives only the slice relevant to its country.
- Country detection is based on payment information first, then SIM/operator for mobile, IP as fallback, followed by profile, locale, and final fallback logic.
- Shared documents may declare platform predicates or similar conditions so iOS, Android, and Web behavior can be expressed in one model.
- The backend-for-frontend evaluates country and platform conditions, trims irrelevant branches, and returns the reduced document the client should render.
- When the client already holds a current document version, the backend-for-frontend may return a delta relative to that version instead of always returning the full reduced document.
- The backend-for-frontend validates presented API keys before returning protected configuration or workflow documents and may rely on a separate key management backend for that decision.
- Clients remain unaware of other countries' configurations.
- Clients remain unaware of platform-specific branches or country-specific content that does not apply to them.

## Consequences

- Responses stay smaller and more privacy-preserving.
- Country-specific data is kept isolated from unrelated clients.
- Server-side country and platform slicing become core parts of configuration and document delivery.
- Shared documents can stay declarative while delivery remains tailored to each client context.
- Delta delivery can further reduce payload size when the server and client agree on the current base version.
- Protected document delivery depends on reliable API key validation before any reduced state is returned.
- Correct country resolution is essential for compliance and user experience.
