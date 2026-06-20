# ADR-0002: Bucket Source Decoupling via Configuration (Delegated)

## Status

Rejected / Delegated to User Backend (See ADR-0013)

## Date

2026-06-20

## Context

Autumn initially aimed to support multiple storage providers and country-specific deployment choices without forcing application code to change whenever infrastructure changes. We envisioned the client resolving buckets per asset type and per country (AWS S3, GCS, CDN selection, etc.) at runtime dynamically inside the framework configuration cache.

However, as we evolved into a pure, zero-allocation Circuit-Based skeleton, maintaining extensive runtime routing tables for Content Delivery Networks (CDNs) added unnecessary heap allocations and string processing complexity. 

## Decision

We have rejected handling Cloud/CDN bucket sourcing and spatial routing inside the Autumn client framework.

As formalized in **ADR-0013**, Autumn treats the backend as a commodity. Country-aware bucket resolution, CDN URL mapping, and edge location bindings are purely user-side backend concerns. 

- The Autumn client strictly cares only about the final deterministic flat configuration payload handed off to the `AutumnNetworkEngine`.
- If a resource requires a specific regional URI, the user's backend (which lives outside the Autumn constraint ecosystem) must pre-resolve that URL and inject it directly into the JSON configuration matrix before serving it to the client.
- The `JsonConfigParser` simply maps the final string bytes into the `StringRegistry` flatly, completely unaware of infrastructure topologies.

## Consequences

- **Positives:** Removes massive configuration bloating on the client side. No dictionary lookups for "fr" vs "de" regional CDN edges on the UI hot path.
- **Positives:** Aligns perfectly with our zero-allocation philosophy. The client parses exactly what it draws.
- **Negatives:** The server/backend must do the heavy lifting of localized URL derivation before transmitting the layout configuration payload to the client.
