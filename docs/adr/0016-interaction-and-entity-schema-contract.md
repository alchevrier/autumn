# ADR-0016: Interaction and Entity Schema Contract

## Status

Proposed

## Date

2026-06-19

## Context

ADR-0006 defines behavioral interaction conventions for Autumn workflows. As the project matured, structural schema rules were added directly into ADR-0006, including entity metadata shape, payload schema declarations, and OpenAPI alignment. That conflates two concerns:

- interaction behavior semantics (workflow meaning)
- schema contract semantics (document shape and validation)

To keep ADR boundaries clean and maintainable, structural schema rules must be separated into a dedicated ADR and referenced by convention ADRs.

## Decision

Autumn defines the structural schema contract in a dedicated ADR and keeps behavior semantics separate.

- ADR-0006 remains the source of truth for interaction behavior semantics only.
- This ADR (0016) is the source of truth for structural schema rules, including:
  - manifest shape
  - entity metadata contract (`entityDefinition`)
  - entity payload schema content contract (`entitySchemas`)
  - interaction field constraints
  - OpenAPI alignment
- The normative machine-readable schema artifact is `docs/schema/autumn-interactions.schema.json`.

## Illustrative Examples

Manifest excerpt with payload schemas and entity definitions:

```json
{
  "manifest": {
    "version": "2026.06.19-001",
    "entitySource": "bff",
    "countryResolverPolicy": "ip-then-payment-override",
    "variantPolicy": {
      "enabled": true,
      "source": "bff"
    },
    "cache": {
      "configTtlSeconds": 3600,
      "defaultReadTtlSeconds": 300
    },
    "buckets": {
      "image": {
        "fr": "https://cdn.example.fr/images/"
      }
    },
    "resources": {
      "hero-banner": {
        "type": "image",
        "path": "banners/hero.webp",
        "action": "screen.home"
      }
    },
    "entitySchemas": {
      "profile.v1": {
        "type": "object",
        "required": ["id", "email", "displayName"],
        "properties": {
          "id": { "type": "string" },
          "email": { "type": "string" },
          "displayName": { "type": "string" }
        }
      },
      "order-status.v1": {
        "type": "object",
        "required": ["orderId", "status", "updatedAt"],
        "properties": {
          "orderId": { "type": "string" },
          "status": { "type": "string" },
          "updatedAt": { "type": "string" }
        }
      }
    },
    "entities": {
      "profile.current": {
        "id": "profile.current",
        "kind": "document",
        "schema": "profile.v1",
        "createAction": "profile.create",
        "readAction": "profile.read",
        "updateAction": "profile.update"
      },
      "order.status.current": {
        "id": "order.status.current",
        "kind": "document",
        "schema": "order-status.v1",
        "readAction": "order.status"
      }
    },
    "entryStateId": "home.ready",
    "stateIndex": ["home.ready", "orders.status"]
  }
}
```

State interaction excerpt showing required structural fields:

```json
{
  "states": {
    "home.ready": {
      "state": "ready",
      "screen": "home",
      "interactions": {
        "read": {
          "action": "profile.read",
          "entityId": "profile.current",
          "cache": {
            "enabled": true,
            "ttlSeconds": 300,
            "strategy": "stale-then-update"
          }
        }
      }
    },
    "orders.status": {
      "state": "ready",
      "screen": "order-status",
      "interactions": {
        "poll": {
          "action": "order.status",
          "entityId": "order.status.current",
          "intervalMs": 5000,
          "stopWhen": "completed"
        }
      }
    }
  }
}
```

OpenAPI 3.1 reference example:

```yaml
openapi: 3.1.0
components:
  schemas:
    AutumnInteractionManifest:
      $ref: ../schema/autumn-interactions.schema.json
```

Normative schema definition excerpts:

Manifest contract excerpt:

```json
{
  "$defs": {
    "manifest": {
      "type": "object",
      "required": [
        "version",
        "entitySource",
        "countryResolverPolicy",
        "variantPolicy",
        "cache",
        "buckets",
        "resources",
        "entitySchemas",
        "entities",
        "entryStateId",
        "stateIndex"
      ],
      "properties": {
        "entitySchemas": { "$ref": "#/$defs/entitySchemas" },
        "entities": { "$ref": "#/$defs/entities" }
      }
    }
  }
}
```

