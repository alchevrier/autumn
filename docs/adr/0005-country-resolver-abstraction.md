# ADR-0005: Country Resolver Abstraction

## Status

Proposed

## Context

Autumn needs a single way to resolve country information across platforms even though the available signals differ between mobile and web. The solution must stay testable, support dependency injection, and allow priority order changes without rewriting business logic.

## Decision

Autumn defines a shared `CountryResolver` abstraction and composes multiple resolvers behind a default implementation.

- `CountryResolver` is the shared interface for country detection.
- `DefaultCountryResolver` chains resolvers in priority order.
- The resolver set may include `PaymentCountryResolver`, `OperatorCountryResolver`, `IPCountryResolver`, `ProfileCountryResolver`, `LocaleCountryResolver`, and `FallbackCountryResolver`.
- Platform-specific resolvers are injected via Kotlin Multiplatform `expect`/`actual`.
- Web uses fewer resolvers but the same abstraction.
- Resolver priority order is configurable.

## Consequences

- Country resolution logic stays consistent across platforms.
- Platform differences are isolated behind injected implementations.
- Testing becomes easier because each resolver can be mocked or replaced.
- Misconfigured resolver ordering can produce incorrect country selection, so defaults should be well-defined.
