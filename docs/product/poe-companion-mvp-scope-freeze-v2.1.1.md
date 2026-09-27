# PoE Companion — MVP Scope Freeze V2.1.1

**Target:** V0.1
**Status:** Canonical scope freeze
**Purpose:** Define the smallest product version that validates the core hypothesis while respecting the V2.1.1 domain semantics.

## 1. Product Hypothesis

V0.1 tests:

> **Is it useful and enjoyable to represent an SSF account through owned Uniques, curated BuildVariant requirements and target-farming probabilities?**

Core validation chain:

```text
Unique Collection → Build Possibilities → Missing Unique → Acquisition Source → Available Attempts → Supported Drop Probability
```

## 2. Primary User and Deployment

V0.1 is built for one experienced PoE1 SSF player: the developer.

Deployment target:

```text
Hosted web application
Persistent PostgreSQL
Single application user
Desktop-first
```

Public signup, organizations, roles, tenancy and password-recovery infrastructure are out of scope. Reasonable internet-facing security is required.

## 3. V0.1 Functional Areas

```text
1. Unique Catalog
2. Manual Account Collection
3. Build Knowledge + Unique Readiness
4. Acquisition Sources + Fixed Boss Probability
5. Basic Goals
```

## 4. Version and Account Context

V0.1 supports manual configuration/selection of `CompatibilityVersion`, primary `AccountContext`, and the explicit EvaluationContext used for account/build/acquisition evaluation. AccountContext identity is not permanently bound to one CompatibilityVersion.

Primary context: current temporary Softcore SSF, normal ruleset.

Automatic GGG league discovery is not required. Previous manually created contexts may remain accessible. Ruthless is unsupported.

## 5. Unique Catalog — Required

Maintain a normalized local catalog of known PoE1 Uniques.

Minimum useful data:

```text
UniqueDefinitionId
name
base type if known
item class if known
availability if known
external identities/references
```

Desirable enrichment:

```text
Unique tier
icon
release/removal/effective version metadata
basic acquisition metadata
```

Catalog browsing must not require PoE Wiki to be online at runtime.

## 6. Catalog Import — Required

V0.1 includes a developer-triggered import from PoE Wiki Cargo as the source. RePoE integration is **OUT of V0.1** and may be reconsidered later as enrichment/cross-reference.

Import requirements:

```text
provider DTO/raw data isolated
normalize to internal candidate model
preserve provider identity
reconcile to application-owned UniqueDefinitionId
validate before activation
surface ambiguous identity rather than guessing
re-apply persistent app-owned catalog corrections
```

Any unresolved identity conflict invalidates the complete candidate CatalogRevision; V0.1 does not partially activate unaffected records. Active corrections persist across imports and are never silently overwritten by provider data. Candidate activation also requires completeness validation for the configured provider scope; incomplete pagination/batches fail activation. Provider-record absence alone never deletes a stable UniqueDefinition or implies retirement/drop-disablement.

A sophisticated approval UI is not required.

## 7. Catalog Revision — Minimal Required Support

Track enough to answer what was imported, when, from which provider/source revision, for which relevant compatibility/version context, and which revision is active.

A failed import leaves the previous active catalog usable.

## 8. Manual Ownership — Required

The user can manually set non-negative quantities for the selected AccountContext.

```text
Nimis = 1
Kaom's Heart = 2
```

V0.1 OwnershipMode is effectively `MANUAL`. This produces `CurrentOwnership`.

## 9. Automatic Account Sync — Out of V0.1

GGG OAuth/stash/character synchronization is post-MVP.

The domain/architecture must preserve the future rule:

```text
MANUAL OR SYNCHRONIZED
never implicit MANUAL + SYNCHRONIZED
```

No snapshot history or automatic recent-change detection is required in V0.1.

## 10. Unique Collection UI — Required

Browse:

```text
All
Owned
Missing
```

Basic search/filtering only.

Unique Detail includes at least name/useful metadata, ownership quantity, known Build associations, known AcquisitionSources and external references.

## 11. Build Model — Required

V0.1 supports manually curated:

```text
BuildArchetype
BuildVariant
BuildVariantRevision
BuildReference
```

