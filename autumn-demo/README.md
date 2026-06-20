# Autumn Demo: End-to-End Circuit Architecture

This module provides a complete, end-to-end demonstration of the **Autumn framework's circuit-based, zero-allocation frontend model**. It consists of a Kotlin/JVM backend orchestrating static payload handoffs and a Kotlin/JS frontend that renders them using native DOM elements—all without allocating a single intermediate DTO or object tree on the hot path.

## The Zero-Allocation Client Architecture

The Autumn client operates radically differently from traditional frontend frameworks like React or Compose (which constantly allocate VDOM nodes and state closure objects). The client is structured like a System-on-a-Chip (SoC):

1. **`AutumnMotherboard`**: The entry point that allocates fixed-size "hardware registers" (`IntArray`, `ByteArray`) at startup.
2. **`AutumnNetworkEngine`**: A strictly bounded gateway. It claims a concurrency slot, fetches bytes from the OS network stack, and hands them to the parser.
3. **`JsonConfigParser`**: A flat state machine that reads raw network `ByteArray` streams. **It never instantiates objects.** As it finds strings and components, it writes indices and offsets into the motherboard's `StringRegistry` and `ConfigManager` (backed by a flyweight `ByteArrayBucketPool`).
4. **`EpochStateEngine`**: An emulated hardware interrupt wire. Once the parser writes the data, the state engine pulses.
5. **`AutumnCircuitBinder`**: The final UI platform adapter (in this demo, native HTML DOM). It wakes up on the pulse, sweeps over the pre-allocated integer matrices, and lazily constructs the actual platform rendering string (HTML) at the very last millisecond. 

## The Proof: How Memory Allocations are Derived

To guarantee a system will not run out of memory or trigger Garbage Collection hiccups, the memory limits cannot be infinitely dynamic. The Autumn Client operates on a **statically derived memory budget**.

### 1. The Schema Source of Truth
We define the exact structural limits of our layouts in a schema, such as `autumn-schema.json`:
```json
{
  "schemaVersion": "1.0.0",
  "screens": {
    "list": {
      "staticComponents": 5,
      "paginatedLists": 1,
      "itemsPerPage": 5
    }
  }
}
```

### 2. K2 Compiler Matrix Sizing
In `Client.kt`, we declare our motherboard budgets initialized to `0`, but we annotate them with the Autumn compiler directive:
```kotlin
@InjectBudget(fromSchema = "autumn-schema.json")
val bucketConfigLimits = 0
```
During the compilation phase, the **Autumn K2 Compiler Plugin** physically intercepts the AST. It reads the local `autumn-schema.json`, does the math (`5 static + (1 list * 5 items) = 10`), and physically replaces the `0` in the bytecode with `10`.

This proves the model: **The client physically cannot allocate more memory than its layout mathematically requires.**

## Handling Endless Dynamic Data (Pagination)

If the client strictly bounds its memory to 10 nodes (per the schema), how does it handle an employee directory of 1,000 people?

It enforces **Server-Side Bounds adherence**. 

1. **Backpressure:** The client tells the server: "I only have 10 layout slots."
2. **Pagination:** The server (`Server.kt`) executes standard pagination (`/api/state/list?page=1&query=...`), returning exactly 5 items and pagination buttons. 
3. **Arena Reset:** When the user clicks "Next Page", the JS Client invokes `motherboard.hardReset()`. This **does not delete objects**. It simply resets the internal array counters (size = 0).
4. **In-Place Overwrite:** The new network payload streams in, and the `JsonConfigParser` seamlessly overwrites the old employee integers in the exact same memory addresses.

**Result:** You can scroll a list of 1,000,000 employees forever on a flat, 10-item primitive `IntArray` without triggering the Garbage Collector once.

## Running the Demo

Compile and spin up the integrated JVM/JS server:
```bash
./gradlew :autumn-demo:runDemoServer
```
Open your browser to `http://localhost:8080/`. You will see the native HTML rendering dynamically adapting to the backend JSON layouts, complete with debounced zero-allocation native input filtering!