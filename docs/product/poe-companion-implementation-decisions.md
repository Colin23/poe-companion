# PoE Companion — Implementation Decisions

**Status:** Evolving implementation clarifications
**Purpose:** Record durable product/domain/architecture decisions discovered while implementing the current canonical specification.

This document does not replace or silently modify the current canonical product specification. When implementation reveals an ambiguity, missing detail, or useful clarification, record the current implementation decision here. If a future implementation decision conflicts with the canonical specification, resolve that conflict explicitly rather than treating this document as an override.

---

## 1. Effective Patch Knowledge Uses Change-Point Semantics

### Context

The canonical specification distinguishes broad `CompatibilityVersion` knowledge from more precise `GamePatch` knowledge and describes some facts as becoming effective at a particular patch.

The first `EvaluationContext` implementation intentionally supports only exact patch constraints. This establishes strict matching and prevents missing patch precision from being treated as an implicit match, but it is not intended to become the final temporal model for versioned game knowledge.

### Decision

Versioned game knowledge should eventually use **effective-from / change-point semantics** rather than requiring one applicability record for every unchanged game patch.

Conceptually:

```text
fact introduced or changed at patch X
        ↓
remains current for later patches
        ↓
superseded by a later change at patch Y
```

For example:

```text
3.29.0  source can drop item
3.29.3b source no longer drops item
3.30.2  source can drop item again
```

An evaluation at an intermediate patch uses the latest applicable fact whose effective patch is not later than the evaluation patch.

Unchanged facts are therefore **not duplicated for every baby patch**.

### Current Foundation

The current applicability foundation supports:

```text
exact CompatibilityVersion constraints
exact Ruleset constraints
exact GamePatch constraints
exact LeagueDefinition identity constraints
tri-state APPLIES / DOES_NOT_APPLY / UNKNOWN results
```

Missing required context precision remains `UNKNOWN`; it must never be treated as a match.

Exact `GamePatch` matching is intentionally limited. It is a safe foundation for strict applicability, not the final representation of temporal knowledge.

### First Concrete Change-Point Use

V2.1.2 source availability/encounter knowledge is the first concrete feature that requires effective-patch resolution, before `DropRelationship`.

`GamePatch` therefore supports a deliberately narrow ordering operation only within one `CompatibilityVersion`:

```text
numeric patch first
same numeric patch: unsuffixed release first
suffix-bearing releases: lexical suffix order
```

For example:

```text
3.30.0 < 3.30.0b < 3.30.0c < 3.30.1
```

Comparing patches from different compatibility versions is rejected rather than pretending that `GamePatch` defines a universal ordering.

GGG hotfix events are not folded into `GamePatch` ordering. The official patch-note history exposes entries such as lettered `3.27.0b` through `3.27.0h` separately from `3.27.0g Hotfix 2` and similar hotfix events. Hotfix chronology remains outside this value type unless a future canonical feature explicitly requires it.

Official evidence:
- https://www.pathofexile.com/forum/view-forum/1122
- https://www.pathofexile.com/forum/view-forum/patch-notes/page/2

Source encounter knowledge uses complete snapshots at change points, not field-level deltas. A baseline snapshot has no effective patch; later snapshots become current at their effective patch and remain current until superseded by another snapshot.

When a timeline contains patch-specific change points but the `EvaluationContext` lacks exact patch precision, resolution remains `UNKNOWN`. A patchless context can resolve a baseline-only timeline because no known patch change can alter the answer.


---

## 2. Resolved Applicability Is Not Provider Uncertainty

### Context

The first applicability model uses nullable dimensions to represent whether canonical knowledge constrains a dimension such as patch or league.

A missing provider/source field can mean either:

- the fact is genuinely broad on that dimension; or
- the provider did not supply enough evidence to know.

Those meanings must not be conflated.

### Current Decision

`KnowledgeApplicability` represents **resolved/trusted canonical applicability**.

For its nullable dimensions:

```text
null = canonical knowledge is known to be broad on this dimension
value = canonical knowledge is constrained to this exact value
```

`null` must never be used to mean "the provider/source did not tell us."

Unknown provider/source applicability is therefore not representable inside `KnowledgeApplicability` yet. It must remain unresolved in the ingestion/reconciliation layer instead of being promoted into canonical knowledge.

### Likely Future Direction

If real provider data requires preserving applicability uncertainty in the canonical model, introduce an explicit representation conceptually equivalent to:

```text
BROAD
CONSTRAINED(value)
UNKNOWN
```

Do not interpret the current nullable representation as the final uncertainty model.

