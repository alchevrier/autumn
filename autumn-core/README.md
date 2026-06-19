# autumn-core

Core interfaces and shared domain models for Autumn. This module defines the foundational contracts used by the state, configuration, resolver, bucket, and UI modules.

## Circuit-Based Foundations

In standard Android/iOS development, core modules hold data classes (`User`, `Article`). In Autumn, `autumn-core` primarily holds the **Compiler Pacts** used to construct Hardware-Sympathetic boundaries:

- `@LongLived`: Enforces strict compile-time checks (via K2 plugin) that objects matching this annotation never allocate on the heap during usage, ensuring they remain in L1-friendly flat memory.
- `@InjectBudget`: Binds domain constraints (e.g., UI pagination bounds) directly to the compiler. The compiler intercepts this and statically injects an integer literal constraint for matrix allocations.
- `@NetworkConcurrencyBudget`: Eliminates network queuing avalanches by capping maximum concurrent operations at compile time, providing a limit for instant-failure Circuit Breakers.
