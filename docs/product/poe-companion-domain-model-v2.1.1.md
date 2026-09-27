# PoE Companion — Domain Model V2.1.1

**Status:** Canonical domain definition **Purpose:** Define the core business concepts, relationships and invariants
independently of framework, database schema and UI technology.

---

## 1. Domain Areas

```text
GAME / REFERENCE KNOWLEDGE
  CompatibilityVersion
  GamePatch
  EvaluationContext
  LeagueDefinition
  UniqueDefinition
  ExternalIdentity
  ItemAvailability
  CatalogRevision
  CatalogCorrection
  AcquisitionSource
  DropRelationship
  DropEstimate

ACCOUNT STATE
  AccountContext
  OwnershipMode
  ManualOwnership
  CurrentOwnership
  PoEConnection
  SyncRun
  AccountSnapshot
  OwnedItem

BUILD KNOWLEDGE
  BuildArchetype
  BuildVariant
  BuildVariantRevision
  BuildReference
  RequirementGroup
  Requirement

DERIVED KNOWLEDGE
  UniqueReadiness
  UpgradeCompleteness
  SnapshotChange
  ProbabilityResult

PLAYER INTENT
  Goal
  GoalTarget
  GoalTemplate

PERSONAL OVERRIDES
  DropRateOverride
  AvailableAttempts
```

The central flow is:

```text
CurrentOwnership
      ↓ satisfies
BuildVariantRevision Requirements
      ↓ produces
UniqueReadiness
      ↓ identifies
Missing UniqueDefinition
      ↓ has
AcquisitionSource(s)
      ↓ may have
DropEstimate
      ↓ combined with
AvailableAttempts
      ↓ produces
ProbabilityResult
```

---

## 2. Fundamental Data Separation

### Game / reference knowledge

Facts or curated knowledge about PoE itself.

Examples:

```text
Nimis is a UniqueDefinition.
A BuildVariantRevision uses Nimis as CORE.
An AcquisitionSource can drop Nimis.
```

### Account state

Facts about one player's account in one AccountContext.

Examples:

```text
This context owns 1 Nimis.
This context has 12 manually entered attempts for Source X.
```

### Player intent

What the player wants to do.

Examples:

```text
Acquire Nimis.
Prepare Build X.
Get four Voidstones.
```

These categories must not be collapsed into each other.

---

## 3. CompatibilityVersion

`CompatibilityVersion` identifies the normal review boundary for knowledge that is generally validated at major PoE
version/league-cycle granularity.

Examples:

```text
3.30
3.31
```

Primary uses:

```text
BuildVariantRevision verification
DropRateOverride freshness
current product compatibility selection
```

A BuildVariantRevision is normally verified for one CompatibilityVersion.

---

## 4. GamePatch

`GamePatch` identifies a more precise game patch when exact effective-version knowledge matters.

Examples:

```text
3.30.0
3.30.1
```

Possible uses:

```text
mid-cycle drop-rate change
catalog availability change
mechanic change
```

V0.1 does not need a generic temporal rules engine. Exact patch metadata is used only where it adds real value.

---

## 4A. EvaluationContext

`EvaluationContext` defines which mutable PoE knowledge is applicable for one account evaluation.

Minimum conceptual fields:

```text
CompatibilityVersion
Ruleset
optional GamePatch
optional LeagueDefinition when genuinely league-specific
```

An AccountContext does not permanently embed one CompatibilityVersion. Instead, viewing/evaluating an AccountContext
uses an explicit EvaluationContext appropriate to that state/view.

This prevents historical account state from being evaluated against today's build/acquisition knowledge and supports
long-lived contexts such as Standard across many CompatibilityVersions.

### EvaluationContext applicability matching

Knowledge applicability is strict and dimension-aware. A knowledge fact that does not constrain a dimension is broad for
that dimension. If a fact explicitly constrains `CompatibilityVersion`, `GamePatch`, `Ruleset` or `LeagueDefinition`,
the EvaluationContext must contain sufficient information and satisfy that constraint.

Missing precision is never treated as an implicit match. For example, a fact known to apply only from `3.30.1` cannot be
applied to a historical EvaluationContext that is known only as `3.30` without a sufficiently precise patch. In that
case applicability is unknown/not established rather than assumed.

---

## 5. LeagueDefinition

`LeagueDefinition` represents one PoE league known to the application.

Potential facts:

```text
provider identity
name
realm
start/end
category
rules
current status
```

LeagueDefinition is reference knowledge, not account state.

