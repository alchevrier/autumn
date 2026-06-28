# Autumn IDE Plugin (Performance Center)

The `autumn-ide-plugin` bridges the brutal, uncompromising hardware constraints of Autumn's HFT roots directly into your daily developer environment.

Instead of waiting for a high-intensity native benchmark to crash or report latency spikes, this IntelliJ IDEA tool actively parses the topology JSON emitted by the **Autumn K2 Compiler**.

## Key Capabilities

1. **Inline Execution Lenses (Gutter Icons)**  
   As you type, gutter indicators visually light up next to your `@Observe` and `@CycleBudget` pipeline handlers. You will see instantaneous feedback directly inside your editor: `⚡ 37 / 60 Cycles | Port 1 ALU Pressure`.

2. **The Output Tool Window (JetBrains Compose)**  
   Open the **"Autumn Performance Center"** tab on the right side of the IDE. A reactive Compose dashboard automatically discovers your local `topology.json` artifacts and updates in real-time to surface:
   - Your hardware partition setup and execution constraints.
   - The unrolled topological graph of channels to handlers (e.g. `BoundaryChannel` -> `@Observe fastPath()`).
   - Sizing structures (RingBuffer allocations, etc).

Because the heavy math, syntax tree analysis, and hardware opcode mapping are structurally tied to the compiler natively, this plugin has a near-zero performance cost on IntelliJ syntax highlighting. It faithfully mirrors the *exact mathematical reality* the compiler utilizes to build your native byte code!

## Testing Locally
To launch a sandbox version of IntelliJ loaded with this plugin:
```bash
./gradlew :autumn-ide-plugin:runIde
```
