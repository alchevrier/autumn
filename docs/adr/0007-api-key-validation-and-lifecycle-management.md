# ADR-0007: API Key Validation and Lifecycle Management

## Status

Proposed

## Date

2026-06-17

## Context

Autumn's backend-for-frontend needs a way to authenticate calling clients before serving reduced state, configuration, or bucket references. It also needs a separate place to issue, validate, and revoke API keys so key lifecycle concerns do not become embedded directly in document delivery logic. Without a dedicated model, each backend could implement key validation differently and revocation could become inconsistent or delayed. More broadly, the key management layer is the primary mechanism by which a developer controls their attack surface: it determines who can call what, allows instant revocation when a key is suspected compromised, and provides the levers to respond to an active attack without redeploying any delivery service.

## Decision

Autumn uses a split API key architecture with validation at the backend-for-frontend and lifecycle management in a dedicated key service.

- The backend-for-frontend requires a presented API key before serving protected state, configuration, or workflow documents.
- The backend-for-frontend validates API keys through a dedicated key management backend rather than relying on locally hardcoded key lists.
- The key management backend is responsible for issuing, validating, rotating, and revoking API keys.
- Keys may be scoped to limit the surface they can access — for example restricting a key to a specific country, platform, document type, or tenant. Narrow scopes reduce the blast radius of a compromised key.
- Validation responses should include enough metadata for authorization decisions, such as key status, scope, tenant, or client identity when applicable.
- Revocation must take effect quickly enough that a revoked key can no longer be used to fetch protected documents with stale authorization.
- Delivery services should fail closed when key validation is unavailable or returns an invalid result.
- Rate limiting and anomaly signals — such as unusually high request volume or unexpected country of origin — can be enforced or surfaced at the key management layer without touching delivery logic.

## Consequences

- Authentication and key lifecycle concerns remain centralized instead of being duplicated across delivery backends.
- The backend-for-frontend can focus on document reduction, authorization, and response shaping.
- Developers have explicit, centralized control over their attack surface: who holds keys, what each key can access, and which keys are active at any moment.
- A suspected compromise can be contained immediately by revoking the affected key, without redeploying any delivery service or changing application code.
- Key scoping limits the blast radius of any single compromised key to only the surface that key was permitted to access.
- Key revocation and rotation become explicit operational capabilities rather than emergency procedures.
- Web is the weakest point in the client-side key storage model. Unlike iOS Keychain and Android EncryptedSharedPreferences, the browser has no OS-level private key store accessible to application code. The mitigation is to use the Web Crypto API's non-extractable `CryptoKey` stored in IndexedDB. The flow is: the server returns the raw key material over TLS in the authentication response; the client immediately calls `crypto.subtle.importKey()` with `extractable: false`, which produces a `CryptoKey` object whose underlying bytes the browser internalises and never re-exposes to JavaScript; that `CryptoKey` object is then stored directly in IndexedDB (CryptoKey objects are structured-cloneable). The raw key bytes exist in a JavaScript variable only for the duration of the single `importKey` call — they are never written to localStorage, sessionStorage, a cookie, or any other persistent JS-readable location. After `importKey` returns, the only way to use the key is through the Web Crypto API from the same origin; the bytes themselves cannot be read back by any JavaScript, including an XSS payload.
- If non-extractable Web Crypto storage is unavailable or insufficient for a deployment, the server-side controls — scoped keys, rate limiting, and instant revocation — act as the fallback containment layer.
- The residual attack surface after applying the non-extractable key model is the TLS connection over which the raw key bytes travel from server to client. A successful TLS interception — via a compromised CA, a misconfigured trust store, or a network-level MITM — would expose the key bytes in transit before `importKey` is called. On mobile this is mitigated by certificate pinning, which rejects any certificate not matching a pinned public key regardless of CA trust. On web, certificate pinning is not available to application code; the trust model relies entirely on the browser's certificate validation and CT log enforcement. For web deployments where the key issuance response carries sensitive material, short key lifetimes reduce the value of an intercepted key, and the key management backend's revocation capability allows immediate invalidation if interception is detected. The practical consequence is that the TLS layer must be treated as the primary remaining threat vector on web, and key issuance endpoints should be hardened accordingly — HSTS, strong cipher suites, and monitoring for anomalous issuance patterns.
- Availability and latency of the key management backend become important dependencies for protected document delivery.
- Future ADRs or module APIs may refine token formats, caching policy for validation responses, rate limiting strategy, and operational controls for key issuance.