---

## 6. AccountContext

`AccountContext` is the application's stable identity for one isolated personalized PoE environment.

It has its own opaque `AccountContextId` and references relevant LeagueDefinition/realm/rules facts.

Conceptually a context describes things such as:

```text
Challenge Softcore SSF / normal ruleset
Challenge Hardcore SSF / normal ruleset
Standard Softcore SSF / normal ruleset
```

UI labels are presentation and are not used as identity.

---

## 7. AccountContext Invariants

Account state never crosses AccountContext boundaries automatically.

Therefore ownership in one league/context never satisfies readiness in another.

These are AccountContext-specific unless explicitly modeled otherwise:

```text
ownership
snapshots
available attempts
goals
progress
personal drop overrides where applicable
```

Every readiness/acquisition evaluation also uses an explicit EvaluationContext. For a current challenge league this is
normally derived from the league/current patch; for Standard and historical views it must not be inferred from a global
active version.

GoalTemplates may intentionally be reusable across contexts.

---

## 8. Ruleset

Normal and Ruthless are distinct rulesets.

V0.1 supports normal ruleset only.

Ruthless data must not silently reuse normal acquisition assumptions.

---

## 9. Application Access

Application authentication is not a PoE domain concept.

It protects the Companion deployment and remains separate from PoEConnection and synchronization state.

---

## 10. PoEConnection

`PoEConnection` represents authorization to access private PoE account APIs.

Conceptual state:

```text
NOT_CONNECTED
CONNECTED
```

Connection does not imply a successful synchronization.

---

## 11. UniqueDefinition

`UniqueDefinition` represents one canonical mechanically distinct Unique item as understood by the Companion.

It is not an owned physical item.

It has a stable opaque application-owned `UniqueDefinitionId`.

---

## 12. Unique Identity

Displayed item name is not identity.

The model must be capable of distinguishing mechanically meaningful variants such as:

```text
normal vs Replica
Foulborn/mutated or other meaningful variants where relevant
other mechanically distinct definitions
```

The exact distinction policy is curated and version-aware.

---

## 13. ExternalIdentity

`ExternalIdentity` maps a provider-specific identity to one UniqueDefinition.

Conceptually:

```text
provider
providerKey
UniqueDefinitionId
```

External IDs never become the internal domain identity.

Import matching policy:

1. Match a trusted known ExternalIdentity when available.
2. Use normalized evidence only according to explicit provider-specific rules.
3. If identity remains ambiguous, create an import conflict for curator review.
4. Never silently merge ambiguous records because name/base happen to look similar.

---

## 14. Unique Metadata

Potential metadata:

```text
name
base type
item class
icon
Unique tier
external references
release/removal metadata
```

Metadata may be incomplete without invalidating identity.

Unique Tier is versionable enrichment and not identity.

---

## 15. ItemAvailability

`ItemAvailability` describes whether a UniqueDefinition is obtainable/relevant under specified game conditions.

Conceptual states:

```text
AVAILABLE
DROP_DISABLED
RETIRED
UNSUPPORTED_RULESET
UNKNOWN
```

Availability may depend on CompatibilityVersion/GamePatch, Ruleset and sometimes LeagueDefinition.

---

## 16. CatalogRevision

`CatalogRevision` represents one validated normalized catalog import that may become active.

Runtime behaviour uses locally persisted catalog knowledge, not live Wiki queries.

Preferred lifecycle:

```text
external data
→ candidate revision
→ normalize
→ identity reconciliation
→ validate
→ persist
→ activate
```

Failure leaves the previous active revision unchanged.

Hard V0.1 activation invariant:

```text
any unresolved identity conflict
→ candidate CatalogRevision invalid
→ candidate not activated
→ previous active revision remains active
```

Partial catalog activation is not supported in V0.1.

### Catalog completeness and disappearance

A candidate revision must be demonstrably complete enough for its configured provider/import scope before activation.
Completeness validation must detect conditions such as incomplete pagination, unexpectedly truncated result sets, failed
required batches, unresolved continuation state, or other evidence that the provider observation is partial.

Provider absence is not deletion evidence. If a previously known provider record is absent from a later successful
import, the application does **not** automatically delete the stable `UniqueDefinition` or infer `RETIRED` /
`DROP_DISABLED`. Such lifecycle/availability changes require positive provider evidence, another trusted source, or
explicit curator action.

---

## 16A. CatalogCorrection

`CatalogCorrection` is app-owned curated knowledge that overrides/corrects imported provider facts without mutating
upstream data.