Ruleset-specific versioned knowledge should remain independently versionable. If the same fact is established for both Normal and Ruthless, represent separate ruleset-specific histories rather than one multi-ruleset record, so either ruleset can later change without partial-supersession complexity.

Ruthless remains unsupported in V0.1; current Normal knowledge must not later be treated as proof that Ruthless is explicitly excluded.


---

## 3. League Definitions Are Manually Curated and Provider-Informed in V0.1

### Context

V0.1 needs durable league and account-context identity before personalized ownership can be isolated correctly.

At the time of this decision (2026-10-01), the official Path of Exile developer documentation states that GGG is currently unable to process new application registrations. The product therefore cannot make live GGG OAuth/API access a prerequisite for its V0.1 league model.

GGG's published League schema is still useful design evidence. It exposes separate league identity/name/realm data and league rules such as `Hardcore` and `NoParties` (SSF), but provider terminology and encoding do not become the Companion's domain model directly.

Official references:

- https://www.pathofexile.com/developer/docs
- https://www.pathofexile.com/developer/docs/reference

### Decision

V0.1 manually curates application-owned `LeagueDefinition` records. Their identity is the opaque `LeagueDefinitionId`; display metadata does not define identity.

The current internal model represents the league dimensions explicitly:

```text
LeagueType
    STANDARD
    CHALLENGE

LeagueParticipation
    SSF
    TRADE

LeagueMortality
    SOFTCORE
    HARDCORE

GameRealm
    PC
    XBOX
    SONY
```

These are Companion-owned concepts. In particular, `LeagueType` is intentionally not named `LeagueCategory`, because GGG already uses `category` for a different provider concept.

A league's human-readable name remains metadata. UI labels such as "SSF Standard" or "Hardcore SSF <league>" should be derived from structured dimensions rather than stored as identity-bearing strings.

### Future Provider Integration

If GGG API access becomes available, a provider adapter should translate GGG league data and rule IDs into the Companion's typed model. Provider DTOs and provider-specific rule names must not leak through the domain.

The current representation is not treated as permanently complete. Real provider observations may justify additional dimensions or refinements later, but V0.1 does not wait for unavailable API access before establishing honest account-context isolation.


---

## 4. Account Context Identity Is Immutable; Evaluation Context Is Constructed Explicitly

### Context

The canonical model separates personalized state isolation from game-version evaluation:

```text
AccountContext
= which isolated player environment owns the personalized state

EvaluationContext
= under which game/version/rules that state is evaluated
```

The bootstrap plan lists `EvaluationContext` among persistence integration concerns, but persisting arbitrary standalone evaluation-context rows would risk turning a transient evaluation value into unnecessary identity/state.

### Decision

An existing `AccountContextId` has immutable defining configuration:

```text
LeagueDefinitionId
Ruleset
```

Changing either creates a different account context rather than redefining the existing identity. Personalized state can therefore safely remain attached to one stable meaning.

`CompatibilityVersion` and `GamePatch` remain outside `AccountContext`.

For V0.1, the application will persist/manual-select the relevant account context plus version/patch configuration and construct an explicit `EvaluationContext` for an evaluation. A standalone persistent `EvaluationContext` entity is not required merely because the value participates in persistence-backed workflows.

The persisted V0.1 selection is the application's one **current** selection, not a history of context switches and not a per-`AccountContext` store of last-used version/patch preferences. Switching the current selection does not reset or overwrite personalized state belonging to either context: ownership, attempts, goals and future synchronized state remain attached to their own `AccountContextId`. If the later UI demonstrates value in restoring a separate last-used CompatibilityVersion/GamePatch for each AccountContext, that preference can be modeled then rather than being introduced speculatively.

Future historical records may store the exact evaluation context they were observed/evaluated under when that history becomes a concrete requirement.


---

## 5. Persisted Manual Ownership Is Sparse

### Context

The initial manual-ownership persistence schema allowed quantity zero because V0.1 accepts non-negative manual quantity input and the write application service did not yet exist. Once the command boundary became concrete, keeping explicit zero rows no longer provided useful semantics and would make manual storage differ unnecessarily from future synchronized ownership aggregation.

### Decision

Manual ownership input still accepts zero as the meaningful instruction "this context owns none of this Unique." Persisted manual ownership is sparse:

```text
quantity > 0
→ persist/update one ManualOwnership row

quantity = 0
→ delete the ManualOwnership row

no row
→ owned quantity 0
```

The database therefore permits only positive quantities for persisted `manual_ownership` rows. Existing explicit-zero rows are removed by the migration that tightens this constraint.

`CurrentOwnership` continues to expose the same semantic result regardless of source representation: absence means quantity zero. Future synchronized ownership is expected to be naturally sparse as well.


