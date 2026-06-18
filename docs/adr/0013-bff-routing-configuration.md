# ADR-0013: Backend-for-Frontend Routing Configuration

## Status

Proposed

## Date

2026-06-17

## Context

Autumn's backend-for-frontend is responsible for slicing configuration, evaluating country and platform conditions, validating API keys, and returning reduced documents to the client. To do that it must forward certain requests to upstream backend services — for example a payment service, an account service, or a content delivery service. Without a declared routing layer, the BFF would need to hardcode upstream addresses or duplicate service discovery logic, coupling the BFF implementation to each application's infrastructure. The upstream services themselves are application-specific and outside Autumn's scope, so the routing configuration must be left to the application deploying the BFF.

## Decision

Autumn's BFF uses a declarative routing configuration that maps action identifiers and document types to upstream service endpoints. The routing configuration is application-supplied and loaded alongside the main Autumn configuration.

- The routing configuration is a JSON document, consistent with the format established in ADR-0002. It declares a set of named routes, each mapping an action identifier or document type pattern to an upstream base URL and, optionally, path and header overrides.
- Entity delivery mode is configuration-driven. Default mode uses BFF routing as defined in this ADR. An optional non-BFF mode is planned for applications that want to keep entity storage/access in their own stack (for example Firebase plus local database). In that mode, configuration declares `entitySource = external`, and Autumn resolves entities through application-provided interfaces instead of BFF routes.
- The granularity of routing is left entirely to the application. At one extreme, every action identifier can map to the same base URL — a single load balancer or API gateway that handles all upstream traffic. At the other extreme, each resource or action identifier can map to a distinct URL — a separate load balancer, microservice, or CDN origin per resource. Both configurations are valid and expressed in the same schema. A typical deployment starts with a single upstream URL and adds per-resource routes only when a specific resource needs independent scaling, a different region, or a distinct auth mechanism.
- Action identifiers declared in resource and workflow documents (ADR-0002, ADR-0006) resolve to routes in this table at BFF dispatch time. The client never sees upstream URLs; it sees only action identifiers.
- Before dispatching any route — forwarding or custom handler — the BFF enforces two access checks in order. First, the presented API key is validated against the key management backend (ADR-0007); an invalid or revoked key causes an immediate rejection before any upstream call is made. Second, the authenticated user's access is evaluated against the resource or action being requested; a user without access to the referenced bucket content or action receives a rejection consistent with ADR-0001's rule that state must never reference content the user is not authorised to access. These checks are performed by the BFF before the route handler is invoked, so neither forwarding routes nor custom handler delegates need to re-implement them.
- The routing configuration is loaded at BFF startup and cached for the lifetime of the process. Route entries may be updated by reloading the configuration without redeploying the BFF binary.
- Authentication requirements for upstream calls — such as service-to-service tokens, mTLS, or header injection — are declared per route in the routing configuration. The BFF injects the declared credentials before forwarding; the upstream service does not need to re-validate the client-facing API key.
- The routing configuration may declare a default upstream for unmatched action identifiers, or may fail closed and return an error for any action identifier without an explicit route.
- The application deploying the BFF is fully responsible for the routing configuration content. Autumn defines the schema and the resolution mechanism; it does not prescribe which upstream services exist or how they are addressed.
- Country-aware routing is supported: a route entry may declare per-country upstream URLs, following the same per-country declaration pattern used for bucket sources in ADR-0002. This allows the BFF to forward requests to country-specific backend infrastructure without the client being aware of the routing decision.
- Some BFF deployments require logic beyond simple URL forwarding — for example aggregating multiple downstream calls into a single response, transforming a response before returning it, or enforcing business rules that cannot be expressed as a header override. For these cases, a route entry may declare a **custom handler** instead of an upstream URL. A custom handler is a delegate interface with a single method that receives the incoming request context and returns a response document. The application implements the delegate and registers it against the route identifier at BFF startup. Autumn invokes the delegate at dispatch time instead of forwarding to an upstream URL. The delegate has access to the same downstream API call helpers that Autumn uses internally, so it can make typed calls to any number of downstream services and compose their responses into a single document. Simple forwarding routes and custom handler routes coexist in the same routing configuration; the distinction is a field in the route entry, not a separate configuration file.
- For planned non-BFF mode, Autumn exposes provider interfaces (for example `EntityReader`, `EntityWriter`, and `AccessPolicyEvaluator`) that the application implements. Autumn then consumes those interfaces to build state documents while leaving persistence and transport choices (Firebase, local DB, proprietary API) to the application.

## Consequences

- The BFF is decoupled from any specific upstream service topology. Changing an upstream URL, adding a new service, or splitting a service requires only a routing configuration change, not a BFF redeployment.
- The routing model scales from the simplest possible deployment — one URL for everything — to a fully decomposed topology where each resource routes to a dedicated load balancer, without any change to client documents or BFF code. The application operator chooses the level of granularity that matches their infrastructure maturity.
- Action identifiers remain the only coupling between client documents and backend infrastructure. The client, the BFF, and the upstream services each know only what they need to know.
- Country-aware upstream routing allows the BFF to enforce data residency and latency optimisation at the forwarding layer without any client involvement.
- Service-to-service authentication is declared in configuration and injected by the BFF, keeping upstream services free from client-facing key validation concerns.
- Custom handler delegates give the application full control over complex BFF logic — fan-out to multiple downstreams, response composition, conditional routing — without forking the BFF or embedding application logic into the Autumn framework itself. The boundary is clean: Autumn owns dispatch and document reduction; the delegate owns any logic specific to the application.
- API key validation and user access enforcement are applied uniformly at the BFF dispatch layer before any route is invoked. Neither forwarding routes nor custom handler delegates can be reached by an unauthenticated or unauthorised caller. This makes the BFF the single enforcement point for both concerns, regardless of how many upstream services or custom handlers are registered.
- Teams that do not want BFF-managed entities can still adopt Autumn's UI/state model through the planned external-entity mode. In that mode, the application owns persistence and transport while Autumn consumes provider interfaces to produce state.
- External-entity mode is a roadmap capability, not implemented in the current ADR scope. Current implementations should treat BFF routing as the supported entity delivery path.
- The routing configuration becomes a sensitive operational artifact — it contains upstream URLs and service credential references. It must be protected with the same access controls as other backend secrets.
- Misconfigured routes produce errors at dispatch time. Monitoring of BFF routing errors is necessary to detect misconfiguration before it affects users.
- Future ADRs or module APIs may refine the route schema, the credential injection mechanism, and the interaction between routing and the experiment variant resolution defined in ADR-0004.