It has provenance and persists independently of any single import run. Future catalog candidates re-apply active
corrections after provider normalization/identity reconciliation and before final validation.

A later provider refresh must never silently erase a correction. Corrections change only through deliberate curator
action.

---

## 17. OwnedItem

`OwnedItem` represents a normalized **transferable** physical item instance included in a synchronized AccountSnapshot
for ownership purposes. Provider-observed items from non-contributing containers (for example Animate Guardian
equipment) are not modeled as OwnedItems merely because the API exposes them; they may remain only in raw sync
diagnostics/provider data if useful.

It may reference a UniqueDefinition and contain observational metadata such as:

```text
external item id
location
character/stash
corruption
raw properties
```

V0.1 manual ownership does not require OwnedItem instances.

---

## 18. Aggregate Ownership

`CurrentOwnership` is the authoritative aggregate quantity map of **transferable account-owned gear** consumed by Build
logic.

An item being observable through an API is insufficient: it contributes only when the container's contents remain
available to move/use as player gear.

Conceptually:

```text
UniqueDefinitionId → quantity >= 0
```

For contributing containers, physical location is irrelevant to readiness. Future sync must explicitly classify every
supported observable container.

Expected policy examples:

```text
stash                  → contributes
character inventory    → contributes
character equipment    → contributes
rucksack                → contributes
passive-tree jewels    → contributes
Animate Guardian gear  → does not contribute
```

Example:

```text
stash: 1 Ring A
character equipment: 1 Ring A
→ current quantity: 2
```

Animate Guardian gear may be provider-observable but is permanently bound/consumed for normal player gearing purposes
and therefore must not increase CurrentOwnership.

---

## 19. OwnershipMode

The future account model has one active authoritative ownership source per AccountContext:

```text
MANUAL
SYNCHRONIZED
```

Manual and synchronized quantities are never implicitly added.

A future explicit correction system, if required, is a separate concept.

---

## 20. ManualOwnership

`ManualOwnership` stores manually maintained quantities for the active AccountContext when OwnershipMode is MANUAL.

It produces `CurrentOwnership` directly.

---

## 21. AccountSnapshot

`AccountSnapshot` represents one complete authoritative synchronized observation of an AccountContext at a point in
time.

Conceptually:

```text
AccountContext
observedAt
OwnedItems
```

Only complete snapshots may become active.

---

## 22. SyncRun

`SyncRun` is one attempt to produce a complete AccountSnapshot.

Conceptual states:

```text
IN_PROGRESS
SUCCEEDED
FAILED
```

It may retain diagnostic sub-step state.

---

## 23. Atomic Synchronization Invariant

A SyncRun may replace the active AccountSnapshot only after every required source succeeds and completeness validation
passes.

Example:

```text
stash fetch       ✓
character fetch   ✗

→ SyncRun FAILED
→ old active snapshot remains authoritative
```

Partial data may be retained for diagnostics but never exposed as current ownership.

---

## 24. Item Instance Identity

Where GGG supplies a usable item ID, the application may correlate an item moving between locations.

This is useful for history but not required for correct readiness.

Aggregate quantity remains authoritative.

---

## 25. Historical Ownership

Historical ownership is derived from snapshots.

There is no dedicated `everDiscovered` property.

Historical ownership never satisfies current Build requirements.

---

## 26. BuildArchetype

`BuildArchetype` is the human-level build family.

Examples:

```text
RF Chieftain
Archmage Ice Nova
Luminary Flame Link
```

It does not own version-specific requirements.

---

## 27. BuildVariant

`BuildVariant` represents a materially different form of a BuildArchetype **from the Companion's tracked requirement
perspective**.

A new Variant is generally warranted when the relevant Unique requirement structure changes meaningfully.

Minor gem/tree/config differences do not automatically create a Variant.

---

## 28. BuildVariantRevision

`BuildVariantRevision` is the versioned semantic definition of a BuildVariant.

It owns:

```text
CompatibilityVersion verification
RequirementGroups
verification metadata
references/classification links
```

Only a revision verified for the EvaluationContext's CompatibilityVersion participates in current matching.

Hard invariant:

```text
for each (BuildVariantId, CompatibilityVersion)
0..1 revisions may be active/eligible for matching
```

Historical/superseded revisions may coexist, but the evaluator must never have to choose between two active revisions
for the same tuple.

### Revision lifecycle and immutability

