# autumn-state

State management for Autumn. This module owns the documents and orchestration that describe what the UI should render, treating the application as a finite-state machine whose current state and transitions are defined by documents.

## Emulated Hardware Interrupt Moderation

To ensure rendering is perfectly efficient, the state machine does not emit unique Reactive Streams (no `Observer`, no `LiveData` per item). It employs hardware engineering design:

- `EpochStateEngine`: Mimics Version Registers and high-performance NIC Interrupt Coalescing.
- When background operations alter memory slots, their respective `IntArray` epoch ticks forward. 
- A single global hardware wire (`SharedFlow` configured with `DROP_OLDEST`) pulses.
- Multiple high-frequency mutations collapse into exactly **one** UI thread wakeup, completely preserving the C-State limits of mobile processors for massive battery improvements.
