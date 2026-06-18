# ADR-0002: Bucket Source Decoupling via Configuration

## Status

Proposed

## Date

2026-06-17

## Context

Autumn needs to support multiple storage providers and country-specific deployment choices without forcing application code to change whenever infrastructure changes. Storage choices may also vary to satisfy regional compliance requirements such as data residency. Different asset types — such as images, videos, documents, or fonts — may be served from different storage providers optimised for that type, and those choices may again differ per country. Page documents also need to reference resources and trigger actions without knowing the physical location of either, so the same document can be rendered correctly regardless of the country or infrastructure the device is assigned to.

## Decision

Bucket sources and resources are declared in the service configuration file rather than hardcoded in documents or application code.

- Supported providers can include AWS S3, Google Cloud Storage, Azure Blob Storage, Firebase Storage, and similar services.
- Buckets are declared per asset type and per country. For example, images, videos, and documents may each resolve to a different bucket, and each of those buckets may resolve to a different provider or region depending on the country.
- Configuration is defined per country so bucket sources can vary by jurisdiction.
- Resources are declared in the configuration as named entries. Each entry carries a country-aware asset URI pointing to the physical content and an action URI describing what should happen when the resource is interacted with. The URI is derived from the bucket assigned to the resource's asset type and country, so routing is automatic.
- Page documents declare only the resource identifier and, where needed, the corresponding action identifier. They contain no physical URIs and no knowledge of asset type routing or country-specific storage.
- At delivery time, the backend-for-frontend or the client resolves the resource identifier to the correct bucket for that asset type and country, then to the final URI and action URI from the active configuration.
- The app and state layers work only with resource identifiers and resolved references, never with hardcoded URIs.
- The actual bucket source and action target are resolved from configuration at runtime.
- Configuration files and state documents use **JSON** as their wire and storage format. JSON is human-readable, universally supported across iOS, Android, and Web, and straightforward to diff, version, and delta-patch. A resource declaration in the configuration looks like:

```json
{
  "buckets": {
    "image": {
      "fr": "https://cdn.example.fr/images/",
      "de": "https://cdn.example.de/images/"
    },
    "video": {
      "fr": "https://video.example.fr/",
      "de": "https://video.example.de/"
    }
  },
  "resources": {
    "hero-banner": {
      "type": "image",
      "path": "banners/hero.webp",
      "action": "screen.home"
    }
  }
}
```

  A page document then references only the identifier:

```json
{
  "assets": [
    { "id": "hero-banner" }
  ]
}
```

## Consequences

- Storage providers and action endpoints can be swapped without changing page documents or application code.
- Asset-type-specific storage decisions — such as using a video CDN for video and an object store for documents — are configuration concerns, invisible to page documents.
- Country-specific infrastructure and action routing can be aligned with compliance requirements, including data residency rules that may differ by asset type.
- Page documents stay small and stable: they describe structure and intent, not infrastructure.
- Adding a new country, a new asset type bucket, or migrating a resource to a different provider requires only a configuration change.
- New screens can be introduced purely through configuration and new state documents without a new app release, provided they compose resource identifiers and action identifiers the app already knows how to resolve.
- Configuration becomes a critical dependency for resource and action resolution.
- Runtime validation is needed to ensure resource identifiers map to a configured bucket for the correct asset type and country.
