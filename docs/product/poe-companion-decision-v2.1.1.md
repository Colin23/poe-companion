# PoE SSF Companion — Product Decisions V2.1.1

**Status:** Canonical accepted decisions
**Purpose:** Record current cross-cutting product/domain decisions. Detailed definitions belong to `Domain Model V2.1.1`; V0.1 inclusion belongs to `MVP Scope Freeze V2.1.1`.

---

## 1. Product Identity

The product is an SSF-first Path of Exile 1 account companion focused on:

```text
account state
Unique ownership
build optionality
target acquisition
drop probability
player goals
```

It does not optimize Trade currency efficiency.

---

## 2. Supported Contexts

Primary target:

> Temporary Challenge League — Softcore SSF — normal ruleset.

Secondary future-compatible contexts:

```text
Standard Softcore SSF
Challenge Hardcore SSF
Standard Hardcore SSF
```

Ruthless is a distinct ruleset and unsupported until intentionally modeled.

All personalized state is isolated by `AccountContext`.

---

## 3. AccountContext Identity

Do not reconstruct AccountContext identity from display flags.

The application owns an opaque `AccountContextId` referencing a `LeagueDefinition` and relevant realm/rules facts.

UI labels such as `SSF 3.30 HC` are presentation.

---

## 4. Decision Support, Not Optimization

The product presents possibilities and evidence.

It does not decide what the player should farm or play.

---

## 5. Trade Economy

No regular Trade-price integration is part of the core product.

---

## 6. Ownership

An item is currently owned if it exists anywhere in the authoritative observable account state for the selected AccountContext.

Locations may include:

```text
stash
character equipment
character inventory
```

Ownership is quantity-based.

Items equipped by another character still count.

---

## 7. Ownership Is Not Reserved Between Builds

Each BuildVariantRevision is evaluated independently against the full current ownership.

One Nimis can satisfy the readiness calculation of any number of different builds.

The product does not ask whether all displayed ready builds can be equipped simultaneously.

---

## 8. CurrentOwnership and Ownership Authority

`CurrentOwnership` means transferable account-owned items that are available to satisfy Build Readiness requirements. An item being observable through an external API does not by itself make it part of CurrentOwnership.

Future sync must explicitly classify supported observable containers. Transferable stash/inventory/equipment/passive-jewel/rucksack contents contribute; permanently bound or consumed containers such as Animate Guardian equipment do not.

Manual and synchronized ownership are alternative authoritative modes.

Future model:

```text
MANUAL
OR
SYNCHRONIZED
```

Never implicit addition of both sources.

---

## 9. Account Snapshots

Future synchronization creates complete AccountSnapshots.

Only a complete successful synchronization may replace the active snapshot.

Partial synchronization never changes displayed authoritative ownership.

Historical ownership does not satisfy current readiness.

There is no dedicated `everDiscovered` flag; history can be derived from snapshots.

---

## 10. Unique Identity

Displayed name is not canonical identity.

The application owns an opaque stable `UniqueDefinitionId`.

External provider identities map to it.

Ambiguous import identity is a curator conflict, not an invitation to guess.

An unresolved identity conflict invalidates the whole candidate CatalogRevision in V0.1. No partial activation occurs; the previous active revision remains authoritative.

Developer-owned catalog corrections are persistent app-owned overlays with provenance. They are reapplied on future imports and are never silently overwritten by provider refreshes. A candidate CatalogRevision must also pass completeness validation for its configured import scope; partial pagination/batch observations cannot activate. A provider record merely disappearing does not delete a stable UniqueDefinition or imply retirement/drop-disablement without positive evidence or curator action.


---

## 11. Item Properties

V0.1 ignores:

```text
corruption implicits
numerical rolls
complex item properties
rare-item alternatives
```

The long-term model must be extendable for these cases.

---

## 12. Version Semantics

Use separate concepts for:

```text
GamePatch          e.g. 3.30.1
CompatibilityVersion e.g. 3.30
```

