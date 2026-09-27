# PoE Companion — Technical Design V2.1.1

**Status:** Canonical technology-neutral architecture **Purpose:** Define system shape, module boundaries, data
ownership, consistency rules and integration patterns without binding them to a concrete programming
language/framework/database product.

## 1. Architectural Goals

Optimize for:

```text
correct domain modelling
low operational complexity
one-developer iteration
strong provider isolation
transactional correctness
explainability/debuggability
easy early deployment
future evolvability without premature distribution
```

Do not optimize initially for:

```text
massive scale
microservice autonomy
multi-region availability
high-throughput distributed processing
independent team deployment
```

## 2. System Shape

Preferred initial shape: **modular monolith**.

```text
Browser
  ↓
Application
  ├── Web / Product layer
  ├── Domain-oriented application modules
  ├── Persistence
  ├── Provider adapters
  └── Administrative/background operations
  ↓
Relational Database
```

External systems connect only through explicit adapters.

## 3. Deployment Principle

Start with:

```text
1 repository
1 application deployable
1 relational database
```

Module boundaries are code/domain boundaries, not network boundaries.

Future service extraction remains possible when a module develops a concrete reason for independent deployment.

## 4. High-Level Modules

Canonical functional modules:

```text
Catalog
League
Account
Build
Acquisition
Goal
```

Supporting concerns:

```text
Security
Administration
Shared technical infrastructure
```

There is no global top-level `Import` domain module.

Provider adapters normally live with the domain capability they feed:

```text
Catalog → PoE Wiki / RePoE adapters
Account → GGG account adapters
Build → PoB adapter
League → GGG league adapter
```

Shared HTTP/client primitives may live in infrastructure.

## 5. Module Ownership

### Catalog

Owns:

```text
UniqueDefinition
ExternalIdentity
ItemAvailability
CatalogRevision
catalog reconciliation/corrections
```

Does not own player ownership, Build requirements or drop probability.

### League

Owns/reference-manages:

```text
CompatibilityVersion
GamePatch
EvaluationContext
LeagueDefinition
AccountContext identity/configuration
Ruleset support
```

### Account

Owns personalized ownership state:

```text
OwnershipMode
ManualOwnership
CurrentOwnership
future PoEConnection
future SyncRun
future AccountSnapshot / OwnedItem
```

### Build

Owns:

```text
BuildArchetype
BuildVariant
BuildVariantRevision
BuildReference
RequirementGroup
Requirement
UniqueReadiness evaluation
```

### Acquisition

Owns:

```text
AcquisitionSource
DropRelationship
AttemptDefinition
DropEstimate
DropRateOverride
AvailableAttempts
Probability calculation
```

### Goal

Owns:

```text
Goal
GoalTarget
GoalTemplate
```

Goal does not duplicate foreign module data.

## 6. Module Dependency Direction

Initial conceptual dependencies should stay simple and acyclic.

```text
Catalog      ← referenced by Build, Acquisition, Account normalization
League       ← referenced by Account, Build version selection, Acquisition versioning, Goal context
Account      → exposes CurrentOwnership to Build/Application reads
Build        → references Catalog identities; consumes CurrentOwnership through Account API
Acquisition  → references Catalog identities and League/version concepts
Goal         → references public identities from Catalog/Build/Acquisition and AccountContext
Web/Application orchestration → composes module APIs/read models
```

No module may reach directly into another module's persistence internals.

Exact allowed dependencies should be enforced once code exists, not via speculative interfaces for every possible future
interaction.

## 6A. EvaluationContext Boundary

Mutable game/build/acquisition knowledge is always evaluated inside an explicit EvaluationContext rather than against an
implicit global current version.

Conceptually:

```text
CompatibilityVersion
Ruleset
optional GamePatch
optional LeagueDefinition when genuinely league-specific
```

AccountContext owns personalized isolation; EvaluationContext owns the knowledge applicability used for a particular
evaluation. Long-lived Standard contexts can therefore be evaluated under different CompatibilityVersions over time, and
historical views can retain/use their historical context.

