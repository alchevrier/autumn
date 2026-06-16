# ADR-0003: Remote Configuration Versioning

## Status

Proposed

## Context

Autumn relies on configuration for bucket sources, country-specific behavior, feature control, and the delivery of shared state documents. The app must continue to work offline while still allowing configuration changes to roll out without requiring an app store release. It also needs a way to keep rendered experiences responsive by reusing cached state and applying small updates when the server can describe what changed instead of forcing the client to replace the entire view every time.

## Decision

Autumn uses bundled configuration with remote version checks, local caching, and version-aware state updates.

- A baseline configuration is bundled with the app.
- On startup, the app calls an API to determine whether a newer configuration version is available.
- When a newer version exists, the app fetches it and caches it locally.
- The cached configuration becomes the active configuration for future runs.
- Clients may also cache the latest resolved state or rendered view document together with its version metadata.
- Servers may respond with either a full replacement document or a delta from the client's current version when that is sufficient to produce the next valid state.
- Delta updates must be computed against a known base version so clients can safely apply them or fall back to a full document when versions diverge.

## Consequences

- The app remains functional offline with bundled configuration.
- Bucket sources, country mappings, and feature flags can be updated remotely.
- Cached state can improve perceived performance and preserve continuity while fresh data is loading.
- Configuration and state lifecycle management become part of application startup and runtime synchronization.
- Care is needed to handle cache invalidation, version compatibility, and fallback from delta updates to full document delivery.
