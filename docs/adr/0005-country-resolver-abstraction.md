# ADR-0005: Country Resolver Abstraction

## Status

Proposed

## Date

2026-06-17

## Context

Autumn needs a single way to resolve country information across platforms even though the available signals differ between mobile and web. The solution must stay testable, support dependency injection, and allow priority order changes without rewriting business logic. It must also work correctly for free and freemium applications where a significant portion of users may never provide payment information at all, so country resolution cannot be gated on a payment signal that may never arrive.

## Decision

Autumn defines a shared `CountryResolver` abstraction and composes multiple resolvers behind a default implementation.

- `CountryResolver` is the shared interface for country detection.
- `DefaultCountryResolver` chains resolvers in two phases. The initial phase runs `IPCountryResolver` to produce a country immediately on first launch without requiring any user data. The override phase runs `PaymentCountryResolver` — which covers credit card billing country, Apple Pay country, and Google Pay country — followed by `OperatorCountryResolver` on mobile, and replaces the IP-derived result when a stronger signal is present. `ProfileCountryResolver`, `LocaleCountryResolver`, and `FallbackCountryResolver` apply when all stronger signals are absent.
- The resolver set may include `IPCountryResolver`, `PaymentCountryResolver`, `OperatorCountryResolver`, `ProfileCountryResolver`, `LocaleCountryResolver`, and `FallbackCountryResolver`.
- Platform-specific resolvers are injected via Kotlin Multiplatform `expect`/`actual`.
- Web uses fewer resolvers — no `OperatorCountryResolver` — but the same abstraction and two-phase model.
- Resolver priority order is configurable, but the default order encodes the two-phase intent: IP first, payment override when available.

## Consequences

- Country resolution logic stays consistent across platforms.
- Free and freemium users who never provide payment information are fully supported: the IP-derived country is the permanent result for them, not a temporary placeholder.
- Platform differences are isolated behind injected implementations.
- Testing becomes easier because each resolver can be mocked or replaced.
- Misconfigured resolver ordering can produce incorrect country selection, so defaults should be well-defined.