Applicability matching is strict. A knowledge record that omits a dimension is broad on that dimension. If it specifies
CompatibilityVersion/GamePatch/Ruleset/League constraints, the EvaluationContext must contain sufficient precision and
satisfy them. Unknown/missing precision yields not-applicable/unknown rather than a permissive implicit match.

---

## 7. CurrentOwnership Boundary

Downstream logic consumes one conceptual interface/read model:

```text
CurrentOwnership
UniqueDefinitionId → transferable quantity
```

`CurrentOwnership` includes only account-owned gear that remains transferable and available to satisfy player Build
Readiness requirements.

V0.1:

```text
ManualOwnership → CurrentOwnership
```

Future:

```text
Active AccountSnapshot → aggregate OwnedItems → CurrentOwnership
```

Build code does not know which source produced ownership.

## 8. Ownership Authority Rule

An AccountContext has one authoritative ownership mode at a time.

```text
MANUAL
OR
SYNCHRONIZED
```

No generic merge-by-addition.

If future correction/override behaviour is needed, define it explicitly as its own workflow/model.

## 9. Build Evaluation Boundary

Conceptually:

```text
BuildVariantRevision
+
CurrentOwnership
→ UniqueReadinessEvaluator
→ readiness + fulfilled/missing groups + upgrade completeness
```

Evaluation is deterministic and side-effect free.

### Cross-build semantics

No allocation across builds. Every BuildVariantRevision sees the full CurrentOwnership. For each
`(BuildVariantId, CompatibilityVersion)`, at most one revision is active/eligible for matching.

### V0.1 within-build semantics

RequirementGroups are independent predicates. No allocation solver.

The architecture must not expose this V0.1 implementation shortcut as a permanent guarantee that richer requirement
expressions can never exist.

## 10. Future Rich Requirement Boundary

The public Build module should conceptually ask:

> Evaluate this BuildVariantRevision against CurrentOwnership.

It should not expose callers to a hard-coded assumption that every future requirement is a flat independent group.

This allows later support for combinatorial/nested/allocation-aware requirements without rewriting consumers.

## 11. Acquisition and Probability Boundary

Probability calculation consumes a supported probability model rather than assuming every DropEstimate is one scalar
forever.

V0.1 model:

```text
FIXED_BERNOULLI
```

Future model families may be condition-dependent.

A `DropRelationship` can exist with no calculable DropEstimate. The relationship itself has explicit
EvaluationContext/applicability semantics because source→item availability can change independently of probability.

The UI/application layer must be able to represent:

```text
known source + unknown probability
known source + conditional but unsupported probability
known source + supported fixed probability
```

## 12. Probability Model Contract

For any supported calculation, input semantics include:

```text
DropRelationship
attempt definition
applicable conditions/model parameters
selected canonical/personal estimate
number of attempts
```

A bare number such as `0.10` is not a complete domain contract without the context that defines what one trial means.

## 13. Provider Adapter Rule

Mandatory flow:

```text
External Provider
→ provider-specific DTO/raw representation
→ adapter
→ normalized internal candidate/value
→ domain/application validation
→ internal state
```

Never:

```text
external JSON/DTO
→ reused throughout domain/UI/persistence
```

Provider types do not cross the adapter boundary.

## 14. Catalog Ingestion and Identity Reconciliation

Catalog import is a first-class application operation, not a live query path.

Conceptual flow:

```text
fetch provider data
→ create import candidate set
→ match known ExternalIdentity mappings
→ apply explicit provider matching rules
→ detect ambiguity/conflict
→ validate complete candidate revision
→ persist
→ atomically activate
```

Any unresolved identity conflict invalidates the complete candidate CatalogRevision in V0.1; the importer never invents
a merge and does not partially activate unaffected records.

Completeness validation is also mandatory before activation. The importer must be able to prove that all required
pages/batches for its configured scope completed successfully and should reject implausible/unexplained truncation
rather than treating a partial provider observation as a complete catalog.

Absence is not lifecycle evidence: a previously known provider record disappearing from a later observation does not
automatically delete a stable UniqueDefinition or mark it RETIRED/DROP_DISABLED. Those changes require positive evidence
or explicit curator action.

The previous active revision stays usable on failure.

## 15. Manual Corrections

Imported knowledge may be incomplete or wrong.

