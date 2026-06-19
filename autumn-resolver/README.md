# autumn-resolver

Country resolver module for Autumn. This module contains the `CountryResolver` abstraction and resolver composition for platform-specific country detection.

## The Network Air Gap

This module enforces a strict lock-free, zero-allocation boundary between the noisy OS network stack and the Autumn state machine.

- `AutumnNetworkEngine`: Translates standard Promises/Flows into a **"Fire and Forget In-Place"** paradigm.
- Requests do not return DTOs. They trigger an asynchronous fetch that pipes bytes straight to `autumn-config` and returns `Unit`.
- `NetworkSlotManager`: Utilizes compiler bounds to provide an `O(1)` local circuit breaker. If the application exceeds the concurrent request budget, failures trigger instantly locally, preventing network pile-ups, OOMs, and battery drain.