EntityDefinition and entity payload schema contract excerpt:

```json
{
  "$defs": {
    "entitySchemas": {
      "type": "object",
      "additionalProperties": {
        "$ref": "#/$defs/entityPayloadSchema"
      }
    },
    "entityPayloadSchema": {
      "type": "object",
      "required": ["type"],
      "properties": {
        "type": {
          "type": "string",
          "enum": ["object", "array", "string", "number", "integer", "boolean"]
        },
        "properties": { "type": "object" },
        "required": {
          "type": "array",
          "items": { "type": "string" }
        },
        "items": { "type": "object" },
        "enum": { "type": "array" }
      }
    },
    "entityDefinition": {
      "type": "object",
      "required": ["id", "kind", "schema", "readAction"],
      "properties": {
        "id": { "type": "string" },
        "kind": {
          "type": "string",
          "enum": ["document", "collection"]
        },
        "schema": { "type": "string" },
        "readAction": { "type": "string" },
        "createAction": { "type": "string" },
        "updateAction": { "type": "string" },
        "deleteAction": { "type": "string" }
      }
    }
  }
}
```

Interaction schema constraint excerpt:

```json
{
  "$defs": {
    "createInteraction": {
      "type": "object",
      "required": ["action", "entityId"]
    },
    "updateInteraction": {
      "type": "object",
      "required": ["action", "entityId"]
    },
    "readInteraction": {
      "type": "object",
      "required": ["action", "entityId", "cache"]
    },
    "pollInteraction": {
      "type": "object",
      "required": ["action", "entityId", "intervalMs"]
    }
  }
}
```

## Structural Contract

### 1. Root document

The root document contains:

- `manifest` (required)
- `states` (required)

### 2. Manifest

The manifest contains:

- required: `version`, `entitySource`, `countryResolverPolicy`, `variantPolicy`, `cache`, `buckets`, `resources`, `entitySchemas`, `entities`, `entryStateId`, `stateIndex`

### 3. Entity definition contract (`entityDefinition`)

Each entity under `manifest.entities` must define:

- required: `id`, `kind`, `schema`, `readAction`
- optional: `createAction`, `updateAction`, `deleteAction`
- `kind` enum: `document`, `collection`
- `schema` must reference an entry key from `manifest.entitySchemas`
- when represented as object map entries, `entity.id` must equal its containing map key

### 4. Entity payload schema content contract (`entitySchemas`)

`manifest.entitySchemas` is required and maps schema IDs (for example `profile.v1`) to JSON Schema fragments that describe payload content.

- each schema entry must declare at least `type`
- payload schemas may use common JSON Schema keywords such as `properties`, `required`, `items`, and `enum`
- this contract defines payload shape ownership in configuration, not in hardcoded platform models

### 5. Interaction field constraints

Interaction objects are closed (`additionalProperties: false`).

- `create`: `action`, `entityId` required; `payload` optional
- `update`: `action`, `entityId` required; `targetId`, `payload` optional
- `read`: `action`, `entityId`, `cache` required; `pagination` optional
- `poll`: `action`, `entityId`, `intervalMs` required; `stopWhen`, `pagination` optional
- `next`: `to` required
- `done`: `to` required
- `redirect`: `to`, `type` required

### 6. Cross-reference validation rules

These rules are required semantically and must be enforced by tooling (schema validator extension and/or compiler plugin):

- every interaction `entityId` must exist in `manifest.entities`
- every `entityDefinition.schema` value must exist in `manifest.entitySchemas`
- every `entity.id` must equal the enclosing `manifest.entities` key

## OpenAPI Alignment

OpenAPI 3.1 uses JSON Schema 2020-12, so `docs/schema/autumn-interactions.schema.json` can be referenced directly via `$ref` in `components.schemas`.

Mapping summary:

- `manifest.entitySchemas.*`: JSON Schema fragment object
- `manifest.entities.*`: strict entity metadata object with required fields and enum constraints
- interaction objects: strict typed objects with explicit required fields and closed additional properties

## Consequences

- ADR boundaries become clearer: behavior in ADR-0006, structure in ADR-0016.
- The schema contract is easier to evolve without polluting interaction semantics.
- Tooling implementation scope is explicit for validator and compiler plugin work.
- OpenAPI integration is centralized in one ADR, reducing ambiguity for backend teams.
