# ADR-0006: Interaction Conventions

## Status

Proposed

## Date

2026-06-17

## Context

Autumn needs a predictable convention for how configuration-driven frontends describe user and system interactions. Without shared conventions, each feature could model create, update, read, poll, next, done, or redirect behavior differently, making state harder to understand and UI bridges harder to implement consistently across platforms. The model also needs a shared way to describe lifecycle states such as loading, ready, submitting, success, error, and completed so applications can be treated consistently as finite-state machines.

## Decision

Autumn defines a shared set of interaction conventions for common frontend flows.

- Workflows are defined in documents and interpreted by the UI layer rather than hardcoded separately per platform.
- Those workflow documents act as finite-state machine nodes whose conventions describe the transitions available from the current state.
- Shared state documents should represent common lifecycle states such as loading, ready, empty, submitting, success, error, and completed when those states are relevant to the flow.
- **Create** conventions describe actions that produce a new document, resource, or workflow instance.
- **Update** conventions describe actions that mutate an existing document or stateful resource.
- **Read** conventions use cache-first stale-then-update semantics for normal entity/document fetches: when a user enters a page, the client serves cached data immediately if present; if cache age exceeds the configured timeout (TTL), the client triggers a background fetch; when the fetch completes, the cache is updated and the UI re-renders from the updated cache. The user is not blocked on this refresh path. Read may be paginated; when it is, the interaction declares `pageSize` explicitly.
- **Poll** conventions are distinct from stale-then-update reads. Poll is an explicit repeated read schedule (fixed interval, long-running status checks, or explicit timer-driven refresh) regardless of whether the user just entered the page. The response is always a full document written to the cache; the UI reads from the cache and is not blocked on the poll. Poll may also be paginated; when it is, `pageSize` is declared explicitly so each poll tick has deterministic payload and memory expectations.
- **Next** conventions describe explicit transitions to the next workflow state.
- **Done** conventions describe explicit transition to terminal/completed workflow states.
- **Redirect** conventions describe navigation or handoff flows where control moves to another route, screen, or external system.
- Interaction documents may include platform, country, capability, or feature conditions, but those conditions should refine the shared model rather than replace the common conventions.

Illustrative interaction examples:

The examples below focus on interaction behavior only. Structural schema rules (manifest shape, entity contracts, and payload schema content) are defined in ADR-0016.

Interaction document excerpt for **Create**:

```json
{
	"interactions": {
		"create": {
			"action": "profile.create",
			"entityId": "profile.current",
			"payload": {
				"source": "form.profile"
			}
		}
	}
}
```

Interaction document excerpt for **Update**:

```json
{
	"interactions": {
		"update": {
			"action": "profile.update",
			"entityId": "profile.current",
			"targetId": "profile.current"
		}
	}
}
```

Interaction document excerpt for **Read** (basic stale-then-update):

```json
{
	"state": "ready",
	"screen": "home",
	"interactions": {
		"read": {
			"cache": {
				"enabled": true,
				"ttlSeconds": 300,
				"strategy": "stale-then-update"
			},
			"action": "screen.home",
			"entityId": "home.screen"
		}
	}
}
```

Interaction document excerpt for **Read** (paginated):

```json
{
	"state": "ready",
	"screen": "home-feed",
	"interactions": {
		"read": {
			"cache": {
				"enabled": true,
				"ttlSeconds": 300,
				"strategy": "stale-then-update"
			},
			"action": "feed.home",
			"entityId": "feed.home",
			"pagination": {
				"enabled": true,
				"pageSize": 20,
				"cursor": "next"
			}
		}
	}
}
```

Interaction document excerpt for **Next**:

```json
{
	"interactions": {
		"next": {
			"to": "checkout.payment"
		}
	}
}
```

Interaction document excerpt for **Done**:

```json
{
	"interactions": {
		"done": {
			"to": "checkout.completed"
		}
	}
}
```

Interaction document excerpt for **Redirect**:

```json
{
	"interactions": {
		"redirect": {
			"to": "https://example.com/3ds",
			"type": "external"
		}
	}
}
```

Interaction document excerpt for **Poll** (basic explicit repeated schedule):

```json
{
	"state": "ready",
	"screen": "order-status",
	"interactions": {
		"poll": {
			"action": "order.status",
			"entityId": "order.status.current",
			"intervalMs": 5000,
			"stopWhen": "completed"
		}
	}
}
```

Interaction document excerpt for **Poll** (paginated):

```json
{
	"state": "ready",
	"screen": "orders-history",
	"interactions": {
		"poll": {
			"action": "orders.history",
			"entityId": "orders.history",
			"pagination": {
				"enabled": true,
				"pageSize": 20,
				"cursor": "next"
			},
			"intervalMs": 5000,
			"stopWhen": "manual"
		}
	}
}
```

These conventions should be represented explicitly in shared models and configuration so platform UI layers can interpret them consistently without inventing platform-specific semantics. Together, these conventions form the UI/UX contract of the app: any state document that stays within this contract can be rendered correctly by an app that has already shipped, without requiring an update.

Schema boundary:

- ADR-0006 defines behavioral interaction conventions only (Create, Update, Read, Poll, Next, Done, Redirect).
- Structural schema, entity definition contract, entity payload schema content, and OpenAPI alignment are defined in ADR-0016.
- The normative JSON Schema artifact remains `docs/schema/autumn-interactions.schema.json`.

## Consequences

- State and configuration gain a common language for interaction patterns.
- State documents gain a more consistent lifecycle vocabulary for common app flows.
- Platform-specific UI bridges can implement consistent behavior for the same interaction type.
- Clients and delivery services gain an explicit model separating timeout-based stale-then-update reads from true polling.
- Declared pagination parameters (`pageSize`, and related cursor semantics) remain explicit in interaction documents so behavior stays consistent across platforms.
- New features should align with existing conventions before introducing new interaction categories.
- A new screen that composes only existing conventions and resource identifiers requires no app update — it can be delivered entirely through configuration and new state documents.
- Removing Stream and Delta server conventions simplifies the interaction model significantly. Delta computation is the UI framework's job — React, Compose, and SwiftUI all have mature, battle-tested diffing engines that operate on the full document. Autumn gives them the full document and steps aside. The result is fewer moving parts, no server-side state tracking, and correct rendering behaviour on every platform without Autumn needing to implement or version a delta protocol.
- Future ADRs or module APIs may refine payload shapes and lifecycle details for each convention.
