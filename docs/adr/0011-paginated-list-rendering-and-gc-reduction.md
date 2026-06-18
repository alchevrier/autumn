# ADR-0011: Paginated List Rendering and GC Reduction

## Status

Proposed

## Date

2026-06-17

## Context

Paginated and scrollable lists are one of the largest sources of GC pressure in mobile UI frameworks. A naïve implementation allocates a new item object for every row as it scrolls into view, immediately making the previous row's object unreachable. At typical scroll speeds this produces a continuous stream of short-lived allocations that trigger frequent minor GC cycles, causing frame drops directly visible to the user. Autumn needs a list rendering model that bounds allocations to a fixed number of view slots regardless of list length or scroll speed, and that connects directly to the zero-allocation data model established in ADR-0010.

## Decision

Autumn uses a fixed-slot view recycling model for all paginated and scrollable lists, backed by index-based access into the AoS data pool.

- The visible window of a list is represented as a fixed pool of view slots — typically the number of visible rows plus a small buffer above and below the viewport. The window and buffer are declared in configuration (for example `activePagesOnScreen` and `bufferPages`) and compiled into constants. At runtime, list mount selects from these precomputed constants; it does not compute or resize capacity dynamically.
- Each slot holds a reusable view object. On all three platforms the platform's native recycling mechanism is used: `RecyclerView` with `ViewHolder` on Android, `UICollectionView` with cell reuse on iOS, and a virtual list library such as `TanStack Virtual` on web. In all cases the platform recycles the view object; only the data bound to it changes.
- Item data is not allocated per row. The backing data for the list lives in the AoS pool defined in ADR-0010. Binding a slot to a row is a single integer assignment — the new row index — followed by O(1) offset reads into the pool. No object is instantiated for the item data at any level.
- When the user scrolls and a slot leaves the viewport on one side, it is reassigned to the incoming row on the other side. The slot object is not released and re-allocated; its bound row index is overwritten with the new integer. The pool is not touched until the slot renders.
- Item objects — wrapper types that represent a single list row — are never created. The row index is a plain integer. The UI layer reads field values directly from the pool using that integer as the slot address. There is no intermediate object between the integer and the pool read.
- Pagination loading — fetching the next page from the network — appends new entries to the AoS pool. The pool is pre-allocated with capacity for a configurable number of pages so appending does not trigger a pool resize during normal scrolling. The visible slot pool is unaffected by pagination; only the index range it can address grows.
- The data pool is divided into a **hot tier** and a **cold tier** to bound total memory use for very long lists. The hot tier is a fixed-size contiguous region declared in configuration — for example, enough slots for N pages around the current scroll position. The cold tier is a compact serialised representation stored in memory or on disk outside the pool. When the user scrolls far enough that rows at the far edge of the hot tier are no longer near the viewport, those slots are evicted to cold storage and their slot indices are freed. When the user scrolls back toward evicted rows, the cold entries are loaded back into freed hot slots before the view slot binds to them. The hot tier size is configurable per list type; a short bounded list may have a hot tier large enough for the entire dataset and never evict. A long feed or search result with potentially thousands of rows uses a smaller hot tier and relies on eviction to keep the pool footprint constant.
- The eviction boundary is defined as a configurable number of viewport-heights above and below the current scroll position. Rows outside this boundary are candidates for cold eviction. Rows within it are guaranteed to be in the hot tier. This gives a predictable and tunable memory footprint generated pre-compile from configuration: `hot_slot_count = (active_pages_on_screen + 2 × buffer_pages + 2 × eviction_boundary_pages) × slots_per_page`.

## Consequences

- Allocations during scrolling are bounded to zero item-object allocations per frame. The row index is a plain integer passed to the pool reader; no object is instantiated at any point in the item data path. The only values produced are the primitives returned by individual field reads, which the platform runtime typically stack-allocates or interns.
- There is no item object lifecycle to manage. There is nothing to allocate when a row enters the viewport and nothing to release when it leaves — only an integer changes.
- List length does not affect the number of live objects. A list of ten items and a list of ten thousand items use the same number of view slots and the same pool access pattern.
- Pagination appends to the pool without reallocating the visible slot pool, so fetching the next page does not disturb the currently visible rows.
- The index-cursor pattern for row access is consistent with the raw JSON view pattern in ADR-0010: a lightweight handle over a backing store, not an independent allocated object.
- Platform-specific virtual list or recycling implementations must be used correctly. Incorrect slot pool sizing — too few buffer slots — can cause visible blank frames during fast scrolling. The slot count formula must account for the maximum scroll velocity expected on each platform.
- The hot tier size sets a hard upper bound on the data pool memory footprint regardless of list length. A list of 10 items and a list of 100,000 items consume the same hot tier memory; only the cold tier grows.
- Eviction and reload are synchronous with respect to the slot binding step. A slot must not bind to a row whose data is not yet in the hot tier. If a fast scroll outruns the eviction/reload cycle, the slot displays a loading placeholder until the hot tier is populated. The eviction boundary must be sized generously enough that this is rare under normal scroll speeds.
- Cold storage format is compact and platform-appropriate: a byte-serialised copy of the slot region written to an in-memory ring buffer for recent evictions, and optionally to disk for very large datasets. Re-loading a cold entry is a sequential read and a `ByteArray` copy into the freed hot slot — no allocation, no parsing.
- Future ADRs or module APIs may define the slot count formula, the eviction boundary formula, the cold storage format, and the interaction between pagination loading states and hot/cold tier transitions.
