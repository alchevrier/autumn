# ADR-0014: Event Loop Model and Context Switch Minimisation

## Status

Proposed

## Date

2026-06-17

## Context

Smooth UI on mobile requires the main thread to be available for rendering at every frame boundary — 16ms at 60Hz, 8ms at 120Hz. OS-level thread context switches are expensive on mobile SoCs: they flush the CPU pipeline, displace cache lines, and may trigger the scheduler to migrate a thread to a different core with a cold cache. An application that creates threads freely produces a high and unpredictable number of context switches that cause jank the pre-allocated data structures from ADR-0010 through ADR-0013 cannot compensate for. An event loop model avoids OS context switches entirely within a loop: work is cooperative, suspension is a function call, and the scheduler is not involved between tasks. Autumn needs an execution model that produces a small, fixed set of event loops with known categories, no blocking, and minimal OS scheduler involvement.

## Decision

Autumn organises all execution into four named event loops. Each loop is a single-threaded coroutine dispatcher backed by Kotlin Coroutines. Work is enqueued by posting a suspending lambda to the target loop. The public API is:

```kotlin
EventLoop.Main.queue { /* runs on main/UI loop */ }
EventLoop.Network.queue { /* runs on network loop */ }
EventLoop.Config.queue { /* runs on configuration loop */ }
EventLoop.Decode.queue { /* runs on decode loop */ }
```

`queue` is a suspend function when called from a coroutine, and a fire-and-forget post when called from non-suspending code. Return values are carried back via the coroutine result or a completion callback descriptor posted to the caller's loop.

- **`EventLoop.Main`** — the UI event loop. Drives frame rendering, user input, and FSM state transitions. No suspending network, I/O, or decode work runs here. On Android and iOS this is the platform main thread's event loop. On web this is the JavaScript event loop — no separate thread is needed or created.
- **`EventLoop.Network`** — owns all outgoing HTTP requests and incoming response processing. Uses async I/O internally so a slow response does not block the loop. On web, this loop is implemented as a thin wrapper over `fetch` promises chained back into the JS event loop; no Web Worker is needed because `fetch` is non-blocking on web by design.
- **`EventLoop.Config`** — owns configuration loading, version checks, hot/cold tier eviction for list pools (ADR-0011), and form slot pool management (ADR-0012). It is the sole writer to configuration AoS pools and list data pools. The main loop reads from these pools without synchronisation because writes complete before the index is published.
- **`EventLoop.Decode`** — owns asset decompression and cold-to-hot slot reloads. Writes into the hot tier slot and publishes the index only after the write is complete. On web, CPU-intensive decode work is offloaded to a Web Worker; the Decode event loop posts the task to the worker and resumes the coroutine on the JS event loop when the worker responds.
- The underlying dispatcher for each loop on Android and iOS is a `SingleThreadDispatcher` — one OS thread per loop, four threads total at startup. Coroutine suspension within a loop is cooperative and does not involve the OS scheduler. Context switches only occur when work crosses loop boundaries, which is bounded and intentional.
- The single-writer discipline per pool region is enforced by loop ownership: only the loop designated as writer for a given pool region ever calls its write API. Publishing an index is the memory visibility fence; no lock is needed.

## Web platform note

Web has one JS thread. `EventLoop.Main` and `EventLoop.Network` are both hosted on it — network work is naturally non-blocking via `fetch`. `EventLoop.Config` is also on the main thread for lightweight work; heavy configuration parsing is deferred to a Web Worker if it would block a frame. `EventLoop.Decode` uses a dedicated Web Worker. The `EventLoop` API is identical across platforms; the dispatcher implementation differs per target via Kotlin Multiplatform `expect`/`actual`.

## Callback vs reactive: communication style decision

Two common styles exist for consuming results across loop boundaries: **callbacks** and **reactive streams** (Flow, RxJava, Combine). Autumn uses **coroutine suspend functions as the primary style**, with `Flow` for ongoing multi-value streams. Neither raw callbacks nor a reactive framework is used as the default. The reasoning:

**Raw callbacks** — straightforward for single results but produce callback nesting when operations compose (`onSuccess` → start next operation → `onSuccess` → ...). Error propagation is manual at every level. Cancellation requires explicit handle management. On web this is the pre-`async/await` model; it is now considered legacy precisely because it does not compose.

**Reactive streams (RxJava, Combine, custom Observable)** — solve composition and propagation but introduce a full framework with its own scheduler, operator chain allocation, and subscription lifecycle. An Observable chain allocates an operator object per operator. On mobile, a reactive stream processing a list update produces allocations proportional to the operator chain length on every emission — the opposite of what ADR-0010 through ADR-0012 work to achieve. They are also a large conceptual surface for contributors to learn and a source of subtle bugs (cold vs hot, backpressure, scheduler misuse).

**Coroutine suspend functions** — a single result from a cross-loop call is `await`ed at the call site with no allocation beyond the coroutine continuation, which is allocated once per coroutine and reused across all its suspension points. Error propagation is structured exception handling — the same as synchronous code. Cancellation is cooperative and scope-based, tied to the IoC container lifecycle (ADR-0009). Composition is sequential `val a = loop.queue { }; val b = loop.queue { }` — readable, flat, no nesting.

**`Flow` for ongoing streams** — configuration updates, list pool eviction signals, and auth state transitions are ongoing multi-value sources. `Flow` is Kotlin's cold stream primitive and is the correct tool here. A `Flow` carries values lazily; only the currently collected value is live. It does not allocate an operator graph upfront. Autumn uses `StateFlow` for state that has a current value (auth FSM state, active configuration version) and `Flow` for event streams (pool eviction triggers, network completion signals).

