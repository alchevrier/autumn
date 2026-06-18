# ADR-0008: Client Authentication and Token Management

## Status

Proposed

## Date

2026-06-17

## Context

Autumn's client needs a unified authentication workflow that works across multiple credential schemes — OAuth2, Basic, and similar mechanisms — without the application or UI layer having to be aware of which scheme is in use. Once a user authenticates, the resulting credential or token must be persisted and automatically attached to every subsequent API call. Token expiry and refresh must also be handled transparently so the application does not need to reason about credential validity at each call site. Basic authentication has no expiry concept, so refresh must not be triggered for it.

## Decision

Autumn defines a shared authentication workflow and token persistence model for all credential schemes. The authentication lifecycle is modelled as a finite-state machine consistent with the FSM approach used across all Autumn workflows.

The FSM has the following states and transitions:

```
                    submit credentials
  unauthenticated ──────────────────────► authenticating
        ▲                                      │
        │                                      ├─── server accepts ──► authenticated ──► refreshing
        │                                      │                            │                │
        └──────── refresh fails ───────────────┘                            │                ├─── refresh succeeds ──► authenticated
        │                                                                    │                │
        └──────── sign-out ──────────────────────────────────────────────────┘                └─── refresh fails ────► unauthenticated
        │
        └──────── server rejects ◄── authenticating (error sub-state → back to unauthenticated)
```

- **`unauthenticated`** — no credential is held. The app presents the authentication state document, which declares the required credential fields and the action to submit them.
- **`authenticating`** — credentials have been submitted and the request is in flight. On success the server returns a credential envelope and the FSM transitions to `authenticated`. On failure it returns to `unauthenticated` with an error state document.
- **`authenticated`** — the credential envelope is persisted in the platform secure store. All API calls attach the credential automatically. Before each call the client checks the expiry timestamp; if the token is within a configurable threshold of expiry the FSM transitions to `refreshing`.
- **`refreshing`** — a silent token refresh request is in flight. On success the new envelope is persisted and the FSM returns to `authenticated`. On failure the credential is cleared and the FSM transitions to `unauthenticated`.
- **Sign-out** is an explicit transition from `authenticated` to `unauthenticated` that clears the persisted envelope and presents the authentication state document.

Scheme-specific behaviour is contained within transitions, not in separate state graphs:

- On successful authentication the server returns a credential envelope containing the token or credential material, the scheme identifier, and, where applicable, an expiry timestamp. Basic credentials carry no expiry field.
- The credential envelope is persisted in a secure platform store — Keychain on iOS, EncryptedSharedPreferences or equivalent on Android, and on Web via the Web Crypto API's non-extractable `CryptoKey` stored in IndexedDB. A non-extractable key is held inside the browser's internal key store and is never exposed to JavaScript or readable by an XSS attacker; it can only be used through the Web Crypto API for the operations it was created for. This makes the web storage model substantially equivalent to the OS-level secure stores on mobile.
- If the credential carries no expiry timestamp — as is the case for Basic authentication — the `refreshing` state is never entered. The FSM stays in `authenticated` until sign-out or server invalidation.

## Consequences

- The application and UI layer never handle credential material directly. Credential attachment and refresh are infrastructure concerns handled below the document rendering layer.
- OAuth2, Basic, and future schemes all produce a credential envelope with the same shape, so the rest of the application is scheme-agnostic.
- Token refresh is driven by expiry metadata in the envelope, not by intercepting 401 responses, which avoids a failed request on every refresh cycle.
- Basic authentication participates in the same workflow and persistence model without triggering refresh logic.
- Secure storage requirements differ by platform; each platform implementation must use the appropriate secure store.
- If the secure store is unavailable or the persisted credential is corrupted, the client must fall back to the authentication workflow gracefully.
- Future ADRs or module APIs may refine the credential envelope schema, the refresh threshold, and the handling of concurrent requests during a refresh.
