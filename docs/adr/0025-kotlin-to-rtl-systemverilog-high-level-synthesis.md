# ADR 0025: Kotlin-to-RTL (SystemVerilog) High-Level Synthesis

## Status
Proposed

## Context
Autumn's theoretical foundation relies heavily on "circuit-based programming" and hardware-sympathetic constructs. We have successfully hijacked the Kotlin K2 compiler to act as a pseudo-Hardware Description Language (HDL), enforcing cycle bounds (`@CycleBudget`), memory structures (`AutumnMemoryBank`), and static topologies (`@BoundaryChannel` into unrolled FSMs). 

Currently, our backend targets are JVM Bytecode and LLVM `linuxX64` binaries, achieving a phenomenally low ~37ns pipeline handoff. However, in the highest echelons of High-Frequency Trading (HFT) and aerospace compute, latency must be driven below 1 microsecond tick-to-trade, necessitating Field Programmable Gate Arrays (FPGAs) or ASICs.

Historically, HFT teams must manually translate their software strategies into Verilog, SystemVerilog, or VHDL, or rely on C++-based High-Level Synthesis (HLS) tools like Xilinx Vitis HLS, which suffer from archaic Developer Experience (DX). 

Since Autumn already parses idiomatic Kotlin into a mathematically-bounded, pointer-free, spatially-aware Intermediate Representation (IR), we possess the exact structural graph required to generate raw hardware.

## Decision
We will extend the Autumn K2 compiler pipeline to include an **RTL Synthesis phase**. It will translate the validated Autumn IR directly into synthesizable **SystemVerilog**.

The exact structural mapping will be:
1. **`AutumnChannel` / `@BoundaryChannel`** -> translates to an **AXI4-Stream interface** (or lightweight FIFO queues).
2. **`AutumnMemoryBank` (SoA)** -> translates to physically instantiated **Block RAM (BRAM)** or UltraRAM macros natively.
3. **`@Observe` Handlers** -> translates to clocked **Finite State Machines (FSM)** using `always_ff @(posedge clk)` blocks.
4. **`@CycleBudget` Limits** -> translates directly into XDC/SDC **Timing Constraints** for physical circuit synthesis.

## Rationale
- **The Ultimate Write-Once, Deploy-Anywhere:** Developers can write a trading strategy or a network packet parser in standard, ergonomic Kotlin. They can run unit tests on the JVM, deploy the low-latency software version via LLVM/Native (37ns), and seamlessly deploy the exact same logic onto a Xilinx Alveo or AMD FPGA card by synthesizing the Autumn-generated SystemVerilog code.
- **Solving the HLS DX Crisis:** Traditional HLS (C++ to RTL) is incredibly painful. By bringing this translation to Kotlin, we provide modern IDE support, fast unit testing, strict type safety, null-safety, and real-time cycle-budgeting before hardware synthesis (which typically takes hours) begins.
- **Natural Architecture Fit:** Because Autumn explicitly forces a flat memory layout (no GC, no heap pointers) and statically mapped dataflows, the translation to physical hardware registers logic is 1-to-1.

## Consequences
- Requires building a new IR traversal pass (`SystemVerilogGenerator`) that outputs text blocks of SystemVerilog instead of lowering native LLVM bytecode.
- The framework must establish constraints on which Kotlin Stdlib math operations are valid for RTL emission (e.g., floating-point division is notoriously complex in standard RTL).
- Creates an entirely new ecosystem opportunity: "Hardware Design via Kotlin."
