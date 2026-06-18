# ADR-0004: Country-Aware Configuration API

## Status

Proposed

## Date

2026-06-17

## Context

Autumn needs to deliver configuration and workflow documents that respect privacy, performance, and country-specific rules. Sending every country's configuration to every client would increase payload size and unnecessarily expose irrelevant regional data. The same problem applies to platform-specific branches in shared documents, because clients should not need to receive or interpret irrelevant iOS, Android, or Web behavior when that reduction can happen centrally.

## Decision

Autumn uses a country-aware configuration API and companion backend-for-frontend delivery layer.

- A central configuration contains all country-specific configurations and shared workflow documents.
- When the client calls the API, it receives only the slice relevant to its country.
- Country detection uses a two-phase model designed to work for free, freemium, and paid applications alike. The initial country is resolved from IP address, which is available immediately without any user action and covers the first-launch experience as well as users who never provide payment information. Once the user has a payment method on file — credit card billing country, Apple Pay country, or Google Pay country — that signal overrides the IP-derived country, since payment information reflects where the user actually resides and pays rather than where they are currently connected. SIM/operator country is used on mobile when payment information is not yet available and the IP signal is ambiguous. Profile, locale, and fallback logic apply when all stronger signals are absent.
- Shared documents may declare platform predicates or similar conditions so iOS, Android, and Web behavior can be expressed in one model.
- The backend-for-frontend evaluates country, platform, and experiment conditions, trims irrelevant branches, and returns the full reduced document the client should render. The server always returns a complete document; delta computation is not performed server-side.
- The backend-for-frontend may assign or resolve an experiment variant for the calling device and include that variant's configuration slice in the response. Variant assignment can be constrained by country, platform, cohort size, or other resolvable dimensions, allowing A/B tests to be scoped precisely without a new app release.
- The backend-for-frontend validates presented API keys before returning protected configuration or workflow documents and may rely on a separate key management backend for that decision.
- Clients remain unaware of other countries' configurations.
- Clients remain unaware of platform-specific branches or country-specific content that does not apply to them.
- Clients remain unaware of experiment variants they are not assigned to.

## Consequences

- Responses stay smaller and more privacy-preserving.
- Country-specific data is kept isolated from unrelated clients.
- Server-side country and platform slicing become core parts of configuration and document delivery.
- Shared documents can stay declarative while delivery remains tailored to each client context.
- The server contract is always a full reduced document, which keeps the protocol stateless and removes any need to track client versions server-side.
- Protected document delivery depends on reliable API key validation before any reduced state is returned.
- Correct country resolution is essential for compliance and user experience. The two-phase model — IP for the initial experience, payment information as the authoritative override — ensures the correct country is used as soon as a reliable signal is available without blocking the first-launch experience on payment data.
- Experiment variant assignment becomes a server-side concern, keeping experiment logic out of the client and allowing rollout percentages and targeting rules to change without a new app release.
