# PoE SSF Companion — Specification Index V2.1.1

**Status:** Canonical — Frozen for implementation
**Date:** 2026-09-22
**Purpose:** Define the authoritative V2.1.1 specification set, document responsibilities, conflict resolution, terminology, and the decisions resolved during the canonization pass.

**V2.1.1 micro-release:** closes four final implementation ambiguities without changing product architecture or V0.1 scope: activated BuildVariantRevision semantic immutability, strict EvaluationContext applicability matching, catalog completeness/disappearance policy, and active probability-input uniqueness. It also records a non-blocking provider-compliance checkpoint for future public deployment.

After V2.1.1 the Canon is frozen for Vertical Slice 1. New information discovered during implementation should be fed back deliberately; it is not a reason to restart broad architecture planning.

---

## 1. Canonical V2.1.1 Document Set

The active specification consists of these documents:

| Document | Authority |
|---|---|
| `poe-companion-product-v2.1.1.md` | Product vision, philosophy, target user, long-term direction |
| `poe-companion-decision-v2.1.1.md` | Accepted product/domain decisions that cut across documents |
| `poe-companion-domain-model-v2.1.1.md` | Domain terminology, semantics, relationships, and invariants |
| `poe-companion-journeys-v2.1.1.md` | Observable user behaviour and end-to-end flows |
| `poe-companion-mvp-scope-freeze-v2.1.1.md` | Exact V0.1 scope and non-scope |
| `poe-companion-feasibility-spikes-v2.1.1.md` | External research and feasibility evidence; time-sensitive |
| `poe-companion-technical-design-v2.1.1.md` | Technology-neutral architecture, module boundaries, consistency and integration rules |
| `poe-companion-tech-stack-decision-v2.1.1.md` | Concrete implementation technology and engineering baseline |
| `poe-companion-bootstrap-impl-plan-v2.1.1.md` | Implementation order and first vertical slices |

The V1, V2 and V2.1 documents are historical context and are **superseded** where they conflict with this set.

---

## 2. Authority Rules

There is no single document that should duplicate every truth. The document closest to the question owns the answer.

### Scope question

`MVP Scope Freeze V2.1.1` wins.

Example:

> Is GGG account synchronization required for V0.1?

Answer comes from `MVP Scope Freeze V2.1.1`.

### Accepted product decision

`Product Decisions V2.1.1` wins.

Example:

> Does the product rank builds by strength?

Answer comes from `Product Decisions V2.1.1`.

### Domain meaning or invariant

`Domain Model V2.1.1` wins.

Example:

> What exactly does `UNIQUE_READY` mean?

Answer comes from `Domain Model V2.1.1`.

### Observable product behaviour

`Core User Journeys V2.1.1` wins.

Example:

> What should happen after a failed synchronization?

Answer comes from `Core User Journeys V2.1.1`, constrained by Domain invariants.

### Architecture question

`Technical Design V2.1.1` wins.

### Concrete library/framework/tool question

`Technology & Engineering Baseline V2.1.1` wins.

### Implementation sequencing question

`Bootstrap & Vertical Slice Plan V2.1.1` wins.

### External feasibility/current-provider fact

`Feasibility Spikes V2.1.1` is the evidence source, but it is explicitly time-sensitive and must be reverified when material.

### Vision or motivation

`Product V2.1.1` provides context, but it must never override a later accepted decision, Domain invariant, or MVP scope rule.

---

## 3. Normative vs. Informative Documents

Primarily normative:

```text
Product Decisions V2.1.1
Domain Model V2.1.1
MVP Scope Freeze V2.1.1
Technical Design V2.1.1
Technology Baseline V2.1.1
Bootstrap Plan V2.1.1
```

Behavioural specification:

```text
Core User Journeys V2.1.1
```

Primarily explanatory/evidentiary:

```text
Product V2.1.1
Feasibility Spikes V2.1.1
```

---

## 4. Canonical Vocabulary

Use these terms consistently.

### Build requirement importance

```text
ENABLING
CORE
UPGRADE
```

There is no current `POWER` or `OPTIONAL` requirement category.

### Unique readiness

```text
BLOCKED
PARTIAL
UNIQUE_READY
```

There is no `NEARLY_READY`, `READY`, `WELL_SUPPLIED`, or readiness percentage in the current model.

### Historical build status

```text
OUTDATED
```