A current BuildVariantRevision is verified for the EvaluationContext's CompatibilityVersion. For each `(BuildVariantId, CompatibilityVersion)`, `0..1` revisions may be active/eligible for matching. Activated/verified readiness semantics are immutable; semantic changes create a new revision and supersede the previous active revision rather than editing historical truth in place.

An old revision is `OUTDATED`, excluded from current matching by default, and optionally inspectable. Manual re-verification is supported.

## 12. BuildReference — Minimal Support

Store PoB URL/code, guide URL or other external URL.

Automatic PoB parsing/deduplication is out of V0.1.

## 13. Requirement Model — Required

Importance:

```text
ENABLING
CORE
UPGRADE
```

Logic:

```text
ALL
ANY
```

Each V0.1 Requirement references a `UniqueDefinition` and `requiredQuantity > 0`. Every RequirementGroup contains at least one Requirement, and the same UniqueDefinition appears at most once within one group; repeated physical need uses `requiredQuantity > 1`.

## 14. Requirement Evaluation Semantics — Required

Across builds, each BuildVariantRevision is evaluated independently against full CurrentOwnership. No item reservation across builds.

Within one build, V0.1 groups are independent non-consuming predicates.

If two copies of the same Unique are simply required simultaneously, curate `quantity = 2`.

Known unsupported physical-allocation case:

```text
A AND (A OR B)
```

V0.1 does not implement an allocation solver or nested-expression engine. If real curated data requires a form that cannot be represented honestly, do not silently encode known-wrong readiness.

## 15. Advanced Requirement Constraints — Out of V0.1

```text
corruption implicits
roll thresholds
special property combinations
rare-item alternatives
equipment-slot allocation
nested/combinatorial requirement expressions
```

These are known future-compatible areas, not MVP work.

## 16. Unique Readiness — Required

```text
BLOCKED
→ at least one ENABLING group missing

PARTIAL
→ all ENABLING satisfied
→ at least one CORE group missing

UNIQUE_READY
→ all ENABLING + CORE satisfied
```

UPGRADE completeness is separate. No readiness percentage. No `NEARLY_READY`.

No-required-Unique case displays `No required Uniques`.

## 17. Build Browser and Detail — Required

Build Browser groups current variants by readiness.

Build Detail shows Archetype/Variant, CompatibilityVersion/verification state, readiness, ENABLING/CORE/UPGRADE groups, owned/missing details and references.

V0.1 does not evaluate build strength, DPS, mapping, bossing or defenses.

## 18. AcquisitionSource — Required

V0.1 implements `BOSS_ENCOUNTER` as the only product-required AcquisitionSource type.

Other acquisition categories may exist as future domain concepts, but V0.1 has **no product requirement** to store, curate or display them. AcquisitionSources are independently browsable. Normal/Uber are separate sources when relevant behaviour differs.

## 19. DropRelationship — Required

Many-to-many relationship:

```text
AcquisitionSource ↔ UniqueDefinition
```

The relationship may exist without known probability. The relationship itself has EvaluationContext/applicability semantics because whether a source can produce an item may change by CompatibilityVersion/GamePatch/Ruleset/League.

## 20. Fixed DropEstimate — Required Where Known

V0.1 calculator supports only `FIXED_BERNOULLI`.

A usable estimate includes:

```text
p
attempt definition
applicable conditions
provenance
CompatibilityVersion
optional exact GamePatch when useful
```

Canonical provenance initially:

```text
OFFICIAL
MEASURED
COMMUNITY_ESTIMATE
```

Unknown probability is represented by no DropEstimate.

## 21. Conditional/Modifier-Dependent Drops — Known but Out of V0.1 Calculation

Some encounter Unique probabilities depend on conditions such as area item quantity.

V0.1 does not implement these effective-probability models.

For such a relationship the product may show the source and explanatory condition information, but must not knowingly present a misleading fixed probability.

## 22. Personal Drop-Rate Override — Required for Supported Fixed Model

A user may store a personal fixed probability for a supported relationship/model context.

Canonical estimate remains unchanged. The user chooses default vs personal input. For an exact `(DropRelationship, ProbabilityModel, applicability/conditions)` key there is at most one active canonical estimate and at most one active personal override. The override carries the relevant probability applicability context and becomes `STALE` whenever that context no longer matches the active EvaluationContext, including relevant GamePatch changes.

