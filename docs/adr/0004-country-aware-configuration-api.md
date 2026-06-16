# ADR-0004: Country-Aware Configuration API

## Status

Proposed

## Context

Autumn needs to deliver configuration that respects privacy, performance, and country-specific rules. Sending every country's configuration to every client would increase payload size and unnecessarily expose irrelevant regional data.

## Decision

Autumn uses a country-aware configuration API.

- A central configuration contains all country-specific configurations.
- When the client calls the API, it receives only the slice relevant to its country.
- Country detection is based on payment information first, then SIM/operator for mobile, IP as fallback, followed by profile, locale, and final fallback logic.
- Clients remain unaware of other countries' configurations.

## Consequences

- Responses stay smaller and more privacy-preserving.
- Country-specific data is kept isolated from unrelated clients.
- Server-side country slicing becomes a core part of configuration delivery.
- Correct country resolution is essential for compliance and user experience.