`OUTDATED` is used for a BuildVariantRevision that is not verified for the active EvaluationContext's CompatibilityVersion.

### Stale personal knowledge

```text
STALE
```

`STALE` is used for data that exists but whose continued validity is questionable, such as an old personal DropRateOverride.

### Unknown drop probability

Unknown probability means:

```text
DropRelationship exists
DropEstimate absent
```

There is no canonical `UNKNOWN` DropEstimate provenance.

---

## 5. Version Vocabulary

V1 overloaded `GameVersion`.

V2.1.1 distinguishes two concepts.

### GamePatch

An exact or sufficiently precise PoE patch identifier when exact effective-version knowledge matters.

Examples:

```text
3.30.0
3.30.1
```

### CompatibilityVersion

A coarser compatibility cycle used for knowledge that is normally reviewed at major league/version boundaries.

Example:

```text
3.30
```

BuildVariantRevision verification primarily targets a `CompatibilityVersion`.

Catalog facts, DropRelationships, DropEstimates and personal probability overrides may additionally carry more precise applicability when a mid-cycle patch matters.

### EvaluationContext

Account knowledge is never evaluated against an unqualified global "current version". An `EvaluationContext` defines the knowledge context used for one evaluation, minimally:

```text
CompatibilityVersion
optional GamePatch when required
Ruleset
optional LeagueDefinition when genuinely league-specific
```

An `AccountContext` selects or determines the appropriate EvaluationContext for the view/evaluation. This is especially important for historical contexts and Standard, whose AccountContext identity survives across many CompatibilityVersions.

V0.1 does **not** require a generic temporal rule engine.

---

## 6. Resolved Canonization Decisions

### 6.1 Readiness model

The old Product Definition readiness model is superseded.

Canonical model:

```text
ENABLING / CORE / UPGRADE
BLOCKED / PARTIAL / UNIQUE_READY
```

No weighted readiness scoring is part of V0.1.

### 6.2 Build item ownership is not reserved between builds

Every BuildVariantRevision is evaluated independently against the complete current account ownership.

One owned item may make any number of different builds ready.

The application does **not** attempt to determine whether all displayed ready builds could be equipped simultaneously.

### 6.3 V0.1 requirement groups are non-consuming predicates

Within one BuildVariantRevision, V0.1 evaluates each requirement group against aggregate ownership without resource allocation between groups.

Explicit simultaneous duplicate need is represented with quantity where possible:

```text
2x Ring A
```

The following kind of combinatorial equipment requirement is a known future requirement but not supported by V0.1:

```text
slot 1: A
AND
slot 2: A OR B
```

where valid inventory combinations are `A+A` or `A+B` but not one `A` alone.

This future requirement must remain modelable without treating cross-build readiness as resource allocation.

### 6.4 Manual and synchronized ownership are alternative authorities

Future ownership mode is conceptually:

```text
MANUAL
OR
SYNCHRONIZED
```

Never implicit:

```text
MANUAL + SYNCHRONIZED
```

A future explicit correction/override mechanism may exist, but simple addition is forbidden.

### 6.5 Unique identity

The application owns a stable opaque `UniqueDefinitionId`.

External identities are mappings to that identity, not the identity itself.

Imports must not invent a merge when identity matching is ambiguous. Ambiguity becomes an import conflict requiring curator review.

### 6.6 PoB deduplication

Normalization + hashing supports exact normalized-reference deduplication.

It does **not** imply semantic or near-duplicate Build detection.

Similarity is a separate future problem.

### 6.7 Goals

A V0.1 Goal has:

```text
0 or 1 optional typed target
```

Supported target types:

```text
UniqueDefinition
BuildVariant
AcquisitionSource
```

A Goal does not simultaneously target several domain objects.

### 6.8 Probability semantics

A DropEstimate is meaningful only for:

```text
one DropRelationship
one defined attempt unit
specified conditions
one probability model
```

V0.1 implements only a fixed independent Bernoulli model.

Known future requirement:

```text
modifier/condition-dependent probability
```

This matters for real PoE encounters where item quantity affects particular unique drop probabilities.

### 6.9 Probability edge rules

For the V0.1 fixed model:

```text
0 < p <= 1
attempts n >= 0
unknown probability != p = 0
```

For `n = 0`, probability of at least one drop is `0`.

For `p = 1`, expected attempts and every positive probability threshold are `1`.

For `0 < p < 1`, threshold attempts use the smallest integer `n` whose probability reaches or exceeds the requested target.

