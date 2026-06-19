# ADR-0003: Remote Configuration Versioning

## Status

Proposed

## Date

2026-06-17

## Context

Autumn relies on configuration for bucket sources, country-specific behavior, feature control, and the delivery of shared state documents. The app must continue to work offline while still allowing configuration changes to roll out without requiring an app store release. It also needs a way to keep rendered experiences responsive without unnecessary network overhead. Because the server controls which configuration version a device receives, the remote configuration layer is also a natural control point for experiment assignment and gradual rollouts.

## Decision

Autumn uses bundled configuration with remote version checks, local caching, and version-aware state updates.

- A baseline configuration is bundled with the app.
- Configuration files and state documents use JSON as their wire and storage format, consistent with the resource declaration model in ADR-0002.
- On startup, before login, the app resolves a bootstrap configuration (bundled or cached, then remote check) so first render and login flow do not block on authentication.
- During bootstrap, the app calls the configuration API to determine whether a newer configuration version is available for the current anonymous/device context.
- When a newer version exists, the app fetches it and caches it locally.
- The cached configuration becomes the active configuration for future runs.
- The remote configuration API may serve different configuration variants to different cohorts. This enables A/B testing and staged rollouts: the BFF computes which variant/version the caller should receive using country, platform, cohort rules, and caller identity context.
- Remote configuration updates must stay within the memory budgets compiled into the client. Because Autumn uses configuration-derived allocation budgets (ADR-0015), the client's data pools and Array-of-Struct layouts are sized at build time based on the bundled configuration. Remote updates can rearrange screens, modify interaction attributes, route to different buckets, or alter variants, but cannot exceed the maximum compiled pool capacities (such as rendering a list with a `pageSize` larger than the compiled hot-tier budget). Remote configurations that exceed compile-time bounds are either gracefully truncated or rejected by the client configuration loader.
- After login (or any identity change), configuration is re-resolved against the authenticated context. If the authenticated variant/version differs from bootstrap, the app atomically switches to the new configuration cache. This allows anonymous bootstrap and authenticated A/B assignment to coexist without a second app launch.
- State document caching is opt-in. A state document may declare itself cacheable; only cacheable documents are stored locally with their version metadata. Screens that do not declare caching are always fetched fresh.
- Bucket resources are cached by stable identity. Once fetched, a bucket document or asset is available to any state document that references the same resource, regardless of which screen first loaded it. This avoids redundant fetches when the same resource appears across multiple screens.
- The server always returns the full reduced document for the requesting client. Server-side delta computation is not used: the server does not need to know what version the client holds, and there is no delta format to maintain or version.
- Configuration fetches follow a stale-then-update policy specific to configuration state: on startup, use bundled or cached configuration immediately; if cached configuration age exceeds configured TTL or the version-check API reports a newer version, fetch configuration in background; on completion, atomically replace the active configuration cache for subsequent reads.

## Consequences

- The app remains functional offline with bundled configuration.
- First render and login flow are decoupled from authentication round-trips because bootstrap configuration is available before login.
- Bucket sources, country mappings, feature flags, and experiment variants can be updated remotely.
- A/B tests and gradual rollouts can be controlled server-side by varying which configuration version or variant the server assigns to a device, without shipping a new app version.
- Variant assignment can change after authentication when identity-aware cohort rules apply; the app handles this through atomic post-login configuration re-resolution.
- Screens that opt in to caching improve perceived performance without the complexity of a server-side delta protocol.
- Resources shared across screens benefit from a single fetch regardless of which screen first loaded them.
- Timeout policy (TTL) becomes a first-class configuration concern because it controls when cached configuration is considered stale and must be revalidated.
- Configuration and state lifecycle management become part of application startup and runtime synchronization.