---

## 6. Build Revision Identity, Lifecycle, and Persistence Synchronization

### Context

Build revision persistence combines immutable revision scope, editable draft snapshots, and lifecycle transitions that must remain safe under concurrent curator actions.

A `BuildVariantRevisionId` identifies one revision whose defining scope is:

```text
BuildVariantId
CompatibilityVersion
```

Requirement groups and requirements are editable while the durable revision remains `DRAFT`. Once activated, readiness-relevant semantic contents are immutable.

Lifecycle transitions use conditional bulk updates so competing commands can detect that durable state changed instead of silently overwriting one another. JPQL bulk updates execute directly against the database and can therefore leave a previously managed JPA entity stale inside the same persistence context unless the affected entity is synchronized explicitly.

### Decision

An existing `BuildVariantRevisionId` permanently remains attached to the same:

```text
BuildVariantId
CompatibilityVersion
```

Saving an existing draft snapshot must reject a reconstructed revision whose variant or compatibility version differs from the durable revision. JPA mapping flags such as `updatable = false` are not treated as sufficient application-level protection against redefining identity.

Build-revision activation keeps the optimistic concurrency contract established for V0.1:

```text
conditional lifecycle transitions
+
PostgreSQL partial unique ACTIVE index
```

For competing activation commands in one `(BuildVariantId, CompatibilityVersion)` scope:

```text
one command may succeed
a losing command rolls back
the loser receives an explicit BuildRevisionActivationConflictException
no automatic retry
```

Automatic retry is intentionally avoided because retrying a genuinely competing activation could turn an explicit conflict into silent last-writer-wins supersession.

The database enforces at most one `ACTIVE` revision per `(BuildVariantId, CompatibilityVersion)`. `OUTDATED` remains derived from compatibility with the current evaluation context and is not persisted as a revision lifecycle state.

After a successful JPQL bulk lifecycle transition, persistence must synchronize the affected managed revision entity with the database. This synchronization is targeted to that revision; the persistence context must not be globally cleared merely to repair one stale managed entity.

### Deliberately Deferred Enforcement

A `BuildVariantId` conceptually remains attached to one `BuildArchetypeId`.

The current persistence adapter does not yet expose a concrete BuildVariant authoring/update application workflow, so explicit persisted re-parenting protection is deferred rather than added speculatively. Revisit this when the first BuildVariant authoring command/service is introduced. That workflow must reject changing the archetype of an existing `BuildVariantId` instead of relying only on JPA's `updatable = false` mapping.


---

## 7. Boss Display Names Are Metadata; Duplicate Protection Belongs at the Curator Boundary

### Context

V2.1.2 establishes that `BossDefinitionId` is stable application-owned identity and that boss display names are metadata rather than identity.

The first `BossDefinition` domain object can validate one boss in isolation, but it has no collection or persistence context from which to determine whether another canonical boss already represents the same game entity.

Treating the display name itself as database identity would also be too strong. Future real data may expose rename, alias, normalization, localization, or rare same-name cases that should not silently redefine application-owned identity.

### Current Decision

The initial `BossDefinition` requires only a non-blank display name.

This is **not** a product decision that duplicate boss definitions are desirable or normally supported.

Normal curator workflows should prevent or strongly guard against accidentally creating two `BossDefinition` records that appear to represent the same boss. The initial identity-domain PR deliberately does not encode cross-record name uniqueness because that rule belongs at a boundary that can see existing canonical records.

Do not add a domain test asserting that two same-named bosses are a supported semantic case merely because different `BossDefinitionId` values technically allow construction in isolation.

### Deferred Enforcement

Revisit duplicate protection when the first real BossDefinition persistence/curator authoring workflow is implemented.

That workflow must decide what counts as an effective duplicate, including at least:

```text
leading/trailing whitespace
case differences
Unicode normalization
boss renames or aliases
whether a legitimate same-display-name/different-entity case exists
```

The normal path should reject or otherwise strongly block accidental duplicates. A database `UNIQUE(display_name)` constraint must not be introduced before those semantics are decided, because display text remains metadata rather than canonical identity.

---

## 8. Drop Relationship Identity Survives Applicability Changes

### Context

Canonical acquisition knowledge must distinguish three different claims:

```text
no known source-to-Unique relationship
explicitly known CAN_DROP
explicitly known CANNOT_DROP
```

Treating the absence of a `DropRelationship` as `CANNOT_DROP` would convert missing canonical knowledge into false negative knowledge. Deleting/recreating relationships whenever drop applicability changes would also make later `DropEstimate`, personal override, and historical references attach to unstable identities.