## 23. Available Attempts — Required

Manual non-negative integer per `AccountContext + AcquisitionSource`.

Automatic fragment/set counting is out of V0.1.

## 24. Probability Calculator — Required

For fixed independent attempts:

```text
0 < p <= 1
n >= 0
```

Output:

```text
Probability of >=1
Expected attempts
Attempts for 50%
75%
90%
95%
99%
```

Required edge behaviour:

```text
n = 0 → probability 0
p = 1 → expected 1, all positive thresholds 1
unknown → no calculation
```

Thresholds round upward to the first whole attempt reaching the target. Probability ranges are out of V0.1.

## 25. Goals — Basic Required Support

Goal fields:

```text
title
completed
AccountContext
0 or 1 optional typed GoalTarget
```

Target types:

```text
UniqueDefinition
BuildVariant
AcquisitionSource
```

No generic dependency graph. GoalTemplates/favorites are **POST-MVP**, not V0.1 scope.

## 26. Curator/Admin Capability — Required but Minimal

The developer needs a practical way to curate BuildArchetypes, BuildVariants, BuildVariantRevisions, Requirements, BuildReferences, AcquisitionSources, DropRelationships, DropEstimates and catalog corrections/identity conflicts where needed.

It may be ugly and developer-oriented.

## 27. V0.1 Minimum Views

```text
Unique Catalog
Unique Detail
Build Browser
Build Detail
Acquisition Browser
Acquisition Detail + fixed Probability Calculator
Goals
Basic curator/admin views
```

A minimal dashboard is optional until the central flow works.

## 28. First Vertical Slice

Given:

```text
A = 1
B = 1
C = 0

Build X:
ENABLING A
CORE B
CORE C

Boss Y:
C drop
FIXED_BERNOULLI p = 0.10
attempt = one completed Boss Y encounter
AvailableAttempts = 12
```

Then:

```text
Build X = PARTIAL
Missing = C
Probability >=1 = 71.8%
95% threshold = 29
```

After `C = 1`, Build X becomes `UNIQUE_READY`.

## 29. V0.1 Definition of Done

1. Real PostgreSQL-backed application starts.
2. Normalized Unique catalog can be imported and browsed.
3. Manual ownership quantities persist per AccountContext.
4. Current BuildVariantRevisions can be curated.
5. ENABLING/CORE/UPGRADE + ALL/ANY + quantities work with documented V0.1 predicate semantics.
6. Readiness derives correctly and explains missing groups.
7. AcquisitionSources and DropRelationships can be curated/browsed.
8. Supported fixed DropEstimates include attempt semantics/provenance.
9. Manual AvailableAttempts persist.
10. Fixed probability results/thresholds handle specified edge cases.
11. Personal fixed overrides coexist with canonical estimates, canonical/personal selection works, and stale applicability is represented correctly.
12. Basic Goals support zero/one typed target.
13. Complete A/B/C vertical slice works through the browser.
14. Application access, migrations, tests, container packaging and backup strategy are sufficient for early hosted use.

## 30. Explicitly Out of V0.1

```text
GGG OAuth/account sync
snapshot history/recent change detection
PoB parsing/hashing
near-duplicate Build similarity
rare/crafting evaluation
corruption/roll-aware readiness
combinatorial requirement allocation
conditional/quantity-scaled probability calculation
automatic fragment counting
recursive farming chains
Trade prices
poe.ninja dependency
RePoE integration
non-boss AcquisitionSource product support
GoalTemplates/favorite goals
public/multi-user/community curation
Ruthless
mobile app/overlay
AI/ML recommendation
microservices/Kafka/Redis/Elasticsearch/Kubernetes
```

## 31. Immediate Post-MVP Candidates

Likely candidates after V0.1 evidence:

```text
GGG account sync
conditional probability models for important encounters
fragment/resource-derived attempts
richer requirement expressions for real combinatorial cases
PoB reference import
GoalTemplates
more AcquisitionSource types
recent account changes
```

Their exact order depends on actual use.

## 32. Scope Rule

A feature enters V0.1 only if it is necessary to prove the core hypothesis or keep implementation/security/data semantics honest.

Known future requirements are documented without automatically becoming MVP scope.
