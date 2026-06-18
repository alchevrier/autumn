# ADR-0009: IoC Container and Lazy Initialization

## Status

Proposed

## Date

2026-06-17

## Context

Autumn spans multiple modules — core, state, buckets, resolver, config, and UI — each of which depends on shared long-lived objects such as the HTTP client, the credential store, the configuration cache, the country resolver chain, and the bucket registry. Without a shared container, each module would construct its own instances, making it hard to share state, swap implementations for testing, and control when expensive initialization occurs. Eager initialization of all dependencies at startup would also increase cold-start time on mobile, where startup latency is directly visible to the user.

## Decision

Autumn uses a shared IoC container with lazy initialization for long-lived objects.

- A single container is constructed once at application startup and passed to or made accessible by each module that needs it.
- Dependencies are registered in the container as lazy providers. A provider is not invoked until the first time the dependency is requested; subsequent requests return the same instance.
- Long-lived objects — such as the HTTP client, the credential store, the active configuration, the country resolver chain, the bucket registry, and the resource cache — are registered as singletons in the container.
- Short-lived or per-request objects are not managed by the container; they are constructed directly at the call site.
- Dependencies are declared by interface so implementations can be replaced without changing call sites. This makes the container the single point where platform-specific or environment-specific implementations are wired in.
- Platform-specific registrations use Kotlin Multiplatform `expect`/`actual` so each platform provides the correct implementation of platform-sensitive dependencies such as the secure credential store, the platform HTTP engine, and the country resolver chain.
- On web, the lazy initialization boundary aligns naturally with React's component model. Each page that needs a service accesses it through a dedicated custom hook file. The hook calls `useEffect` to initialize the service on first mount and returns it for use within that page. The service instance itself lives in the container and is only constructed the first time a page that needs it is opened. Subsequent navigations to the same or other pages that use the same hook receive the already-constructed instance from the container without re-initializing it.
- On Android, the pattern is equivalent. A screen-scoped ViewModel accesses the container through `viewModel()` in Jetpack Compose, which constructs the ViewModel lazily on first composition. Side-effecting initialization within the ViewModel runs in `LaunchedEffect` or `init`, mirroring the role `useEffect` plays on web. The ViewModel is the access layer between the Compose screen and the container; screens never touch the container directly.
- On iOS, SwiftUI's `@StateObject` plays the same role. A ViewModel declared as `@StateObject` is constructed lazily when the view first appears and is kept alive for the lifetime of that view. Initialization side effects run in `.task` or `.onAppear`, the SwiftUI equivalents of `useEffect` and `LaunchedEffect`. The ViewModel resolves its dependencies from the container; the view itself remains unaware of the container.
- Across all three platforms the pattern is the same: a screen-scoped object (hook, ViewModel, or StateObject) acts as the single access point between the UI and the container, is constructed lazily on first screen open, and is never re-constructed on subsequent visits.
- The container must not hold references to UI components, view models, or any object with a lifecycle shorter than the application. Those objects manage their own dependencies.

## Consequences

- Cold-start cost is reduced because expensive objects are only constructed when first needed, not all at startup.
- Each long-lived object is constructed exactly once and shared across all modules that need it, eliminating duplicate state and redundant initialization.
- Swapping an implementation — for example replacing the HTTP client with a mock during testing — requires only a single registration change in the container.
- Platform-specific wiring is isolated to the container registration layer, keeping module code platform-agnostic.
- On web, hook files act as the access layer between React components and the container. A component never touches the container directly; it calls a hook, and the hook resolves the dependency. This also means the hook file is the natural place to write a test double — swap the container registration and every component using that hook gets the mock automatically.
- The container itself becomes a dependency of the application entry point and must be initialized before any module that relies on it.
- Care is needed to avoid circular dependencies between lazy providers, which would cause initialization to deadlock or fail at first access rather than at registration time.
- Future ADRs or module APIs may refine the container API, scoping rules, and the boundary between container-managed and manually constructed objects.