BuildVariantRevision verification primarily targets a CompatibilityVersion.

More precise effective versions can be attached to knowledge that changes mid-cycle.

Evaluations use an explicit `EvaluationContext` rather than an implicit global current version. It contains the applicable CompatibilityVersion and, where needed, GamePatch/Ruleset/League facts. AccountContext identity is not itself tied permanently to one CompatibilityVersion; Standard is the obvious counterexample. Applicability matching is strict: unconstrained fact dimensions are broad, explicitly constrained dimensions must be present and match, and missing precision is never silently treated as a match.

---

## 13. Build Model

Canonical structure:

```text
BuildArchetype
    ↓
BuildVariant
    ↓
BuildVariantRevision
    ↓
BuildReference[]
```

A BuildVariant exists when the difference is meaningful for the Companion, especially its tracked requirement structure.

A BuildReference is evidence, not the semantic build identity.

---

## 14. PoB Identity and Deduplication

PoB content is not BuildVariant identity.

Future PoB import may decode, normalize and hash content to detect exact normalized duplicate references.

A hash does not provide near-duplicate or semantic-build detection.

Similarity is a separate future problem.

---

## 15. Build Versioning

A BuildVariantRevision that is not verified for the active EvaluationContext's CompatibilityVersion is `OUTDATED`.

Outdated revisions remain inspectable but are excluded from current account matching/readiness.

For each `(BuildVariantId, CompatibilityVersion)`, at most one revision may be active/eligible for matching. Historical revisions can coexist but cannot create two current truths. Draft revisions may be edited; once activated/verified, readiness-relevant semantic contents are immutable. A semantic change creates and atomically activates a new revision that supersedes the previous active revision rather than rewriting history.

`STALE` is reserved for data whose continued validity is questionable, such as old personal estimates.

---

## 16. Requirement Importance

Canonical categories:

```text
ENABLING
CORE
UPGRADE
```

No current `POWER` or `OPTIONAL` category.

Missing ENABLING blocks the variant.

Missing CORE makes the variant PARTIAL once all ENABLING groups are satisfied.

UPGRADE never changes primary Unique Readiness.

---

## 17. Requirement Logic

V0.1 supports:

```text
ALL
ANY
quantity
```

Every RequirementGroup contains at least one Requirement. The same UniqueDefinition appears at most once inside one group; duplicate physical need is represented by `requiredQuantity > 1`.

Requirement semantics belong to BuildVariantRevision.

References and population statistics do not own these semantics.

---

## 18. Requirement Consumption Semantics

V0.1 requirement groups are independent predicates over aggregate CurrentOwnership.

Items are not consumed or reserved while evaluating one group after another.

Explicit simultaneous duplicate need should be represented using quantity where possible.

Known future requirement: combinatorial equipment constraints such as:

```text
A AND (A OR B)
```

where valid physical combinations depend on allocation.

V0.1 does not implement a resource-allocation solver or nested requirement language.

---

## 19. Unique Readiness

Canonical states:

```text
BLOCKED
PARTIAL
UNIQUE_READY
```

No `NEARLY_READY` state.

No prominent readiness percentage.

A variant with no ENABLING or CORE groups should be presented as:

```text
No required Uniques
```

rather than prominently as UNIQUE_READY.

---

## 20. Build Quality

`UNIQUE_READY` means the tracked Unique setup is available.

It does not mean the build is strong, good, fast, safe, meta, or appropriate for the player.

V0.1 does not rank those qualities.

---

## 21. Acquisition Model

Use the generic concept `AcquisitionSource`.

Normal and Uber encounter variants are separate sources when access, drops, rates, or conditions differ.

Items and sources have a many-to-many relationship through `DropRelationship`/acquisition relationships.

A DropRelationship has an applicability context: whether a source can produce an item may change between CompatibilityVersions/GamePatches/Rulesets/Leagues. It is not an eternal source→item fact.