**There is no streaming of data from the network.** Every network response is a complete document or a complete page of list items — fetched once, written to the cache, and served from the cache immediately on the next read. The cache is refreshed in the background by the Network loop after a mutation or on a revalidation interval. `Flow` is used internally to propagate cache update signals between the Network loop and the Config loop, not to stream partial data from the server. This means the reactive surface is small and internal; application code sees suspend functions and `StateFlow`, never an ongoing data stream from a server.

**Summary:** suspend functions for one-shot cross-loop calls; `StateFlow` for current values (auth state, active config); `Flow` for internal loop-to-loop signals. No raw callbacks, no reactive framework dependency, no server-side streaming of data.

## Consequences

- Coroutine suspension within a loop is a cooperative yield — no OS context switch, no cache flush. The OS scheduler only sees four threads on mobile; on web it sees one thread plus one Web Worker for decode.
- The `EventLoop.X.queue {}` API is the only way to cross loop boundaries. All cross-loop communication is explicit, auditable, and does not require mutexes or condition variables.
- The main loop is never blocked. Frame delivery depends only on the time to read field values from the hot tier pool — an integer offset load — and the time to drain the main loop's pending completions.
- The fixed loop topology makes scheduler priority hints worthwhile on Android: `setThreadPriority(THREAD_PRIORITY_DISPLAY)` on the main loop gives it higher CFS weight in the runqueue. True core pinning is not achievable — Android is a multi-app OS and affinity hints are advisory, not exclusive. The practical gain is a reduced probability of preemption in favour of lower-priority work, not guaranteed core access.
- Incorrect loop discipline — writing to a pool from a loop that is not the declared owner — produces data races. The pool write API must be internal to `autumn-core` and only callable from the owning loop's context, enforced at compile time where possible.
- Future ADRs or module APIs may define the Web Worker messaging protocol for the Decode loop, the CPU affinity hint mechanism per platform, and the coroutine scope lifecycle tied to the IoC container defined in ADR-0009.

## Honest per-platform analysis of context switch reduction

The model does not eliminate OS-level context switches — it bounds and predicts them. The distinction matters.

### Android

Four OS threads (pthreads) are live from startup. The Linux CFS scheduler preempts all threads at timer interrupts regardless of what they are doing. What the model changes: it replaces an unbounded, dynamic number of threads (one per request, one per task) with a fixed four. Preemption frequency becomes proportional to time, not to request volume. Crucially, each thread's working set — socket state, pool write cursor, codec buffers — stays resident in L1/L2 between preemptions because no other thread competes for the same cache lines. Coroutine suspension within a loop is a cooperative yield with no OS involvement: the scheduler is not called, the thread is not descheduled, and the cache is not displaced. The practical result is a large reduction in unplanned context switches and eliminated cache misses from inter-thread working set competition, but not zero OS preemptions.

CPU affinity on Android deserves a more honest assessment. `Process.setThreadPriority` and thread affinity APIs exist, but Android is a multi-app OS — the scheduler serves all foreground and background processes simultaneously. Even if Autumn pins its main loop to a performance core, another app, a system service, or a kernel interrupt handler may preempt it. Hardware affinity hints via the NDK are advisory on most Android OEM kernels, not enforced. The meaningful gain from `setThreadPriority(THREAD_PRIORITY_DISPLAY)` on the main loop is that the CFS scheduler gives it a higher weight in the runqueue, reducing the probability of preemption in favour of lower-priority work — not that it gets exclusive core access. The fixed loop count still helps here: four predictable high-weight threads are easier for the scheduler to service consistently than a dynamic pool of unknown size.

### iOS

iOS uses GCD (Grand Central Dispatch) as its scheduler. When Kotlin Coroutines targets iOS, the `SingleThreadDispatcher` maps to a GCD serial queue. GCD manages its own thread pool internally and may not create exactly one pthread per serial queue — it reuses pool threads across queues. This means the OS-level thread count may be lower than four (GCD may multiplex queues onto fewer threads) but it also means the mapping is not fully deterministic. The cooperative suspension guarantee within a loop still holds at the coroutine level — GCD processes one task from a serial queue at a time — but the thread the task runs on may vary between GCD scheduler decisions.

The practical consequence: the model reliably eliminates unnecessary context switches at the coroutine layer on iOS, but the underlying OS thread count is a GCD implementation detail. Frame jank caused by GCD thread pool contention is uncommon in practice given that the four serial queues carry light, predictable workloads.

### Web

Web is where the model is most effective. The JS event loop is single-threaded. `EventLoop.Main` and `EventLoop.Network` both run on it — there is no OS context switch between them at all; they interleave cooperatively at `await` boundaries. `EventLoop.Config` similarly runs on the JS thread for most work. The only OS thread boundary is `EventLoop.Decode` crossing to a Web Worker via `postMessage`. This is one explicit, auditable cross-thread communication point per decode operation, not a continuous stream of OS switches. On web, the model effectively eliminates all intra-framework context switches except the decode boundary.

### Summary

| Platform | OS context switches eliminated | Remaining switches | Primary gain |
|---|---|---|---|
| Android | Intra-loop (coroutine suspension) | Timer preemption on 4 fixed threads | Fixed working set, no cache displacement between loops |
| iOS | Intra-loop (coroutine suspension) | GCD thread pool decisions | Cooperative loop scheduling, predictable serial queue ordering |
| Web | All intra-framework switches | postMessage to Decode Worker | Near-zero switches for Main + Network + Config |

The honest claim is: the model bounds and predicts context switch frequency, eliminates unnecessary intra-loop switches, and stabilises each loop's cache working set. It does not guarantee zero OS preemptions on Android or iOS.