Draft BuildVariantRevisions may be edited. Once a revision is activated/verified, its readiness-relevant semantic
contents are immutable. This includes RequirementGroups, Requirement importance/logic, required quantities and other
facts that affect readiness semantics.

A semantic correction or change creates a new revision. Activating the new revision transactionally supersedes the
previously active revision for the same `(BuildVariantId, CompatibilityVersion)`. Historical active revisions are never
rewritten in place. Purely editorial metadata that cannot affect semantic evaluation may be corrected separately where
useful.

---

## 29. OUTDATED

A revision not verified for the EvaluationContext's CompatibilityVersion is `OUTDATED`.

It may be inspected historically but is excluded from current readiness and matching.

Creating a current revision requires curator review; there is no automatic migration.

---

## 30. BuildReference

`BuildReference` is evidence describing a build, for example:

```text
PoB
build guide
video
external URL
```

It is not the BuildVariant and does not own requirement semantics.

---

## 31. PoB Reference Deduplication

A future importer may:

```text
decode
normalize
hash
```

and reuse an existing BuildReference when the normalized representation is identical.

This is exact normalized-reference deduplication only.

It does not determine:

```text
semantic BuildVariant identity
near-duplicate similarity
requirement classification
```

---

## 32. RequirementGroup

`RequirementGroup` represents one logical requirement expression in a BuildVariantRevision.

V0.1 fields conceptually include:

```text
importance
logic
requirements
```

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

Hard V0.1 structural invariants:

```text
each group contains at least 1 Requirement
the same UniqueDefinition appears at most once inside one group
requiredQuantity >= 1
```

If two identical copies are needed, use one Requirement with `requiredQuantity = 2` rather than duplicate Requirements.

---

## 33. Requirement

For V0.1 a Requirement is:

```text
UniqueDefinition
requiredQuantity > 0
```

Example:

```text
2x Ring A
```

---

## 34. Requirement Importance

### ENABLING

Without this group the defining mechanic of the Variant does not function as intended.

Any unsatisfied ENABLING group → `BLOCKED`.

### CORE

A tracked part of the defining key-Unique setup for this Variant.

All ENABLING satisfied but any CORE missing → `PARTIAL`.

### UPGRADE

A meaningful Unique upgrade that does not determine primary readiness.

UPGRADE never changes `BLOCKED/PARTIAL/UNIQUE_READY`.

---

## 35. Requirement Evaluation — Cross-Build Rule

Build readiness calculations are independent.

Ownership is never reserved by another build.

One physical copy may satisfy the readiness calculation for any number of separate BuildVariantRevisions because the
product asks whether each build could be assembled, not whether all builds could be simultaneously equipped.

---

## 36. Requirement Evaluation — V0.1 Within One Build

V0.1 RequirementGroups are evaluated as independent non-consuming predicates over CurrentOwnership.

Example:

```text
Group A requires 1x Ring X
Group B allows Ring X OR Ring Y
Owned: 1x Ring X
```

Both groups evaluate true in V0.1.

This is intentional predicate semantics, not an allocation solver.

If the build simply requires two identical copies simultaneously, curate:

```text
requiredQuantity = 2
```

---

## 37. Known Future Combinatorial Requirements

Some real builds require physical combination semantics that V0.1 cannot express correctly.

Example:

```text
slot 1: A
AND
slot 2: A OR B
```

Valid:

```text
A + A
A + B
```

Invalid:

```text
only one A
```

This is a known future requirement.

The future solution may use nested expressions, equipment-slot constraints or allocation-aware evaluation. V2.1.1
intentionally does not choose the mechanism yet.

The important invariant is that this future within-build allocation problem must not be confused with cross-build
ownership reservation.

---

## 38. Future Item Constraints

Future Requirement expressions may include:

```text
corruption/property constraints
numerical rolls
rare alternatives
special item states
```

These are outside V0.1.

---

## 39. UniqueReadiness

`UniqueReadiness` is derived, not authored.

Input:

```text
CurrentOwnership
BuildVariantRevision
```

States:

```text
BLOCKED
PARTIAL
UNIQUE_READY
```

---

## 40. Readiness Rules

```text
BLOCKED
→ at least one ENABLING group unsatisfied

PARTIAL
→ all ENABLING groups satisfied
→ at least one CORE group unsatisfied

UNIQUE_READY
→ all ENABLING groups satisfied
→ all CORE groups satisfied
```

UPGRADE is evaluated separately.

If there are no ENABLING and no CORE groups, required Unique conditions are vacuously satisfied but UI should
communicate `No required Uniques`.