### Decision

`DropRelationship` is the stable application-owned canonical association between one:

```text
AcquisitionSourceId
UniqueDefinitionId
```

Its `DropRelationshipId` survives applicability changes such as:

```text
CAN_DROP
→ CANNOT_DROP
→ CAN_DROP
```

Versioned `DropRelationshipSnapshot` values carry the explicit current state for one `AcquisitionKnowledgeScope`. Missing applicable knowledge resolves to `UNKNOWN`; it is never promoted to `CANNOT_DROP`.

Therefore:

```text
no DropRelationship / no applicable relationship knowledge
    != CANNOT_DROP

CANNOT_DROP
    = explicit canonical negative knowledge
```

Source availability remains a separate fact. A source may be AVAILABLE while a relationship is CANNOT_DROP, and historical drop knowledge may remain CAN_DROP while source availability is UNAVAILABLE.

### Deferred Enforcement

Normal canonical authoring should maintain one stable `DropRelationship` for one `(AcquisitionSourceId, UniqueDefinitionId)` pair.

The initial domain object cannot enforce that cross-record invariant because it has no repository or curator-collection context. Revisit and enforce pair uniqueness when DropRelationship persistence/curator authoring is introduced rather than inventing an in-memory global registry.

---

## 9. Fixed Bernoulli Probability Is a Pure Known-Probability Model

### Context

V0.1 supports fixed independent Bernoulli probability calculations, while canonical acquisition knowledge may also have no known probability at all.

Conflating unknown probability with a numeric sentinel such as `0` would contradict the established "unknown stays unknown" rule. Threshold calculations also have a finite-reachability edge case: when per-attempt probability is less than 1, cumulative success probability approaches 100% but never reaches exactly 100% in a finite number of attempts.

### Decision

`FixedBernoulliModel` exists only when a concrete fixed probability is known:

```text
0 < probabilityPerAttempt <= 1
```

Unknown probability is represented by the absence of an applicable probability model/estimate, never by `p = 0`.

The pure model owns:

```text
cumulative probability within n attempts
expected attempts until first success
smallest finite attempt count reaching a target probability
```

For threshold calculation:

```text
0 < targetProbability < 1
    -> return the smallest finite attempt count that reaches the target

targetProbability = 1 and probabilityPerAttempt = 1
    -> 1 attempt

targetProbability = 1 and probabilityPerAttempt < 1
    -> reject because no finite attempt count reaches 100%
```

Attempt counts use `Int` in V0.1. A mathematically required threshold beyond `Int.MAX_VALUE` is rejected rather than overflowing or returning a misleading value.

`AvailableAttempts` remains separate personalized AccountContext state and does not belong inside the probability model.

---

## 10. Persist AcquisitionSource Identity Independently of Encounter Knowledge

### Context

Manually entered AvailableAttempts must reference a stable canonical AcquisitionSource. The initial Acquisition domain
models source identities and versioned encounter knowledge without persistence, so a database foreign key for personal
attempt state requires a persistent source identity first.

### Decision

Persist each canonical `AcquisitionSource` as one row keyed solely by its application-owned `AcquisitionSourceId`.

Existence of that row means that the canonical acquisition opportunity is known to the Companion. It does **not**
establish source availability, encounter participation, applicability to an EvaluationContext, or drop knowledge.

Keep the domain identity independent from the JPA representation. Versioned encounter knowledge, boss participation,
DropRelationships, and curator authoring/persistence workflows remain separate work.

This identity table is a prerequisite for the `AvailableAttempts` foreign key; no attempt count is stored in this step.

---

## 11. Manually Recorded AvailableAttempts Preserve Explicit Zero

### Context

V0.1 lets the player manually record currently available attempt counts for a canonical AcquisitionSource within an
AccountContext. A never-entered count is not evidence of zero attempts, unlike sparse manual ownership where absence
is deliberately defined as zero.

### Decision

`AvailableAttempts` is one non-negative integer fact per `(AccountContextId, AcquisitionSourceId)`. Its lifecycle is:

```text
no row             -> no attempt count has been entered
row with count = 0 -> explicitly recorded zero available attempts
row with count > 0 -> explicitly recorded positive available attempts
clear command      -> delete only the targeted fact, restoring the unrecorded state
```

The composite primary key permits at most one current fact per account/source pair. Database foreign keys require
the account context and canonical source identities to exist; a non-negative check permits explicit zero.

This is manually maintained personalized Acquisition state, independent of the selected EvaluationContext, canonical
source availability, encounter access knowledge, DropEstimate, and the pure probability model. No resource-derived
attempt counting or attempt history is introduced.