Developer-owned corrections are persisted as an app-owned overlay with provenance rather than mutating upstream source
data. Every later candidate import re-applies active corrections after normalization/identity reconciliation and before
final validation. Provider refreshes never silently overwrite them.

Exact storage shape can remain lightweight in V0.1, but persistence/reapplication semantics are fixed.

## 16. Future GGG Sync Flow

Conceptual future flow:

```text
user triggers sync
→ fetch all required stashes
→ fetch all required characters
→ enumerate all supported observable item containers
→ classify each container as contributing/non-contributing to transferable CurrentOwnership
→ normalize contributing items
→ validate completeness
→ build candidate AccountSnapshot
→ persist candidate
→ atomically activate
→ derive CurrentOwnership
```

Partial results never replace current state. Provider-visible Animate Guardian equipment is explicitly non-contributing
to CurrentOwnership; stash/inventory/equipment/rucksack/passive-tree jewels contribute when successfully observed.

## 17. GGG Rate-Limit and Failure Rule

GGG request pacing must follow server-provided rate-limit state/Retry-After rather than fixed architectural RPS
assumptions.

Failures leave the last successful local state available.

## 18. Persistence Boundary

Persistent storage is needed for:

```text
Catalog/identity mappings/revisions
League/version/context data
Manual ownership
Build definitions and revisions
Acquisition relationships/estimates
Personal overrides/attempts
Goals/templates
application configuration
```

Future:

```text
OAuth connection metadata/tokens
SyncRuns
AccountSnapshots/OwnedItems
```

## 19. Relational Data Style

The core domain is relational.

Prefer explicit relationships/constraints for core semantics.

Avoid generic `entity_type/entity_id/json_blob` patterns for central relationships unless a real requirement justifies
them.

Semi-structured storage is acceptable for provider raw payloads, diagnostics and uninterpreted future metadata.

Core product logic must not depend on opaque blobs.

## 20. Canonical vs. Personal Persistence

Keep semantic separation between:

Canonical/curated:

```text
catalog
Build taxonomy/requirements
Acquisition relationships
canonical DropEstimates
```

Personal:

```text
ownership
snapshots
AvailableAttempts
DropRateOverrides
Goals
GoalTemplates (post-MVP)
```

Single-user deployment does not remove this distinction.

## 21. Internal IDs

Core entities use application-owned opaque IDs.

Provider IDs are stored as external mappings.

Changing providers must not require changing internal identity.

## 22. Current-State Activation

Where history/revisions exist, explicitly identify active state rather than treating newest row as implicitly active.

Examples:

```text
active CatalogRevision
active AccountSnapshot
0..1 active/eligible BuildVariantRevision per (BuildVariant, CompatibilityVersion)
0..1 active canonical DropEstimate per exact probability applicability/model key
0..1 active personal DropRateOverride per exact probability applicability/model key
```

Activation of important revisions/snapshots is transactional. Activated BuildVariantRevision readiness semantics are
immutable; semantic edits are performed by creating and atomically activating a superseding revision. Historical active
revisions remain historical facts rather than mutable editor state.

## 23. Command vs. Query Separation

Conceptually distinguish state-changing operations from reads without introducing a CQRS framework.

V0.1 command examples:

```text
SetOwnedQuantity
Create/ReviseBuildVariantRevision
SetAvailableAttempts
SetDropEstimate
SetDropRateOverride
Create/CompleteGoal
ActivateCatalogRevision
```

V0.1 query examples:

```text
GetUniqueDetail
ListBuildMatches
GetBuildDetail
GetAcquisitionDetail
CalculateSupportedProbability
```

Detailed command DTOs are specified when implemented, not globally in advance.

## 24. Transactions

Important multi-record operations are atomic.

Examples:

```text
create BuildVariantRevision + groups + requirements
activate CatalogRevision
future activate AccountSnapshot
```

Controllers/UI actions do not coordinate persistence across repositories themselves.

## 25. Read Models

Cross-module screens may use dedicated application/read models.

Example Build Browser read model combines:

```text
BuildVariantRevision
CurrentOwnership
UniqueReadiness
UpgradeCompleteness
```

A read model is not automatically a new domain entity.

## 26. Background Work

V0.1 requires little asynchronous infrastructure.