---

## 41. UpgradeCompleteness

Derived separately from UPGRADE groups.

It may show counts and influence ordering among otherwise equal readiness states.

It does not claim build strength.

---

## 42. Build Availability vs. Build Quality

UniqueReadiness answers only:

> Does the account own the tracked Unique setup?

It does not evaluate:

```text
DPS
mapping
bossing
defense
player skill
rare gear
gems
passive tree
```

`UNIQUE_READY` never means `GOOD BUILD`.

---

## 43. AcquisitionSource

`AcquisitionSource` is an independently browsable way an item may be obtained.

Possible types:

```text
BOSS_ENCOUNTER
LEAGUE_MECHANIC
DIVINATION_CARD
VENDOR_RECIPE
GLOBAL_DROP
OTHER
```

V0.1 implements rich behaviour for encounter sources only.

Normal/Uber are separate sources when behaviour differs.

---

## 44. DropRelationship

`DropRelationship` connects:

```text
AcquisitionSource
→ UniqueDefinition
```

It means the source can produce the item **within its applicability context**.

A DropRelationship is mutable/versioned game knowledge, not an eternal fact. Conceptually it can carry/apply to:

```text
CompatibilityVersion
optional effective GamePatch
Ruleset
optional LeagueDefinition when genuinely league-specific
```

A source may therefore drop an item in one EvaluationContext and not in another.

The relationship may exist without a known probability.

---

## 45. AttemptDefinition

An encounter-style AcquisitionSource must define what one calculator attempt means whenever probability calculations are
supported.

Example:

```text
one completed Uber Eater of Worlds encounter
```

Attempt semantics belong to the source/probability model, not to the UI label alone.

---

## 46. DropEstimate

`DropEstimate` is canonical best-known probability knowledge for one applicable DropRelationship under defined
conditions and a defined probability model.

Conceptual fields:

```text
probability model type
parameters
attempt definition
conditions
provenance
CompatibilityVersion
optional exact effective GamePatch
review metadata
```

For one exact probability applicability key — conceptually
`(DropRelationship, ProbabilityModel, applicability/conditions)` — there may be at most `0..1` active canonical
DropEstimate. Older estimates/evidence may remain historical, but probability selection must never require choosing
arbitrarily between multiple active canonical values for the same key.

---

## 47. DropEstimate Provenance

Canonical provenance values initially include:

```text
OFFICIAL
MEASURED
COMMUNITY_ESTIMATE
```

A relationship with no DropEstimate represents unknown probability.

There is no `UNKNOWN` provenance on an existing estimate.

`USER_DEFINED` is not canonical provenance; personal data uses DropRateOverride.

---

## 48. Probability Models

### FIXED_BERNOULLI — V0.1

One attempt has constant independent probability `p`.

Constraints:

```text
0 < p <= 1
attempts n >= 0
```

Probability of at least one success:

```text
1 - (1-p)^n
```

Expected attempts:

```text
1/p
```

Threshold attempts use the smallest integer `n` that reaches or exceeds the requested target probability.

Special cases:

```text
n = 0 → probability 0
p = 1 → expected attempts 1 and all positive thresholds 1
```

Unknown probability is never represented as `p = 0`.

### CONDITIONAL / MODIFIER-DEPENDENT — known future requirement

Some real PoE drops depend on encounter conditions such as area quantity.

The Domain therefore recognizes that not all DropRelationships have a single constant `p`.

The exact future model is intentionally deferred until concrete mechanics are implemented.

---

## 49. DropRateOverride

`DropRateOverride` is personal probability input for one DropRelationship/model context.

It never overwrites canonical DropEstimate.

The user may select canonical or personal input for supported calculations. For one exact
`(DropRelationship, ProbabilityModel, applicability/conditions)` key there may be at most `0..1` active personal
override. Historical/replaced overrides may remain stored but cannot create two simultaneous personal inputs for the
same calculation context.

An override becomes `STALE` when its probability applicability context no longer matches the active EvaluationContext.
This includes relevant CompatibilityVersion, GamePatch, Ruleset or League-specific changes rather than only
major-version changes.

---

## 50. AvailableAttempts

`AvailableAttempts` represents how many currently available attempts the user has for an encounter-like
AcquisitionSource.

V0.1 stores a manually entered non-negative integer per:

```text
AccountContext
AcquisitionSource
```

Automatic derivation from fragments/resources is post-MVP.

---

## 51. Goal

`Goal` represents current player intent in one AccountContext.

