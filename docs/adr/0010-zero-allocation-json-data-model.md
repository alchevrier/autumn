# ADR-0010: Zero-Allocation JSON Data Model

## Status

Proposed

## Date

2026-06-17

## Context

Autumn passes JSON documents through multiple layers — network response, configuration resolution, state production, UI rendering, and API forwarding. A conventional approach deserializes JSON into allocated Kotlin objects, operates on those objects, then re-serializes to JSON for outgoing calls. This produces allocations at every boundary, increases GC pressure on mobile where heap pressure and GC pauses are directly visible to the user, and introduces the risk of a round-trip changing the representation of fields the application did not touch. Autumn needs a model that eliminates these allocations without sacrificing a typed, readable API.

The foundational principle across Autumn's entire data layer is **array-based, pointer-free data structures**. All internal data — configuration tables, resource registries, bucket mappings, list items, and form state — lives in flat pre-allocated `ByteArray` pools accessed by integer index and byte offset. There are no object graphs, no pointer chains, and no GC-visible references between data items. A field read is an integer multiply and an array offset load. This eliminates pointer indirection overhead, maximises cache line utilisation, and removes allocation pressure from the hot paths of the framework. The JSON data model described in this ADR is one application of this principle to the specific problem of mutable documents that cross network boundaries.

## Decision

Autumn uses a zero-allocation JSON data model where all data objects wrap the raw JSON representation directly.

- Every data object holds a single underlying raw JSON value — a byte array or string containing the original JSON received from the network or configuration store.
- Getters read field values by navigating the raw JSON structure on access. No intermediate object is allocated for the document as a whole; only the primitive value at the accessed field is produced.
- Setters modify the underlying raw JSON representation in place. The object remains the raw JSON; the setter produces a new raw JSON value with the target field updated rather than copying an object graph.
- When an API call is made, the underlying raw JSON is forwarded directly as the request body. No re-serialization step is needed because the representation was never converted away from JSON.
- Untouched fields are preserved exactly as received — byte-for-byte — because the raw representation is the source of truth, not a deserialized object graph reconstructed from it.
- This model applies to all data objects that cross a network boundary or require mutation: state documents, configuration documents, credential envelopes, and similar payloads.
- **Exception: read-only lists and tables use an Array of Structs layout.** Data that is loaded once from configuration and never mutated — such as the resource registry, the country bucket table, the experiment variant table, and the resolver chain configuration — is decoded once at configuration load time into a flat AoS pool. Each entry occupies a fixed-size slot at a known byte offset in a pre-allocated `ByteArray`. Access is an index multiply and offset read; no JSON navigation is needed at runtime. The pool size and slot layout are derived from the configuration schema, which is known before any data arrives. Because these structures are read-only after load, there is no mutation concern and no JSON passthrough requirement, so the ingress parse cost is paid once and then eliminated for the lifetime of the configuration.

### Concrete SBE mechanism

- Autumn defines a portable `SbeArena` abstraction in common code. All slot reads and writes go through this API using `(slotIndex, fieldOffset)` addressing.
- The default implementation is `ByteArrayArena` in commonMain so the same mechanism works on Android, iOS, and Web.
- `nativeStack` is not used for the arena. It is Kotlin/Native cinterop scope memory, unavailable in common code, and valid only inside a `memScoped` lifetime. Arena slots are long-lived and must survive across frames and requests, so `nativeStack` is the wrong lifetime model.
- Platform-specific fast paths may exist behind the same interface (for example direct/native buffers), but they must preserve the same slot addressing semantics and pass the same tests.
- Slot and pool sizes are generated from configuration: `slotBytes = fixedFieldBytes + alignmentPadding + unknownBlobBytes`; `slotCount` is derived from declared capacities (for example list `activePagesOnScreen`, `bufferPages`, `evictionBoundaryPages`, and `slotsPerPage`); `poolBytes = slotBytes × slotCount`. These values are emitted as generated constants and validated at startup.

## Consequences

