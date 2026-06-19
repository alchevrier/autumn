# autumn-config

Configuration management for Autumn. This module handles bundled defaults, remote updates, versioning, and country-aware configuration resolution.

## Zero-Allocation Payload Resolution

Unlike traditional frameworks that use Jackson or Moshi to map JSON blobs into a massive tree of short-lived proxy objects, `autumn-config` operates purely on bytes—exactly like hardware DPDK packet parsers:

- `JsonConfigParser`: Iterates the raw OS socket byte array. When it finds relevant boundaries, it calculates the raw integer offset and limits without creating strings.
- `StringRegistry`: Caches those integer coordinates natively against a flat `.ByteArray`.
- `BudgetCalculator`: Provides deterministic size forecasting to avoid dynamic list allocations.