Minimum semantics:

```text
title
completion state
AccountContext
optional GoalTarget
```

---

## 52. GoalTarget

A Goal has **zero or one** typed target.

V0.1 target types:

```text
UniqueDefinition
BuildVariant
AcquisitionSource
```

A Goal does not duplicate target-domain facts.

Example:

```text
Goal: Acquire Nimis
Target: UniqueDefinition(Nimis)
```

The application resolves current ownership/acquisition context from normal modules.

---

## 53. GoalTemplate

Reusable player intent that can be copied into another AccountContext with fresh progress.

Example:

```text
4 Voidstones
Atlas Completion
```

Templates may cross league boundaries; Goal completion does not.

---

## 54. SnapshotChange / RecentChanges

Differences between complete snapshots may derive:

```text
new Unique
quantity increased
quantity decreased
Unique disappeared
```

Readiness changes can be derived from old/new CurrentOwnership.

A persistent generic event system is not required initially.

---

## 55. Derived Domain Services

Illustrative pure/application-level services:

```text
OwnershipAggregator
UniqueReadinessEvaluator
BuildMatcher
SnapshotDiffer
ProbabilityCalculator
```

The names are not framework requirements.

---

## 56. Core Invariants

### I1 — Context isolation

Account state never crosses AccountContext boundaries automatically.

### I2 — One authoritative ownership mode

Manual and synchronized ownership are not implicitly combined.

### I3 — Atomic sync

Only a complete successful sync may replace the active AccountSnapshot.

### I4 — Current means current

Current readiness uses CurrentOwnership, not historical ownership.

### I5 — Cross-build non-consumption

No BuildVariantRevision reserves ownership from another BuildVariantRevision's readiness calculation.

### I6 — V0.1 requirement predicate semantics

V0.1 groups do not consume quantities across groups; unsupported combinatorial equipment constraints must not be
silently approximated as correct.

### I7 — ENABLING hard gate

Any missing ENABLING group causes BLOCKED.

### I8 — UPGRADE independence

UPGRADE never changes primary UniqueReadiness.

### I9 — Current build verification

Only revisions verified for the active EvaluationContext's CompatibilityVersion participate in current matching.

### I10 — Historical build visibility

OUTDATED revisions may be inspected but not current-matched.

### I11 — Reference != build

BuildReference identity and BuildVariant identity are separate.

### I12 — Exact dedupe != similarity

PoB/reference hashing may deduplicate exact normalized references only.

### I13 — Requirement semantics are curated

Requirement semantics belong to BuildVariantRevision.

### I14 — Internal identity is owned by the application

External provider IDs do not define UniqueDefinition identity.

### I15 — Ambiguity is explicit

Ambiguous import identity becomes a conflict rather than a guessed merge.

### I16 — Unknown stays unknown

No fake value is created to enable a feature.

### I17 — Personal estimates do not overwrite canonical estimates

DropRateOverride remains separate.

### I18 — Estimate semantics include conditions

A probability number is meaningless without its attempt definition and applicable conditions/model.

### I19 — Goals reference rather than copy

GoalTarget links to domain knowledge; Goals do not duplicate it.

### I20 — Player agency

The Companion presents possibilities and probabilities; the player decides what to do.

---

## 57. Primary Domain Queries

### Build optionality

> Given this AccountContext's CurrentOwnership and current verified BuildVariantRevisions, what is the UniqueReadiness
> of each Variant?

### Missing-item acquisition

> Given a missing UniqueDefinition, which AcquisitionSources can produce it and what probability knowledge exists?

### Target farming

> Given a supported DropEstimate/model and AvailableAttempts, what is the probability of at least one target drop?

---

## 58. Explicitly Deferred Complexity

```text
rare-item quality
crafting requirements
passive trees
gem readiness
cluster jewels
corruption/roll constraints
combinatorial requirement allocation
conditional probability model implementation
recursive acquisition graphs
Trade economy
build-strength scoring
community curation
multi-user permission model
Ruthless acquisition rules
```

---

## 59. V0.1 Domain Success Scenario

```text
Owned:
A = 1
B = 1
C = 0

Build X:
ENABLING: A
CORE: B, C

→ PARTIAL
→ missing C

C has DropRelationship to Boss Y
Boss Y fixed estimate: p = 0.10 per completed encounter
AvailableAttempts = 12

→ probability >=1 = 71.8%
→ 95% threshold = 29 attempts

Set C = 1
→ UNIQUE_READY
```

This scenario must work without special-case logic.
