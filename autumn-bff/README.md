# autumn-bff

Backend-for-Frontend (BFF) responsible for securely terminating API requests, validating API keys, resolving geography boundaries, and evaluating A/B experiments before delivering compressed state arrays to the device.

## Core Responsibilities

1. **API Key Lifecycle (ADR-0007)**: Drops untrusted connections instantly. Manages scope and key rotation to ensure compromised keys can be revoked without pushing a client update.
2. **Country Resolution (ADR-0005)**: Looks up the physical IP (e.g., via `X-Forwarded-For` or `CF-IPCountry`) to inject the localized bucket pointer index.
3. **Cohort Hash Provider**: Identifies A/B test variations per device ID and strips unneeded configurations from the JSON payload to preserve device network bounds.
4. **Zero-Allocation Ready**: Produces the pre-optimized, heavily flat JSON layout that `autumn-config` requires, ensuring the mobile/web clients don't do any post-processing.
