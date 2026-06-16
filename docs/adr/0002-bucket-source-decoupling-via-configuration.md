# ADR-0002: Bucket Source Decoupling via Configuration

## Status

Proposed

## Context

Autumn needs to support multiple storage providers and country-specific deployment choices without forcing application code to change whenever infrastructure changes. Storage choices may also vary to satisfy regional compliance requirements such as data residency.

## Decision

Bucket sources are declared in the service configuration file rather than hardcoded in the application.

- Supported providers can include AWS S3, Google Cloud Storage, Azure Blob Storage, Firebase Storage, and similar services.
- Configuration is defined per country so bucket sources can vary by jurisdiction.
- The app and state layers work only with references.
- The actual bucket source is resolved from configuration at runtime.

## Consequences

- Storage providers can be swapped without changing app code.
- Country-specific infrastructure can be aligned with compliance requirements.
- Configuration becomes a critical dependency for bucket resolution.
- Runtime validation is needed to ensure references map to configured sources.