### 6.10 Provider boundaries

Provider response/DTO types never become Domain types and never cross the adapter boundary into unrelated modules.

### 6.11 Module naming

There is no global top-level Kotlin package named `import`.

Provider adapters should normally live with the domain capability they feed, for example:

```text
catalog.integration.poewiki
account.integration.ggg
build.integration.pob
```

### 6.12 CurrentOwnership means transferable gear

`CurrentOwnership` contains account-owned items that remain transferable and can actually satisfy Build Readiness requirements.

Future synchronization must enumerate every supported observable item container and explicitly classify whether its contents contribute to CurrentOwnership.

Examples:

```text
stash                  → contributes
character inventory    → contributes
character equipment    → contributes
passive-tree jewels    → contributes
rucksack                → contributes
Animate Guardian gear  → does not contribute
```

Animate Guardian items may be observable through the provider but are not available as transferable player gear and therefore must not inflate Unique Readiness.

### 6.13 Build revision uniqueness

For each:

```text
(BuildVariantId, CompatibilityVersion)
```

there may be `0..1` revision that is active/eligible for current matching.

Historical/superseded revisions may coexist, but matching must never choose between two simultaneously active truths.

### 6.14 DropRelationship applicability

A DropRelationship is versioned/applicable knowledge, not an eternal fact.

It must be possible to state that a source produced an item in one EvaluationContext but not another. Probability versioning alone is insufficient.

### 6.15 Catalog conflict activation rule

An unresolved catalog identity conflict invalidates the complete candidate CatalogRevision.

```text
unresolved identity conflict
→ candidate invalid
→ no activation
→ previous active revision remains active
```

V0.1 has no partial catalog activation.

### 6.16 Catalog corrections persist as overlays

Developer-owned catalog corrections are app-owned data with provenance.

They are re-applied when building later import candidates and are never silently overwritten by new provider data. A correction changes only when deliberately edited/removed.

### 6.17 RequirementGroup structural invariants

Every RequirementGroup contains at least one Requirement.

Within one group, the same UniqueDefinition appears at most once. Multiple simultaneously required copies are expressed with `requiredQuantity > 1`.


Shared low-level HTTP infrastructure may exist separately.

---

## 7. Known Future Requirements, Not V0.1

These are known and expected, not speculative accidents:

```text
combinatorial / nested equipment requirements within a build
modifier-dependent drop probability models
GGG synchronized account ownership
fragment-derived available attempts
property/corruption/roll-aware item requirements
PoB parsing and exact normalized reference deduplication
```

V0.1 must not implement them prematurely, but its public domain semantics must not make them impossible.

### Final V2.1.1 hard invariants

```text
activated BuildVariantRevision readiness semantics are immutable
strict EvaluationContext matching: missing required precision never implicitly matches
catalog activation requires a complete provider observation; absence alone is not deletion/retirement evidence
0..1 active canonical estimate and 0..1 active personal override per exact probability applicability/model key
```

---

## 8. Deliberately Not Fully Specified Before Implementation

The specification does not attempt to freeze:

```text
all future database tables and indexes
all future command/query classes
pixel-perfect UI layout
all post-MVP state machines
all future acquisition-source probability models
all future requirement-expression syntax
```

Implementation-level contracts should be made explicit immediately before the feature that needs them.

The first Vertical Slice is the first architecture checkpoint.

---

## 9. Change Discipline

When a decision changes:

1. Update the authoritative V2.1.1 document.
2. Update every directly contradictory V2.1.1 statement.
3. If the change affects several documents, record the canonical meaning here when ambiguity is likely.
4. Do not leave an older current-looking definition in another V2.1.1 file.
5. Time-sensitive external claims must carry a verification date in `Feasibility Spikes V2.1.1`.

---

## 10. Canonical Product Chain

```text
ACCOUNT CONTEXT
      ↓
CURRENT OWNERSHIP
      ↓
BUILD VARIANT REVISION
      ↓
UNIQUE REQUIREMENTS
      ↓
UNIQUE READINESS
      ↓
MISSING UNIQUE
      ↓
ACQUISITION SOURCE
      ↓
DROP RELATIONSHIP / ESTIMATE
      ↓
AVAILABLE ATTEMPTS
      ↓
TARGET DROP PROBABILITY
      ↓
PLAYER DECISION
```

The Companion informs the final decision; it does not make it.
