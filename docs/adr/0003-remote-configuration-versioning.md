# ADR-0003: Remote Configuration Versioning

## Status

Proposed

## Context

Autumn relies on configuration for bucket sources, country-specific behavior, and feature control. The app must continue to work offline while still allowing configuration changes to roll out without requiring an app store release.

## Decision

Autumn uses bundled configuration with remote version checks and local caching.

- A baseline configuration is bundled with the app.
- On startup, the app calls an API to determine whether a newer configuration version is available.
- When a newer version exists, the app fetches it and caches it locally.
- The cached configuration becomes the active configuration for future runs.

## Consequences

- The app remains functional offline with bundled configuration.
- Bucket sources, country mappings, and feature flags can be updated remotely.
- Configuration lifecycle management becomes part of application startup.
- Care is needed to handle cache invalidation and compatibility between app and config versions.
