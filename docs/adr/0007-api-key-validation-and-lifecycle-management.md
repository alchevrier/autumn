# ADR-0007: API Key Validation and Lifecycle Management

## Status

Proposed

## Context

Autumn's backend-for-frontend needs a way to authenticate calling clients before serving reduced state, configuration, or bucket references. It also needs a separate place to issue, validate, and revoke API keys so key lifecycle concerns do not become embedded directly in document delivery logic. Without a dedicated model, each backend could implement key validation differently and revocation could become inconsistent or delayed.

## Decision

Autumn uses a split API key architecture with validation at the backend-for-frontend and lifecycle management in a dedicated key service.

- The backend-for-frontend requires a presented API key before serving protected state, configuration, or workflow documents.
- The backend-for-frontend validates API keys through a dedicated key management backend rather than relying on locally hardcoded key lists.
- The key management backend is responsible for issuing, validating, rotating, and revoking API keys.
- Validation responses should include enough metadata for authorization decisions, such as key status, scope, tenant, or client identity when applicable.
- Revocation must take effect quickly enough that a revoked key can no longer be used to fetch protected documents with stale authorization.
- Delivery services should fail closed when key validation is unavailable or returns an invalid result.

## Consequences

- Authentication and key lifecycle concerns remain centralized instead of being duplicated across delivery backends.
- The backend-for-frontend can focus on document reduction, authorization, and response shaping.
- Key revocation and rotation become explicit operational capabilities.
- Availability and latency of the key management backend become important dependencies for protected document delivery.
- Future ADRs or module APIs may refine token formats, caching policy for validation responses, and operational controls for key issuance.
