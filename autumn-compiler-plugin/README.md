# autumn-compiler-plugin

K2 Compiler Plugin for Autumn. This is the cornerstone of the **Circuit-Based Programming** architecture, acting as the strict verifier and hardware synthesizer for the JVM/Native targets.

## Static Constraint Verification

Unlike standard frameworks that discover Out-Of-Memory (OOM) errors and layout bugs at runtime, this plugin forces hardware-sympathetic limits directly into the Abstract Syntax Tree (AST):

- **Allocation Enforcement:** A custom K2 IR visitor scans all scopes marked mathematically as `@LongLived`. If it detects any heap-allocated objects being spawned (instead of array mutations), it aborts the build.
- **Budget Injection (`BudgetInjectionTransformer`):** It sweeps the IR tree for configurations like `@NetworkConcurrencyBudget` or `@InjectBudget` and evaluates them into static `IrConst` integer literals. Dynamic concurrency layouts literally disappear from the final bytecode, replaced by statically sized `IntArray` initializations matching strict `ADR-0015` capacities.
