# ADR-0008: Form State Management via Pre-Allocated Slots

## Status

Proposed

## Date

2026-06-17

## Context

Forms are a common source of repeated short-lived allocations in UI frameworks. A conventional implementation allocates a new state object when a form is opened, populates it as the user types, and discards it on submit or cancel. If the user opens and closes the same form repeatedly — a login form, a search field, a payment form — the runtime allocates and discards the same object shape on every cycle, producing predictable GC pressure at exactly the moments the user is actively interacting. Autumn's allocation model should apply the same pre-allocation principle to form state that ADR-0010 and ADR-0011 apply to data and list rendering.

## Decision

Each form type is assigned a dedicated pre-allocated slot in a shared form state pool. The slot is zeroed on open and returned to a ready state on submit or cancel, never released.

- At configuration load time, the set of form types in the application is known from the document schema. A form state pool is allocated with one slot per distinct form type. Slot size is the maximum field count and field size declared for that form type in the schema.
- When a form is opened, its slot is zeroed — all fields reset to their empty representation — and the slot is handed to the form view as its backing store. No object is instantiated; the slot is a region of the pool `ByteArray` identified by its slot index.
- As the user edits fields, writes go directly into the slot at the pre-computed field offset. No intermediate state object is created; the slot is the form state.
- On submit, the slot contents are read directly and forwarded to the API call as per the raw JSON forwarding model in ADR-0010. The slot is then zeroed and marked available.
- On cancel or dismiss, the slot is zeroed and marked available immediately. No cleanup of allocated objects is needed because no objects were allocated.
- Only one instance of each form type can be active at a time in the default model. If a form type must support concurrent instances — for example a modal opened from within a list row — the pool can allocate a small fixed number of slots for that type, declared in the schema. The maximum concurrency is bounded and known at configuration load time.

## Consequences

- Opening and closing a form allocates nothing. The cost is a `memset`-equivalent zeroing operation on the slot region, which is a sequential write into a `ByteArray` and is predictable and fast.
- Repeated open/close cycles on the same form — login retries, repeated search, re-opening a settings form — produce zero GC pressure regardless of frequency.
- Form state exists in a contiguous, pre-known memory region. There is no object graph to traverse, no reference to null out, and no finalizer to run on dismiss.
- The slot zeroing on open acts as an implicit reset, eliminating a class of bugs where stale field values from a previous session leak into a freshly opened form.
- The maximum number of simultaneously active form instances is declared in the schema and enforced by the pool. Attempting to open a sixth instance of a form with a pool of five slots is a detectable, handleable condition rather than an unbounded allocation.
- Form field writes are direct offset writes into the pool, consistent with the AoS field access pattern in ADR-0010. There is no boxing, no event object, and no intermediate state copy on each keystroke.
- Future ADRs or module APIs may define the slot zeroing strategy, the schema declaration for form concurrency limits, and the interaction between form slot state and the authentication workflow FSM defined in ADR-0008.