- No heap allocation occurs for document objects at deserialization or serialization boundaries. The only allocations are the primitive values returned by individual field accesses.
- GC pressure is significantly reduced on Android and iOS, where allocation rate directly affects frame times and battery consumption.
- Outgoing API calls forward the raw JSON without a re-serialization step, which eliminates the risk of field value drift caused by round-tripping through a typed object representation.
- Untouched fields are guaranteed to be preserved exactly as received, which is important for fields the client does not understand or does not need to inspect.
- JSON navigation on each field access is slightly more expensive than reading a struct field, but this is offset by the elimination of upfront deserialization cost and the improvement in cache locality from not allocating a large object graph.
- Read-only list and table data — resource registry, country bucket table, variant table, resolver configuration — pays the JSON parse cost once at configuration load and then provides O(1) AoS slot access for the lifetime of that configuration. This is strictly cheaper than raw JSON navigation for data that is read frequently and never mutated.
- The two storage strategies are complementary and share the same `autumn-core` access abstraction: mutable documents use raw JSON views; read-only tables use AoS pools. Callers above `autumn-core` are unaware of which strategy is in use.
- The model requires a JSON access layer that can navigate and patch raw JSON efficiently. This layer is a core part of `autumn-core` and must be implemented before any other module can define data objects.
- Future ADRs or module APIs may define the specific JSON navigation strategy — such as index-based access, path-based access, or lazy parsing — the in-place mutation approach used by setters, and the slot layout schema for each AoS pool.

## Alternatives Considered

### Pre-allocated pool with SBE views

An alternative model pre-allocates a large contiguous memory pool at startup and stores all document data inside it as a flat Array of Structs. A Simple Binary Encoding (SBE) codec acts as a zero-copy view over the pool: field access is a fixed-offset read into the pool buffer, and mutation is a fixed-offset write. When a new document object is needed, the allocator assigns the next available slot index and zeroes it. The object identity is the slot index, not a pointer; releasing it returns the slot to the pool without GC involvement.

**Advantages over raw JSON views:**
- Field access is O(1) at a known byte offset — faster than JSON navigation, which must scan for field names or maintain a parsed index.
- The pool is a single contiguous allocation; all document data lives in a predictable memory region with strong cache locality.
- No GC involvement at any point — allocation is an index increment, release is an index decrement or free-list push.
- Mutation is a direct write at a known offset with no JSON patching logic.

**Why not chosen for the current decision:**
- Autumn's documents arrive as JSON from remote APIs and are forwarded as JSON to remote APIs. A pool-and-SBE model requires a decode step (JSON → pool) on ingress and an encode step (pool → JSON) on egress. These are one-time boundary costs per document rather than per-field costs, but they do reintroduce an allocation and a parsing step at the network boundary that the raw JSON model avoids entirely.
- The schema objection is substantially resolved by Autumn's configuration model: because document field layouts are declared in configuration, the exact pool size and field byte offsets for each document type can be computed at configuration load time — before any document arrives. Known fields map to fixed-offsets in the pool; a raw JSON blob appended to each slot covers any unknown or forward-compatible fields that must be preserved and forwarded without interpretation. This is a viable hybrid: SBE-style access for declared fields, raw passthrough for the rest.
- The hybrid model is the correct direction if the ingress/egress JSON parse cost becomes a measurable bottleneck. The `autumn-core` access layer is intentionally isolated so that the storage strategy — raw JSON views today, pool-plus-SBE tomorrow — can be replaced without changing the module APIs above it.
- Kotlin Multiplatform targeting JavaScript and WASM does not expose direct memory management. A pool implemented as a `ByteArray` works on all targets, but care is needed on JS where the JVM-style memory layout does not apply; byte offset arithmetic must be validated per target.
- The pool approach becomes the clear winner if Autumn's wire format ever moves to binary end-to-end. If the API contract changes from JSON to SBE or FlatBuffers natively, the one-time ingress/egress cost disappears and the pool model is strictly superior. This decision should be revisited at that point.