AcquisitionSource is independently browsable.

---

## 22. DropEstimate

A DropRelationship may exist without a DropEstimate.

That means:

```text
source is known
probability is unknown
```

For one exact `(DropRelationship, ProbabilityModel, applicability/conditions)` key, at most one canonical DropEstimate may be active. Historical evidence/estimates may remain stored. Likewise, at most one personal DropRateOverride may be active for the same exact key.

A canonical DropEstimate has real provenance such as:

```text
OFFICIAL
MEASURED
COMMUNITY_ESTIMATE
```

There is no `UNKNOWN` provenance on an existing estimate.

Personal estimates live in `DropRateOverride`, not canonical provenance.

---

## 23. Probability Model

V0.1 implements only fixed independent Bernoulli attempts.

A supported fixed estimate defines:

```text
attempt unit
conditions
probability p
```

with:

```text
0 < p <= 1
n >= 0
```

Unknown is not encoded as `p = 0`.

The probability of at least one success is:

```text
1 - (1-p)^n
```

Expected attempts are `1/p` and are not guarantees.

---

## 24. Conditional Drop Probability Is a Known Requirement

Real PoE encounter drops can depend on modifiers such as area item quantity.

The long-term model must therefore allow multiple probability-model types, for example conceptually:

```text
FIXED
QUANTITY_SCALED / CONDITIONAL
```

The exact future model is intentionally not designed yet.

V0.1 must not pretend a conditional drop is fixed if that would be materially false.

---

## 25. Available Attempts

V0.1 attempts are manually entered per:

```text
AccountContext
AcquisitionSource
```

Automatic derivation from fragments/resources is post-MVP.

---

## 26. Personal Drop Overrides

A personal DropRateOverride never overwrites the canonical estimate.

It can be selected as calculator input.

It becomes `STALE` whenever its probability applicability context no longer matches the active EvaluationContext, including relevant mid-cycle GamePatch changes, unless explicitly reviewed/reused.

---

## 27. Goals

Goals are manual player intent.

A V0.1 Goal has zero or one optional typed target:

```text
UniqueDefinition
BuildVariant
AcquisitionSource
```

Goals reference existing domain knowledge rather than copying it.

Generic dependency graphs are not V0.1.

Reusable GoalTemplates may cross AccountContexts with fresh progress.

---

## 28. Build Corpus

Initial builds are manually curated.

A small high-quality corpus is preferred over a large weakly classified dataset.

PoB import may later automate reference ingestion but not requirement semantics.

poe.ninja build/profile internals are not a product dependency.

---

## 29. Application Access vs. PoE Connection

Keep distinct:

```text
application authentication
PoE account connection
account synchronization
```

A protected single-user app with no PoE connection is valid.

A connected PoE account with no successful snapshot is valid.

---

## 30. Synchronization Trigger and Failure

Initial future sync is user-triggered.

GGG/API failure leaves the last successful snapshot active.

Item movement between stash and character should ideally preserve instance continuity where IDs allow it, but exact instance tracking is not required for correct aggregate ownership.

---

## 31. Data Freshness

Mutable game knowledge carries version/freshness information.

Historical data may remain visible but must not silently affect current calculations.

---

## 32. UI Direction

Primary environment: desktop web.

No in-game overlay initially.

The UI should be information-dense, explainable and functional rather than design-system-heavy.

---

## 33. Single User and Security

The first deployment is for one user and does not require public signup, roles, tenancy or password recovery.

It still requires reasonable security: HTTPS, protected access, secure sessions/cookies, secret hygiene, DB protection, backup, and safe future OAuth-token handling.

---

## 34. Core Product Model

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
DROP ESTIMATE / CONDITIONS
      ↓
AVAILABLE ATTEMPTS
      ↓
DROP PROBABILITY
```

---

## 35. Design Principle

> **Model only what we can explain clearly, keep unknown data unknown, and add complexity only when a real PoE use case requires it.**
