# Autumn Certifier (`autumn-certifier`)

The Autumn Certifier is a rigorous, automated CI/CD validation toolkit for **Clock-Aware Programming**. Because Autumn applications compile down to mathematically isolated blocks of statically padded memory, this module formally proves that physical hardware bounds behave as mathematically predicted during runtime.

## Architecture & Flow

When you annotate your structures with `@CycleBudget` or `@ThreadCacheBudget`, the Autumn K2 Compiler calculates the pure theoretical limitations of the control flow graph during the initial Kotlin AST pass. It extracts this metadata and exports the telemetry into `topology.json`.

The `autumn-certifier` Gradle plugin reads this topology and runs native Linux `perf stat` hardware assertions against your binary. If your loop takes 2,000 physical instructions in reality but you budgeted 1,200, the build fails cryptographically.

```mermaid
flowchart TD
    A[Idiomatic Kotlin Code<br/>@CycleBudget(800)] --> B(K2 Compiler Plugin)
    
    subgraph Compile-Time
    B -->|Static Analysis| C[autumn-topology.json]
    B -->|LLVM Lowering| D[linuxX64 Executable]
    end
    
    subgraph Continuous Integration Validation
    C --> E(autumn-certifier Gradle hook)
    D -->|Deploy binary| F[perf stat hardware profiler]
    F -->|Actual Hardware Tick Metrics| E
    end
    
    E -->|Validation: Actual Cycles <= Theoretical Budget?| G{Threshold Validated?}
    G -->|Yes| H(Artifact Hardware Certified)
    G -->|No| I(Build Fails - Latency Breach)
    
    style H fill:#2ECC71,stroke:#000,stroke-width:2px,color:#fff
    style I fill:#E74C3C,stroke:#000,stroke-width:2px,color:#fff
```

By connecting standard Gradle CI/CD to physical processor instruction-count profiles (`perf stat -e instructions,cycles`), Autumn bridges the gap between software development and bare-metal high-frequency trading constraints.