Potential longer operations:

```text
catalog refresh
future account sync
```

They may execute synchronously initially if acceptable.

No message broker/distributed worker is justified initially.

If progress is needed, an in-process job abstraction is enough until proven otherwise.

## 27. Frontend Responsibility

Frontend/presentation handles:

```text
navigation
forms
display/filter controls
progress feedback
validation presentation
```

It does not implement critical domain rules such as readiness or probability semantics independently.

## 28. UI Contract Depth

Before implementing a screen, specify:

```text
information shown
actions
states/empty states
validation/failure behaviour
navigation relationships
```

Pixel-perfect visual dimensions and a full design system are not architecture prerequisites.

## 29. Security Boundary

Conceptual deployment:

```text
Internet
→ HTTPS
→ Application Access Control
→ Application
→ Database / Secrets / External APIs
```

Database should not be publicly exposed unless operationally unavoidable.

Secrets live outside source control.

Future GGG OAuth tokens remain server-side.

## 30. Single-User Security

V0.1 does not need a user-management domain.

It still requires protected application access and secure session/cookie/CSRF handling appropriate to the selected
stack.

## 31. Observability and Debuggability

At minimum make it possible to answer:

```text
Why is this Build BLOCKED?
Why did this item fail identity reconciliation?
Which CatalogRevision is active?
Which DropEstimate/model is selected?
Why is probability unsupported?
Why did a future sync fail?
```

Structured logs should cover imports, normalization failures, external requests and security-relevant failures without
leaking secrets.

## 32. Search and Cache

V0.1 search is simple database-backed text/filter search.

No Elasticsearch/vector system.

No distributed cache.

Introduce cache/search infrastructure only after a measured problem exists.

## 33. Testing Strategy

### Pure domain tests

Heavy coverage for:

```text
UniqueReadiness
ALL/ANY/quantity predicate semantics
probability edge cases
version/status logic
```

### Database integration tests

Use the real target relational database engine through isolated test infrastructure.

Test constraints, migrations, repository behaviour and important transactions.

### Provider adapter tests

Use recorded/simulated HTTP responses to test:

```text
normalization
pagination
schema variation
error handling
identity conflicts
rate-limit behaviour where practical
```

### Module architecture tests

Verify intended module boundaries and no cycles/internal leakage.

### Few full HTTP tests

Cover central end-to-end journeys rather than every branch.

## 33A. Provider Compliance Boundary

Provider adapters must also respect provider-specific access and attribution requirements. Before any public/broad
deployment, reverify current terms rather than relying on this design snapshot.

Known current examples include:

```text
GGG API client → identifiable User-Agent / API-policy compliance
public/broad PoE tool → current required non-endorsement/affiliation notice
PoE Wiki content/data → current license/attribution obligations reviewed before redistribution/public use
```

These are deployment/provider compliance concerns, not reasons to expand V0.1 architecture.

---

## 34. Vertical Slice 1

Must prove:

```text
Persistence
→ Manual ownership
→ Build requirements
→ UniqueReadiness
→ Missing Unique
→ AcquisitionSource
→ fixed DropEstimate
→ AvailableAttempts
→ Probability
→ server-rendered user flow
```

No external provider integration required.

## 35. Vertical Slice 2

Real PoE Wiki catalog import with:

```text
provider isolation
identity mapping/reconciliation
candidate validation
activation
```

## 36. Vertical Slice 3

Real curated Build/Acquisition data and enough curator UI to make the application useful without direct DB editing.

## 37. Early Deployment

Deploy after Vertical Slice 1 plus:

```text
protected access
migrations
persistent DB
backup approach
container image
basic logs/health
```

This validates operations early without coupling product progress to GGG OAuth.

## 38. Architecture Non-Goals

```text
microservices
message broker
service mesh
Kubernetes
reactive data stack
separate SPA requirement
distributed cache
search cluster
generic workflow engine
generic rule engine for all future PoE mechanics
```

## 39. Architecture Success Criterion

The architecture is successful if the product can evolve from manual V0.1 state to richer requirements, conditional drop
models and GGG synchronization **without changing the meaning of CurrentOwnership, UniqueReadiness, canonical identity
or player intent**.
