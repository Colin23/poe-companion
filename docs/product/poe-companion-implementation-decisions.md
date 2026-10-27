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

### Persistence/Curator Follow-Up

The first BossDefinition persistence and curator-create workflow is implemented in §16. It blocks likely accidental
duplicates using a conservative display-name comparison while keeping names separate from identity. Rename/alias
support and exceptional true same-name cases remain explicit future curator decisions, not implicit name keys.
A database `UNIQUE(display_name)` constraint is intentionally not introduced.

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

### Enforcement

One stable `DropRelationship` for each `(AcquisitionSourceId, UniqueDefinitionId)` pair is enforced by the
PostgreSQL identity table introduced in section 12. An existing `DropRelationshipId` cannot be rebound to a different
source or Unique through the persistence adapter. This does not persist versioned CAN_DROP/CANNOT_DROP knowledge.

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

---

## 12. DropRelationship Identity Is Durable and Pair-Unique

### Context

Canonical DropEstimate persistence needs a foreign key to a real DropRelationship identity. A relationship identity must
survive changes in which game contexts allow the drop, and multiple IDs for the same source-to-Unique pair would make
probability knowledge ambiguous.

### Decision

Persist a `DropRelationship` with its stable application-owned UUID and immutable defining pair:

```text
AcquisitionSourceId
UniqueDefinitionId
```

PostgreSQL enforces the unique pair as well as both foreign keys. The persistence adapter follows the AccountContext
identity pattern: inserting an absent ID is allowed; saving an existing ID with the same pair is idempotent; attempting
to bind an existing ID to another pair is rejected rather than updating it. Another ID for an existing pair is likewise
rejected by the database uniqueness constraint.

The presence of a relationship identity is not itself a claim that the source currently drops the Unique. Current
CAN_DROP/CANNOT_DROP state remains separately resolved by `DropRelationshipTimeline`; its accepted change points are
persisted separately (see §15). No DropEstimate or attempt probability is stored here.

---

## 13. Accepted Fixed DropEstimate Knowledge Is a Pure Scoped Timeline

### Context

The V0.1 probability calculator supports only fixed independent Bernoulli attempts. A bare probability has no reliable
meaning without the source/attempt semantics, applicability scope, provenance, and evidence. Game-patch changes to
probability must not be confused with later curator corrections to an estimate for the same game context.

### Decision

A `FixedDropEstimateTimeline` represents **currently accepted canonical** fixed-probability knowledge for one stable
`DropRelationship` and one `AcquisitionKnowledgeScope`. It is a pure domain structure, not a database revision.
Its `FixedDropEstimateSnapshot` records include application-owned `DropEstimateId`, a validated `FixedBernoulliModel`,
optional effective-from `GamePatch`, canonical `DropEstimateProvenance` (`OFFICIAL`, `MEASURED`,
`COMMUNITY_ESTIMATE`), nonblank evidence reference, and curator review time.

Snapshots describe accepted **game-patch change points**. Only one accepted snapshot is allowed at each change
point; each snapshot identity is unique inside its timeline. Patchless resolution becomes `UNKNOWN` if the timeline
contains any patch change points; an earlier patch without a baseline or preceding snapshot also resolves `UNKNOWN`.
Scope applicability uses the established strict Normal/Ruthless, CompatibilityVersion, and optional league semantics.

V0.1's only supported counted attempt is `CompletedBossEncounterAttempt(AcquisitionSourceId)`, meaning **one successfully
completed encounter at that source**, including multi-boss encounters. The timeline checks that this source matches
the referenced DropRelationship. No one-value attempt enum, free-text attempt-definition field, or generic attempt
type hierarchy is introduced. The UI can obtain human-readable encounter labels from canonical source knowledge.

Only genuinely supported fixed probabilities without unsupported modifier-dependent conditions belong in these
timelines. The data type enforces the probability range and structural applicability but cannot independently verify
the underlying evidence or prove that an encounter's drop mechanics are unconditional. That remains curator
responsibility. Unsupported conditional probabilities are never approximated as fixed.

### Deliberately Deferred Enforcement

**Game applicability** and **curator authority** are separate histories. A past game-patch estimate can remain the
accepted answer for that old patch, whereas a replaced or corrected curator estimate for the same patch must not
remain simultaneously active. Archive/activation semantics, transactional replacement, and uniqueness across
currently authoritative estimate records belong to the next persistence PR.

Overlapping broad and league-specific scopes must not be silently resolved by repository iteration order; the later
application read model must enforce deliberate unambiguous selection. Estimating probability also requires separately
resolving source availability and `DropRelationship` `CAN_DROP` status; a relationship identity alone is not evidence
that the item currently drops there.

Personal overrides, probability-condition engines, resource-derived attempts, JPA mappings, and calculated acquisition
read models remain outside this domain-only increment.

---

## 14. Persisting Fixed Drop Estimates Separates Game Change Points from Curator Corrections

### Context

Accepted `FixedDropEstimateTimeline` snapshots are game-patch change points, not revisions of curator decisions. The same
historical patch can receive better curated evidence, but replacing that evidence must not erase earlier records or
invalidate still-accepted estimates for other patches. The application must not select two simultaneously authoritative
estimates for the same exact probability key.

### Decision

Persist one `fixed_drop_estimate` row per immutable curated `DropEstimateId` with foreign keys to the stable
`DropRelationshipId` and optional `LeagueDefinitionId`. Each row contains the `FIXED_BERNOULLI` model and numeric
probability, version/ruleset/league scope, optional effective patch and suffix, provenance, nonblank evidence reference,
and review timestamp. The encounter-attempt source is reconstructed from the referenced relationship, not duplicated.

Curator publication state is `ACTIVE` or `SUPERSEDED`, separate from the immutable domain snapshot, with publication
and supersession timestamps. The semantic fields of an existing row are not updated. Publishing a replacement assigns a
new ID, conditionally supersedes only the expected active estimate at the **same exact change-point key**, then inserts
the replacement in one transaction. An absent expected ID declares an expected empty active slot. Competing operations
are explicit conflicts without automatic retry; no explicit pessimistic parent-row lock is required.

PostgreSQL enforces **at most one ACTIVE row** per exact
`(DropRelationship, CompatibilityVersion, Ruleset, optional LeagueDefinition, FIXED_BERNOULLI, optional effective patch)`
key. Its partial unique index uses `NULLS NOT DISTINCT` so that nullable league, baseline-patch, and patch-suffix
components cannot admit duplicate active rows. Multiple past `SUPERSEDED` records remain permitted. Other database
constraints enforce real foreign keys, valid `0 < p <= 1`, and supported provenance/model/lifecycle values.

A read scoped to the **exact** `DropRelationshipId + AcquisitionKnowledgeScope` reconstructs one
`FixedDropEstimateTimeline` from ACTIVE change-point records, or returns null if none exist. No historical/superseded
evidence is selected as active, and no implicit league-specific precedence or best-match selection is introduced.

### Explicit Deferrals

Overlapping broad and league-specific estimates can both be valid exact-key records and require an explicit
cross-scope ambiguity/selection policy in the later acquisition read model. Versioned `DropRelationship` CAN_DROP and
source-availability knowledge must be resolved separately before using an estimate in a calculator.

A known invalidation with **no replacement fixed probability** cannot be faithfully represented by a timeline
containing only numeric snapshots; a dedicated withdrawal change point was implemented in §19 before the combined
Acquisition read model. Previous accepted numeric change points remain available for historical game patches.

Conditional/quantity-dependent probability models, personal overrides, curator draft UI, and resource-derived attempt
counts are not implemented here.

---

## 15. Persisting Accepted DropRelationship Knowledge Without Curator Revision History

### Context

A durable DropRelationship identity is not evidence of a currently applicable CAN_DROP state. The acquisition read model
must reconstruct actual curated CAN_DROP/CANNOT_DROP game-version change points, not infer applicability from an
estimate or the mere existence of a source-to-Unique association.

### Decision

The `drop_relationship_knowledge` table introduced in V014 stores **currently accepted** authored states at exact
`(DropRelationshipId, CompatibilityVersion, Ruleset, optional LeagueDefinitionId, optional effective GamePatch)` keys.
The table has FK protection to both the relationship and optional league identity, supported state/version/patch
checks, and a unique index using `NULLS NOT DISTINCT` to prevent duplicate league-independent or baseline facts.

An existing accepted fact may be corrected **in place** at the same key; a newly authored game-patch change point
creates a separate row. The persisted revision starts at zero and increments on every accepted correction, even if
the replacement state is the same. Commands require the curator's expected revision for correction and null for
first creation. A conditional SQL UPDATE and the database unique index protect both update and insert races.
Losing commands raise an explicit conflict without automatic retry. A database row ID is a persistence detail,
not an application-owned `DropRelationshipSnapshot` or curator-revision identity.

Reconstruction reads the exact requested scope and maps accepted records back into the existing
`DropRelationshipTimeline`; empty knowledge returns no timeline, never a CANNOT_DROP claim. Existing strict
CompatibilityVersion/Ruleset/optional LeagueDefinition and effective-patch resolution remains in domain code.

### Deliberate Deferrals

These accepted state corrections are not a curator revision/audit log, unlike the immutable DropEstimate evidence
records. If auditability is later required for relationship curation, introduce an explicit immutable correction
history at that time; do not conflate it with historical **game** states already preserved by effective-patch points.

Source availability and encounter metadata remain a separate source-knowledge persistence increment. Broad and
league-specific relationship scopes can overlap; cross-scope resolution/ambiguity remains the responsibility of
the later Acquisition read model. No source availability, probability estimate, calculator or UI is derived from
these identities or facts alone.

---

## 16. Persisted BossDefinition Identity and Conservative Curator Duplicate Protection

### Context

V2.1.2 separates a stable boss identity from any boss encounter, roster, arena, or display label. The first
BossDefinition persistence increment must provide stable foreign-key targets for later multi-boss encounter knowledge,
without turning a human-facing name into identity or allowing ordinary curator errors to create obvious duplicates.

### Decision

Flyway V015 persists `boss_definition(id UUID PRIMARY KEY, display_name TEXT NOT NULL)` and rejects blank SQL
display text. `BossDefinitionId.generate()` remains the application-owned identity generator. No provider or arena
identifier is used as the key. Normal creation is an idempotent insert on an absent ID: replay with the exact same
display metadata succeeds, but a changed name for an existing ID is an explicit conflict. Rename/edit is **not**
implemented by silently upserting; it requires a future deliberate operation.

A small curator-facing `BossDefinitionService` rejects likely duplicate names under **distinct** IDs after
Unicode NFKC compatibility normalization, trimming/collapsing Unicode whitespace, and locale-independent case
normalization. This is a conservative human-review guard, not a global claim that two distinct bosses can never
share the same displayed name. Provider aliases, translations, name history, and genuinely distinct same-name
entities require explicit curator resolution. Display names remain metadata, and no database
`UNIQUE(display_name)` or normalized-name natural key is established.

The normal service checks an existing ID first, reviews potential name collisions, and uses atomic
`INSERT ... ON CONFLICT (id) DO NOTHING` to protect immutable identity metadata even across competing insertions.
The application write is transactional and the adapter also has a defensive transaction boundary.

### Explicit Limitations and Deferrals

The current small manually curated corpus permits an in-memory comparison across persisted boss display names;
introduce a scalable normalized-name lookup only when concrete curator volume requires it. The application-level
name guard does **not** serialize simultaneous creations with *different IDs* but equivalent normalized names.
The primary-key constraint protects the same ID, not different IDs with matching display metadata. Revisit stronger
reservation/coordination if multi-curator concurrent creation becomes a supported workflow. Do not claim that the
absence of a database name-uniqueness constraint is a guarantee of semantic non-duplication.

Persisting the boss identity does not create an AcquisitionSource, assign it exactly one boss, or imply the
source's availability. Versioned encounter knowledge and boss participation are persisted separately in §17.

---

## 17. Persisting Complete BossEncounter Knowledge and Its Participant Set

### Context

An AcquisitionSource is a stable identity, not proof of current availability or a fixed association with one boss.
The already implemented BossEncounterKnowledgeTimeline represents complete historical source/encounter snapshots,
including labels, AVAILABLE/UNAVAILABLE authoring and fixed or configuration-dependent participation.

### Decision

Flyway V016 introduces `boss_encounter_knowledge`: one **currently accepted** complete snapshot per exact
`(AcquisitionSourceId, CompatibilityVersion, Ruleset, optional LeagueDefinitionId, optional effective GamePatch)`.
It has source/league foreign keys, allowed availability and participation-kind constraints, nonblank display labels,
nonnegative revisions and a `NULLS NOT DISTINCT` index covering baseline and league-independent change points.
The row UUID is persistence-only; curated source identity and knowledge scope remain the domain keys.

`boss_encounter_participant` stores a set of `BossDefinitionId` references for each fixed snapshot, with both
foreign keys and a composite primary key disallowing duplicate participants. One snapshot can reference multiple
bosses, and one boss can occur in multiple different source snapshots. A `CONFIGURATION_DEPENDENT` snapshot uses
no participant rows: it is **not** an empty fixed roster. The domain application service validates FIXED nonempty
rosters and existing boss references; the database does not use cross-table deferred triggers to enforce the
presence/absence of child rows. The read adapter explicitly rejects inconsistent persisted roster shapes.

A first publication inserts the parent and any fixed participants together; a curator correction conditionally
increments the accepted parent's revision, then **replaces** that exact snapshot's full participant set, all in
one transaction. Lost races are conflicts, not automatic retries. Other game-patch rows and source history remain
unchanged. Corrections of the same game-patch change point update the accepted state **in place** (no separate
curator edit audit records), matching the earlier DropRelationshipKnowledge decision.

A versioned exact-scope query reconstructs the existing BossEncounterKnowledgeTimeline, or null when no accepted
knowledge exists. The Spring Data JPA repository uses a **single JPQL collection fetch join** to load all parent
snapshots and participants in one SQL SELECT. Under READ COMMITTED, that prevents combining a parent revision from
one statement with participant rows from a different statement. Native SQL within Spring Data repositories is
retained for conditional revision updates, exact NULL-aware key lookups and atomic participant replacement.
Modifying queries clear the persistence context to avoid stale JPA collection state after native DML.
Resolution of patch order, ambiguous scope and UNKNOWN contexts stays in the pure domain/read-model boundary.

### Explicit Limits

Source availability does not infer source→Unique drop applicability, probability, player attempt inventory or
immediate access. Cross-scope precedence, structured access rules, area/arena and provider metadata, encounter
source-kind taxonomy, editor UI, curator audit history and database trigger enforcement of roster cardinality
remain outside this increment. Different scope timelines can overlap; later read-model composition must not
arbitrarily select a winner.

---

## 18. Persistence Technology Escalation Policy

Use the least-specialized persistence mechanism that **correctly and reliably implements the requirements**:

1. **Spring Data JPA first.** Prefer typed entities, repository queries, entity relationships/element collections
   and JPQL fetches when they express the domain and transactional requirements without compromising correctness.
2. **Spring Data JPA with native SQL where needed.** Use explicit PostgreSQL capabilities such as
   `IS NOT DISTINCT FROM`, conditional compare-and-set updates, atomic conflict handling and indexed null semantics
   when JPA/JPQL cannot express the required guarantees equally well.
3. **Direct JDBC only as a justified exception.** Introduce `JdbcTemplate`/`NamedParameterJdbcTemplate` only when
   the JPA repository + native-query approaches genuinely cannot meet the requirements appropriately. Document
   the specific missing capability and preserve domain/persistence separation.

This is a **capability/correctness order**, not a license to weaken an invariant for simpler code or to use native
queries merely as a convenience. Test concurrency, complete-row consistency and rollback at the database boundary.
Do not mix direct JDBC with JPA by default; native bulk updates can bypass the JPA persistence context, so flush
and clear managed state appropriately before reconstructing affected entity collections.

---

## 19. Accepted Fixed-Probability Withdrawal Change Points

### Context

A published fixed estimate can cease to be trustworthy after an official game patch, without a known replacement
number. Removing a newer numeric snapshot would allow an older numeric rate to carry forward incorrectly. A withdrawal
is **not** evidence that a relationship CANNOT_DROP, a source is UNAVAILABLE, or that the drop rate is zero.

### Decision

The accepted `FixedDropEstimateTimeline` now contains a discriminated sequence of
`FixedProbabilityChangePoint.Known(FixedDropEstimateSnapshot)` and
`FixedProbabilityChangePoint.Withdrawn(FixedProbabilityWithdrawal)` values. Numeric snapshots retain strictly
validated `FixedBernoulliModel` values. An explicit withdrawal has its own application-owned
`FixedProbabilityWithdrawalId`, effective game patch (or version baseline), mandatory nonblank reason/evidence
reference, and curator review timestamp; it has no numeric model. `FixedProbabilityPublicationId` distinguishes an
estimate identity from a withdrawal identity, so the expected-active token can guard either publication kind.

The timeline preserves normal strict source/scope/version/patch applicability and the accepted game-change-point
ordering. Its latest applicable numeric record resolves normally; its latest applicable withdrawal resolves
`FixedDropEstimateResolution.Unknown`, not an invented zero, and never falls back to an earlier rate after the
withdrawal patch. A withdrawal-only accepted timeline is valid. A missing exact-scope timeline still returns null.
Absent patch precision stays UNKNOWN when any patch-specific change point exists.

Flyway V017 extends the existing immutable `fixed_drop_estimate` publication table with a
`knowledge_kind` discriminator (`ESTIMATE` / `WITHDRAWAL`) and nullable `withdrawal_reason`. Existing rows
are `ESTIMATE` records; numeric/provenance columns become nullable **only for withdrawal rows**. A cross-column
database constraint requires a validated numeric probability and provenance exclusively for ESTIMATE rows, and
a nonblank reason but no numeric/provenance fields for WITHDRAWAL rows. Evidence, timestamps and lifecycle remain
required for both. The model category remains `FIXED_BERNOULLI`; `knowledge_kind` identifies the explicit
knowledge boundary for this fixed-model category.

Both kinds share the existing `ACTIVE`-only unique exact-key index (`NULLS NOT DISTINCT`) and
`ACTIVE`/`SUPERSEDED` lifecycle. Publication inserts an immutable row, conditionally superseding the expected
**type-checked active publication ID** at exactly the same relationship/scope/effective-patch key in one transaction.
An accepted game-patch withdrawal does **not** supersede a different earlier patch's active row; instead it becomes
the latest applicable accepted game fact for newer evaluation contexts. A curator correction *at the same key*
supersedes its previous active record but retains that evidence for audit. Races report conflicts without automatic
retries. Reinstatement publishes a new fixed estimate at the same key or at a later game change point.

### Persistence and Scope

Spring Data JPA owns entity mapping and accepted-record reads, with narrowly targeted native PostgreSQL queries
for compare-and-set supersession and immutable publication. Direct JDBC is not required in production. Database
constraint tests may use JDBC to exercise invalid raw rows.

Withdrawal does not independently establish or revoke drop relationship applicability, source availability,
personal attempts, or an alternative probability model. The combined Acquisition read model will resolve those
dimensions independently, without silently preferring league-specific facts or assuming probability from identity.

---

## 20. Acquisition Read-Model Scope Ambiguity Is Explicit

### Context

Canonical source availability, source-to-Unique drop applicability, and fixed-probability knowledge have separate
accepted timelines. Each timeline is scoped by CompatibilityVersion, Ruleset, and an optional LeagueDefinitionId.
A null league constraint means the knowledge is genuinely league-independent, not a low-priority default.
A league-independent timeline and a timeline for the selected league may therefore both be authored and applicable.
Earlier persistence increments intentionally did not establish a cross-scope precedence rule.

### Decision

The Acquisition read model must resolve each knowledge dimension **independently** for the same explicit
EvaluationContext; source availability does not imply CAN_DROP, and neither implies a usable probability.

For a selected AccountEvaluation, CompatibilityVersion, Ruleset and LeagueDefinitionId are known. For one canonical
source or relationship and one knowledge dimension, examine the exact league-independent scope and the exact
selected-league scope:

- **Neither timeline exists:** knowledge is `Missing`, not an authored negative fact.
- **Exactly one timeline exists:** select it and use its existing game-patch resolution. That resolution may itself
  be `Unknown` because the EvaluationContext lacks patch precision or applicable accepted change points.
- **Both timelines exist:** knowledge is explicitly `Ambiguous`. Neither overrides the other, even when their
  current resolved values agree or one would resolve `Unknown`; do not use database row order, league-specific
  preference, or fallback to choose an authority.

Selection happens before resolving a timeline's game-patch state. A source-availability timeline may use one scope
while its drop-relationship timeline uses another; that is not a conflict between dimensions. Any ambiguity belongs
to the affected dimension and must not be hidden by the other dimensions' results. A durable DropRelationship
identity alone never establishes current CAN_DROP applicability.

The first Acquisition read-model increment covers relationship discovery, source availability, and drop
applicability. It reuses the existing exact-scope persistence queries and domain timeline resolvers; it adds no
new tables or Flyway migration. Fixed-probability, withdrawal, and personalized AvailableAttempts composition
will follow as a separate increment and must apply the same no-implicit-precedence principle.

### Deliberate Limits and Revisit Triggers

This is a conservative V0.1 ambiguity policy, **not** a general rule that league-specific knowledge always overrides
league-independent knowledge. A future explicit override/precedence model would require a separate product decision,
clear authority semantics, and tests before changing this rule.

The current selected-account query has a known LeagueDefinitionId. Future queries that permit an absent or
uncertain league identity must preserve that insufficient precision as unknown rather than treating a broad-scope
lookup as evidence that no league-specific knowledge could apply. Existing strict game-patch resolution remains
unchanged. Query indexing and broader batching can be revisited with measured data volume or performance needs.

---

## 21. Actionable Fixed-Probability Read-Model Composition

### Context

The Acquisition read model already discovers source-to-Unique relationships and resolves source availability and
drop applicability independently. Accepted fixed-probability timelines, explicit withdrawals, and manually recorded
AvailableAttempts exist as separate authorities. The MVP's Bernoulli calculator needs these facts composed without
conflating accepted canonical estimates, personal resources, or proof of current farming eligibility.

### Decision

For each known DropRelationship, preserve independent scoped source-availability, relationship-applicability, and
fixed-probability resolutions. Probability uses the same league-independent versus selected-league ambiguity policy
as §20, including ambiguity when accepted values agree. Timeline patch resolution and withdrawal semantics from
§19 remain authoritative: a withdrawn estimate resolves UNKNOWN and an older numeric rate is never reused afterward.

Only produce an **actionable fixed-probability calculation** when source availability resolves to AVAILABLE,
the relationship resolves to CAN_DROP, and exactly one selected probability scope resolves to an accepted numeric
FixedBernoulliModel. Missing, unknown, does-not-apply, ambiguous or known negative prerequisites block that
calculation, **without hiding the independent canonical facts or any recorded attempt count**. A numeric estimate
alone does not establish that its encounter is currently available or that it can drop the Unique.

The existing selected AccountEvaluation supplies the EvaluationContext for canonical knowledge and the
AccountContextId for personalized attempts. AvailableAttempts is looked up by AccountContextId and
AcquisitionSourceId, so a count is shared across relationships of one source but never transferred across accounts.
Absence of a record is distinct from an explicitly recorded zero. When attempts are unrecorded, the probability
within personal available attempts is omitted, but model-only expected attempts and canonical thresholds remain
calculable. Zero recorded attempts produces zero cumulative chance.

V0.1 exposes the supported probability of at least one success within recorded attempts, expected attempts until
first success, and whole-attempt thresholds for 50%, 75%, 90%, 95% and 99%. These results reuse the pure
FixedBernoulliModel and its source-defined CompletedBossEncounterAttempt semantics. When valid extreme probability
values require more than Int.MAX_VALUE attempts or yield a nonfinite Double expectation, represent the metric as
unsupported/absent instead of throwing away otherwise valid knowledge or inventing a finite result. The supported
threshold targets are fixed for this slice; arbitrary 100% target requests are not introduced.

The read model stays an in-memory, read-only composition under its existing repeatable-read transaction. No
additional persisted projection, Flyway migration or alternative canonical probability authority is introduced.

### Non-scope

Personal probability overrides, conditional/modifier-dependent probability models, automatic attempt derivation,
resource reservations, cross-source probability aggregation, and UI/controller work are deferred. The selected
accepted withdrawal currently resolves as Unknown rather than carrying a separate displayable reason; richer
withdrawal explanations can be revisited for the Acquisition browser flow.

---

## 22. Persistent Development-Only Vertical Slice Fixture

### Context

Vertical Slice 1 requires the canonical A/B/C → Build X → Boss Y scenario to exist as ordinary PostgreSQL-backed
application data before the server-rendered browser views are implemented. The current Catalog deliberately has no
persisted Unique display names; provider ingestion and real metadata belong to later milestones.

### Decision

Create deterministic synthetic application-owned IDs for three UniqueDefinitions, a league/account, Build X's
activated revision and requirement groups, Boss Y's definition/source, source-to-C relationship, accepted source and
drop applicability knowledge, a 10% fixed Bernoulli estimate, and 12 available completed-encounter attempts.
Baseline compatibility version 3.30 and NORMAL ruleset are test context, not assertions about live PoE mechanics.
A and B initially have one owned copy each; sparse ManualOwnership represents C=0 by the absence of a row.

The initializer composes existing application services and narrowly scoped, module-owned development access
points instead of referencing another module's persistence adapters. These access points use existing persistence
operations inside each owning module, preserving Spring Modulith's public API boundaries. Coordination lives in
the application root, and no production feature depends on development access points. We do not add parallel fixture
domain types, provider identities, general-purpose curation APIs, or database migrations.

The application initializer remains a callable transactional Spring bean without autonomous startup behavior.
Automatic loading remains restricted to an ApplicationRunner enabled by **both** the `dev` Spring profile and
`poe-companion.dev-fixture.enabled=true`. The initializer and all four module-owned `Development*Access` components
are registered only when the opt-in property is `true`; no profile restriction is placed on these five beans
so integration tests can set the property without `dev` and invoke initialization explicitly. Default startup
registers none of these beans and performs no synthetic inserts. Tests cover all four configuration combinations;
standard CI needs no globally enabled development profile or environment variables.

The first initialization is atomic. A subsequent run verifies the existing canonical scaffold rather than
creating duplicate identities or silently repairing changes. Player-editable ownership, AvailableAttempts, and an
already-selected AccountContext are preserved. An inconsistent/partial synthetic fixture is an explicit failure,
not a reason to overwrite data. A/B/C display labels remain development-only aliases until Catalog metadata is
designed; synthetic evidence labels must not be presented as real measured PoE facts.

### Scope Limits

The fixture isn't a production Flyway data migration or a replacement for future provider ingestion/curator UI.
No schema changes, generic seed framework, browser navigation, or automatic reset are introduced. Browser-level
flow tests will follow when the relevant views exist.

---

## 23. Initial Read-Only Unique Collection Browser

### Context

Milestone 5's first browser flow needs to show Catalog identities and the selected AccountContext's current owned
quantities without importing PoE Wiki data or prematurely creating Catalog metadata tables. Catalog's stable
UniqueDefinition identities already exist in PostgreSQL, but names, tiers, item classes, availability and other
enrichment belong to Milestone 7. The current development fixture explicitly supplies only A/B/C display aliases.

### Decision

Expose normal Catalog-owned read operations to enumerate all known UniqueDefinitions and look up one identity.
Keep Catalog persistence private to the Catalog module; the existing DevelopmentCatalogAccess remains exclusively
a development-bootstrap entry point. A small application-root browser read model composes Catalog identities,
AccountEvaluationService's selected AccountContext and the single CurrentOwnership projection for that account.
The composition uses a read-only repeatable-read transaction, returns presentation DTOs rather than new domain
facts, and never queries ownership once per Unique.

The initial server-rendered URLs are `/uniques` and `/uniques/{id}`. List filters are **All / Owned / Missing**,
where Owned means quantity > 0 and Missing means quantity = 0 in the selected AccountContext. These are not
ItemAvailability judgments. Without a selection, all catalog identities remain browseable but ownership is
unknown, not zero; Owned and Missing navigation is disabled, and directly requesting those filtered states without
a selected account is rejected. Invalid filter and malformed UUID inputs produce HTTP 400, unknown existing-format
Unique IDs produce HTTP 404. An empty catalog renders an explicit empty state without initializing fixture data.

The presentation may render the existing development-only A/B/C alias map, clearly labelled as synthetic.
Unlabelled catalog identities display `Unique (name unavailable)` alongside their stable UUID; neither label is
saved to Catalog or treated as a provider identity. This allows ordinary non-fixture identities to appear correctly
even before catalog ingestion. Real names, base types, tiers, ItemAvailability and name-based search await
Milestone 7. Basic All/Owned/Missing filtering works independently of that metadata.

Use the existing Spring MVC, Thymeleaf, Spring Security and CSS stack. Controllers handle only routing, input
validation and view dispatch, while read-model services assemble consistent display data. Tests exercise rendered
HTTP responses with authenticated/anonymous callers against Testcontainers PostgreSQL, including ownership
changes, missing account selection and unknown Unique identities.

### Non-scope

No ownership-editing controller, Build/Acquisition associations or navigation, probability presentation, SPA,
new schema, real provider import, tier filters, generic presentation mapper, or full browser automation.

---

## 24. Browser and Module Maintainability Conventions

### Context

Milestone 5 introduces HTTP controllers, server-rendered views, presentation read models and development-only
fixture helpers. As the number of screens and domain types grows, intentional package boundaries and a tracked
temporary-code lifecycle are more useful than speculative general-purpose abstractions.

These are agreed implementation conventions, not additions to the frozen V0.1 product scope.

### HTTP and presentation

- Write Spring MVC + Thymeleaf HTML controllers by hand. Their routes use natural product paths (for example,
  `/uniques`, `/builds`, `/acquisitions`), not `/api` and not a browser-URL version prefix. If a real JSON API
  consumer emerges, decide its `/api` prefix, versioning, OpenAPI contract, and possible interface generation then.
- Controllers own routing, typed path/query parameter interpretation, HTTP status mapping, and view dispatch.
  Application services/read models own business rules, module composition and transaction boundaries; templates
  only render supplied presentation data. Do not bind browser forms directly to JPA entities.
- Define invalid/missing parameter behavior deliberately (including omitted vs explicitly blank query values).
  Malformed requests normally yield HTTP 400; unknown resource IDs yield HTTP 404. Protect authenticated browser
  routes with existing Spring Security. Future writes use validated commands, CSRF protection and
  Post/Redirect/Get, not state-mutating GET requests.
- Group small, closely related Kotlin presentation DTOs in one dedicated file (currently
  `UniqueBrowserViewModels.kt`) instead of mixing them into orchestration code or insisting on one file per type.
  Split further only as responsibilities genuinely diverge.
- Keep minimal server-rendered templates. When a second product page warrants it, introduce persistent navigation
  and a shared Thymeleaf header/logout fragment; don't create links to nonexistent screens. Group CSS by
  shared/feature responsibilities with section comments now, and split CSS files only once reuse/complexity proves
  where boundaries belong. No new frontend toolchain is justified by file length alone.

### Module and package organization

- Preserve the existing Spring Modulith modules: Catalog, League, Account, Build, Acquisition, etc. Inside
  **large existing modules**, prefer cohesive domain-capability subpackages (for example encounter, relationship,
  probability, attempts) over generic `model/`, `service/`, or `view/` buckets.
- A module's root package remains the straightforward public API surface; move **internal** implementation types
  into internal subpackages. Before each move, check cross-module consumers: a type moved out of the root is not
  automatically exposed across Spring Modulith boundaries. Use named interfaces only when a real cross-module
  API merits one. Retain/update `ApplicationModulesTests`, imports, and integration tests.
- Don't reorganize small modules just for symmetry. A distinct Browser module is warranted for the established
  incoming adapter and its enforceable dependency boundaries; see the superseding decision in §29. Acquisition's
  large root remains a candidate for a dedicated behavior-preserving package reorganization.
- Before adding any service, adapter, interface or access wrapper, first decide whether an **existing** service
  can and should own that behavior without blurring responsibility. Introduce a new abstraction only when it
  materially improves cohesion, module boundaries, reuse, testability or clarity. A single implementation does
  not automatically require an interface, and duplicated forwarding methods aren't automatically justified.

### Development-only fixture lifecycle

- The fixture startup **runner** requires both the `dev` profile and the opt-in property. The initializer
  and all four module-owned `Development*Access` beans are **conditionally registered by property alone**.
  This intentionally excludes `@Profile("dev")` from those five beans so integration tests can instantiate them
  and call the initializer directly without automatic startup initialization. Disabled beans remain compiled in the
  artifact; no separate source set/module is needed. Existing initialization and idempotency semantics are unchanged.
- Track temporary production-facing dependencies on synthetic labels explicitly. In Milestone 7, audit and
  remove/replace `DevelopmentFixtureIds.uniqueLabels`, browser read-model label fallback,
  `UniqueBrowserEntry.developmentLabel`, and synthetic-label branching in `templates/uniques/list.html`
  and `templates/uniques/detail.html`. Also check any subsequently added Build/Acquisition browser screens.
  Replace presentation fallbacks with real Catalog metadata; do **not** imply a nameless Unique is absent
  from the catalog. Keeping useful synthetic fixtures for automated tests and local development is a separate
  choice from keeping those shortcuts in the normal UI.
- Revisit the four development access wrappers when real curation/import commands make them unnecessary.
  Never evade Spring Modulith verification by reaching into another module's persistence packages.

### Execution

The Unique browser cleanup was completed in PR #70. Conditional fixture-bean registration is a separate,
behavior-preserving Milestone 5 cleanup. Acquisition's internal package organization remains a distinct
follow-up requiring a dependency review and Spring Modulith verification.

---

## 25. Acquisition Encounter-Knowledge Internal Package Boundary

### Context

Acquisition has a broad root package containing externally consumed domain/application API types and internal
persistence mechanics. Moving every encounter/probability-related class into nested packages would accidentally
hide module API types behind Spring Modulith's default internal-package boundary. Leaving all implementation
details at the root also makes the module unnecessarily difficult to navigate.

### Decision

Start the behavior-preserving, domain-capability-oriented reorganization with encounter-knowledge internals:

- Move `BossEncounterKnowledgePersistencePort` and `CorruptBossEncounterKnowledgeException` to
  `acquisition.encounter`.
- Move `BossEncounterKnowledgeEntity`, `BossEncounterParticipationKind`,
  `BossEncounterKnowledgePersistenceAdapter`, and `SpringDataBossEncounterKnowledgeRepository` to
  `acquisition.encounter.persistence`.
- Keep `BossEncounterKnowledgeService`, `BossEncounterKnowledgeTimeline`, snapshots/resolutions,
  participation model, identities, and externally used conflict types in the Acquisition **root package**.
  No new Spring Modulith application module or named interface is needed for these six internal moves.
- The encounter persistence adapter may continue to use Acquisition's existing root-level/persistence
  boss/source identity repositories internally; domain capabilities within one Modulith module do not require
  artificial service wrappers to communicate.
- Update imports in the service and existing tests without changing public APIs, business rules, transactions,
  JPA entity/table mappings or queries, database migrations, or production behavior. Use existing PostgreSQL
  encounter/acquisition integration tests and `ApplicationModulesTests` to validate entity/repository discovery
  and module boundaries.

Follow-up internal capability migrations (relationship, probability, attempts) are **not** implied by this
first move and should be proposed only when their concrete dependency structure justifies them.

---

## 26. Read-Only Build Browser, Explainable Readiness and Shared Navigation

### Context

Milestone 5 extends the authenticated Unique Collection with Build browsing, using real persisted BuildArchetypes,
BuildVariants and compatible activated BuildVariantRevisions. The V0.1 Build browser must explain readiness using
the existing ENABLING/CORE/UPGRADE and ALL/ANY semantics, without inventing scores, copying ownership rules into
the presentation layer or depending on optional development fixture beans.

### Decision

- Expose authenticated `GET /builds` and `GET /builds/{BuildVariantId UUID}` as Spring MVC/Thymeleaf pages.
  Browse known variants from persistent Build identity regardless of whether their readiness is evaluable.
  List evaluated variants grouped by `UNIQUE_READY`, `PARTIAL`, `BLOCKED`, and separate unavailable outcomes
  (no account selected / no compatible active revision). A malformed UUID is HTTP 400 and unknown variant is 404;
  no compatible active revision is **not** a missing or blocked Unique requirement.
- Add a narrowly scoped `BuildCatalogQueryService` in Build's public root package, backed by existing Build-owned
  archetype/variant persistence adapters. Keep `BuildReadinessService` responsible for readiness and offer a
  batch evaluation API that selects one account and fetches CurrentOwnership once for the whole list.
  Compose browser DTOs at the application root in a read-only repeatable-read transaction; do not use
  `DevelopmentBuildAccess` in normal browsing, cross into another module's persistence, or implement a second
  readiness algorithm in the controller.
- Detail renders the active compatible revision, requirement importance and logic, each owned/required quantity,
  group-level satisfaction, and Unique detail links. `ANY` groups can be satisfied even when other alternatives
  are individually missing. UPGRADE failures don't affect primary readiness. A zero-group or upgrade-only revision
  shows **No required Uniques** while its domain readiness remains `UNIQUE_READY`. An absent account or revision
  produces no fabricated ownership information.
- Share a single temporary `UniqueDisplayLabels` helper with the Unique Collection for synthetic A/B/C aliases;
  non-fixture identities remain `Unique (name unavailable)` with the UUID available. Replace the helper with
  real Catalog metadata in Milestone 7; avoid duplicating aliases in other browser models.
- Add a shared Thymeleaf header fragment (Home, Uniques, Builds, signed-in user and CSRF-protected logout) to
  all currently implemented pages. Navigation advertises only working destinations. Retain existing CSS with
  clearly marked feature sections and responsive behavior.
- BuildReference persists nowhere yet. A truthful unavailable references section in this read-only browser
  does not satisfy V0.1's eventual BuildReference persistence/curation requirement: implement and track that
  separately. Likewise, missing Unique → acquisition navigation awaits later Milestone 5 increments.

### Exclusions

No Build editor, revision activation UI, persistence migration, external metadata ingestion, calculated build
strength/DPS, advanced searching/pagination, new web Modulith module or unrelated domain package reshuffle.
Review touched files for cohesion continuously; move internal classes only where a small related change clearly
improves the structure. Larger capability migrations remain separate, reviewable refactors.

### Verification

Authenticated and anonymous HTTP tests against PostgreSQL cover Build X's PARTIAL state, the A/B/C ownership
transition to UNIQUE_READY, BLOCKED enabling groups, ANY alternatives, multi-copy requirements, UPGRADE-only and
zero-group revisions, missing account/compatible revision, absent catalogue, malformed/unknown IDs, synthetic
labels and navigation. Existing Build domain and Spring Modulith tests continue to apply.

---

## 27. Acquisition Source Browser, Contextual Knowledge and Read-Only Attempt Previews

### Context

Milestone 5 adds the missing source-oriented half of the authenticated Unique → Build → Acquisition browser
journey. Acquisition already owns accepted encounter availability, source-to-Unique CAN_DROP/CANNOT_DROP knowledge,
fixed probability histories and per-account available attempts. Stable source identity is independent of the
relationships attached to it; a relationship identity never proves current availability or a numeric drop chance.

### Decision

- Add authenticated read-only `GET /acquisitions` and `GET /acquisitions/{AcquisitionSourceId UUID}`. A source
  remains visible even with no relationships, no account selection or no accepted contextual encounter knowledge.
  The selected account governs contextual availability, relationship applicability and accepted fixed estimates.
  Invalid UUIDs and attempt parameters return HTTP 400; unknown stable source identity returns HTTP 404.
- Extend the existing Acquisition-owned `AcquisitionReadModelService` with `findSources()` and
  `findForSource(sourceId, previewAttempts?)`. Add only source enumeration and source-oriented relationship
  queries to existing persistence adapters. Reuse the existing shared per-relationship knowledge resolution
  and eligibility calculation, never calculate directly from a relationship identity or estimate alone.
  Keep the source/relationship/estimate scope outcomes separate; never silently select an ambiguous scope.
- The optional `?attempts=N` parameter is a **read-only preview** with `N` a nonnegative 32-bit integer.
  An omitted parameter means use the saved AvailableAttempts record; explicit zero is meaningful. The domain
  method substitutes a transient count solely for the calculation while returning the actual saved count
  independently. It never writes or resets player-editable data.
- Render source label and current availability from accepted encounter knowledge, relationships independently
  with their per-Unique applicability and estimate state, and the fixed probability calculator only when
  encounter availability, CAN_DROP and an applicable accepted estimate are all affirmative. Show per-attempt
  probability, provenance, evidence reference, review timestamp, at-least-one cumulative probability for
  the effective attempt count, expected attempts and the 50/75/90/95/99-percent whole-attempt thresholds.
  Missing attempts mean not recorded, not zero; withdrawn/unknown/ambiguous scopes do not yield fabricated
  actionability. A known estimate can be displayed as independent knowledge without asserting eligibility.
- Compose simple Thymeleaf presentation DTOs at the application root using the existing shared Unique display
  label helper, and add Acquisition to the shared navigation only when its pages work. No hard-coded Boss Y
  display label or duplicate browser probability formulas.
- Apply continuous package-location review. The touched source/relationship persistence adapters remain inside
  Acquisition's existing internal persistence package for this increment because their responsibilities
  are cohesive there and relocating them plus unrelated repositories would obscure the feature. Keep
  domain-facing Acquisition read APIs in the module root and never cross another module's persistence boundary.

### Exclusions

No saved attempts editor, personal drop-rate overrides, Build/Unique → Acquisition navigation, ownership editor,
provider ingestion, unsupported conditional effective-probability models or database migrations. Actual drop-rate
claims for synthetic fixtures must remain clearly identified as development-only evidence, not real PoE data.

### Verification

HTTP integration tests cover the Boss Y fixture (12 attempts, approximately 71.8-percent cumulative chance,
95-percent threshold 29), nonpersistent attempt previews including zero, missing counts, empty and relationship-free
sources, absent account, ambiguous scope, explicit CANNOT_DROP, unknown IDs, invalid input and authentication.
Existing Acquisition domain read-model, JPA and Spring Modulith tests remain mandatory.

---

## 28. Connected Read-Only Build → Unique → Acquisition Browser Journey

### Context

The Build, Unique, and Acquisition browsers were implemented as independent Milestone 5 pages. The Unique
Detail page still contained an obsolete placeholder, and the homepage described technical bootstrap rather
than the actual player journey. Stable AcquisitionSource/DropRelationship identities can be known even if no
AccountEvaluation is currently selected, but the existing `findForUnique` query returned only a no-account
sentinel instead of those stable candidates.

### Decision

- Make the central browser flow navigable as Build requirement → Unique Detail → known acquisition source →
  its source-to-Unique relationship section. Add stable `unique-{UniqueDefinitionId UUID}` HTML anchors on
  Acquisition Detail relationship panels. The reverse link remains independently navigable.
- Add `AcquisitionReadModelService.findCandidatesForUnique(id)` returning account-independent candidate
  relationship identities and optional contextual assessments. Retain the existing `findForUnique(id)` API
  and its explicit `NoAccountEvaluationSelected` outcome by delegating to the same discovery/assessment
  implementation. No new persistence access outside Acquisition, no invented probability, no database changes.
- When no relationships are recorded, say **No acquisition sources recorded yet**, not that a Unique cannot
  drop. Even with no selected account, display known candidate identities but **no contextual conclusions**.
  Render source availability, relationship applicability, and accepted estimate status independently using
  the Acquisition browser's existing label policy. Show a numerical chance on Unique Detail only when the
  Acquisition domain's actionable fixed calculation is available; no assumptions about overlapping scopes.
- Replace the root bootstrap page with concise, player-facing links to Builds, Unique Collection, and
  Acquisition Sources. Do not describe the app homepage as exclusively SSF; the fixture's SSF game context
  remains a valid example rather than a product limitation. Order shared navigation as
  Home → Builds → Uniques → Acquisition. Preserve authentication, CSRF logout and link correctness.
- Remove the obsolete Unique Detail acquisition placeholder. Keep ownership editing, reverse Build
  associations, account dashboards, real Wiki catalog labels and broader UI layout/metadata polish
  explicitly deferred. When touching files, consider package cohesion; do not move unrelated internals.

### Verification

HTTP tests cover canonical A/B no-source empty states, C → Boss Y navigation, absence of account selection
with preserved identities, independent unavailable/CANNOT_DROP/unknown/ambiguous knowledge outcomes, and
the shared homepage/navigation. Keep existing Acquisition service integration and Spring Modulith tests green.

---

## 29. Extracting the Browser Incoming Adapter as a Spring Modulith Module

### Context

After completing the first end-to-end Build → Unique → Acquisition → ownership-editing browser journey, the
application root contained multiple Spring MVC controllers, cross-module read-model compositions, presentation
DTOs, and shared label helpers. Those features had coherent responsibilities and established cross-module
dependencies, but root-level code was not a separately verified application module. Keeping everything there
would also hinder navigation as further browser functionality is implemented.

This decision supersedes the earlier §24 guidance against introducing a separate `web` module simply for
tidiness. The justification here is a real, already implemented incoming-adapter boundary, not directory symmetry.

### Decision

- Introduce `browser` as the eighth Spring Modulith module, with explicit allowed dependencies on
  Account, Acquisition, Build, Catalog, and League. It does not depend on the Security or Goal modules.
- Organize HTTP controllers, browser read-model orchestration, and presentation DTOs by player feature:
  `browser.home`, `browser.builds`, `browser.uniques`, and `browser.acquisitions`. Keep reused
  `UniqueDisplayLabels` and `AcquisitionBrowserKnowledgeLabels` under `browser.shared`, rather than
  coupling one browser feature package to another's presentation implementation.
- Browser consumes existing public module-root APIs; domain rules and persistence stay in their owning modules.
  Its own subpackages are not external APIs and require no named interface. No event bus, web service interface,
  JSON API, endpoint versioning, or new abstraction is introduced.
- Keep the cross-module synthetic fixture initializer, IDs, and startup gating at the application root. The
  temporary Browser presentation fallback to `DevelopmentFixtureIds.uniqueLabels` remains intentionally
  unchanged; remove or replace it with real Catalog metadata as already tracked for Milestone 7.
- Mirror production package moves in `src/test/kotlin` in the same PR. Feature-level browser integration
  tests belong beside their feature package, while the cross-feature canonical journey belongs in `browser`.
  Shared test infrastructure and fixture-specific integration tests stay in their appropriate existing packages.
- Add `browser` to the expected Spring Modulith module inventory and keep `ApplicationModules.verify()` as
  an architecture guard. Preserve existing server-rendered endpoints, security/CSRF behavior, transactions,
  templates, domain objects, and database schema without functional changes.

### Verification

The full Gradle build, Spring Modulith verification, migrated MVC/PostgreSQL integration tests and canonical
browser journey must remain green. This move is distinct from planned Acquisition and Build internal
capability reorganizations and the later two-user/role-based authorization work.


---

## 30. Acquisition Internal Capability-Owned Persistence Organization

### Context

After the Browser module extraction, Acquisition's broad mixed `acquisition.persistence` package still contained
JPA entities, adapters, Spring Data repositories and persistence-only enums from five different capabilities.
Four internal persistence ports occupied the Acquisition root alongside intentionally externally consumed
domain and application APIs. Encounter knowledge had already established a partial internal capability
boundary under decision §25.

This Milestone 5.2 change is a behavior-preserving extension of that earlier, deliberately limited refactor.
It supersedes §25 only as to the final placement of its existing encounter persistence port and
corruption exception; the original rationale and public API constraints remain valid.

### Decision

- Organize the existing storage implementations by Acquisition capability:
  `acquisition.sourceidentity.persistence` for source/Boss identity,
  `acquisition.relationship.persistence` for stable source-to-Unique relationships and accepted applicability
  knowledge, `acquisition.probability.persistence` for accepted fixed estimates/publication states and
  withdrawals, `acquisition.attempts.persistence` for account-scoped recorded available attempts, and
  retain `acquisition.encounter.persistence` for accepted boss encounter knowledge.
- Keep capability-owned persistence *contracts* distinct from JPA implementation classes under
  `sourceidentity.internal`, `relationship.internal`, `probability.internal`,
  `attempts.internal`, and `encounter.internal`. The existing two encounter-level internal types
  move into `encounter.internal`; the established encounter JPA implementation does not move.
- Cross-capability repository/adapter dependencies within the single Acquisition Modulith module remain
  direct when justified: encounter persistence checks stable source/Boss identities, and probability persistence
  resolves canonical relationships. Do not introduce forwarding services or additional application modules
  solely for package isolation.
- Keep external domain types, public application services, read models and knowledge timelines in the
  Acquisition root for now. Their planned capability migration and explicit named interfaces will be a
  separate PR and review. Kotlin `internal` remains compilation-module visibility; Spring Modulith
  controls cross-module API exposure.
- Move existing source/relationship persistence integration tests alongside their new production packages.
  Update imports elsewhere while keeping existing test behavior and coverage. New packages remain descendants
  of the Spring Boot application scan root, retaining Spring/JPA component and entity discovery.
- Do not change JPA annotations, table names, native SQL, domain rules, transaction boundaries,
  concurrency semantics, source fixture registration, HTTP endpoints, or Flyway migrations.

### Verification

Validate the same eight Spring Modulith module boundaries via `ApplicationModules.verify()`, all existing
Acquisition PostgreSQL persistence/concurrency tests, fixture and Browser integration tests, and the complete
`./gradlew build`. This internal reorganization does not require any named interfaces yet.


---

## 31. Acquisition Capability Named Interfaces (Staged API Migration)

### Context

After decision §30 relocated internal Acquisition persistence contracts and adapters by capability,
47 Kotlin files remained in Acquisition's module root. The completed Browser extraction (§29)
provides a real cross-module consumer whose Acquisition API dependencies can now be explicitly verified.

### Decision

- Move existing Acquisition domain/application types to capability-owned packages and expose the
  package-level surface through Spring Modulith named interfaces. Do not make Acquisition an open module.
- Stage the work in separately reviewed changes: (A) `sourceidentity`, `encounter`, `knowledge`;
  (B) `relationship`, `probability`, `attempts`; and (C) `readmodel`, including the final
  Browser allowed-dependency cleanup and explicit visibility tests.
- Each named interface exposes types directly in its package, **not** nested `persistence` or
  `internal` classes. Maintain the original service methods, domain rules and rich return
  contracts for this initial migration. Never introduce new wrappers, events, or reactive machinery
  merely to reorganize packages.
- Keep `DevelopmentAcquisitionAccess` in Acquisition's root as an intentionally exported, gated
  bootstrap entry point used by the application-root fixture orchestrator. The root module
  declaration also remains there.
- Document that package-level named interfaces initially expose some canonical curator operations
  beyond Browser's direct needs. Narrowing exported type/return surfaces is a separately tracked
  review, not hidden functionality added to a mechanical package move.
- For each migration PR, update real cross-module imports and `allowedDependencies` references,
  move corresponding test files to mirrored packages, and preserve all persistence, transaction,
  concurrency, fixture, HTTP and probability invariants.

### Verification

Preserve all eight application modules and pass `ApplicationModules.verify()`, existing PostgreSQL
integration and concurrency tests, fixture gating and the canonical Browser journey in `./gradlew build`
after every stage.

---

## 32. Build Capability-Owned Persistence Organization (Milestone 5.2 PR A)

### Context

Following the Acquisition named-interface migration, Build remains a single Spring Modulith module.
Its root contains stable archetype/variant identities, versioned revision lifecycles, curated
requirements, pure readiness evaluation, browser-facing query services, and a property-gated
development fixture entry point. The original `build.persistence` package mixes identity
persistence with revision snapshots, their ordered requirement groups, and concurrency-sensitive
revision lifecycle updates.

The responsibility split is real, but revision, group, and requirement storage must remain
a single cohesive persistence concern: the revision owns its entire ordered semantic snapshot.

### Decision

- Start the Build capability migration with internal persistence organization only; the root
  domain/application APIs and Browser's existing `build` dependency remain unchanged in PR A.
  Follow-up moves and named interfaces for Build identity, revisions, requirements, readiness,
  and read models receive their own reviews and approvals.
- Move archetype/variant JPA entities, adapters, and Spring Data repositories into
  `build.identity.persistence`; move revision, group, and requirement JPA entities, adapters,
  and repositories together into `build.revision.persistence`.
- Move the existing `BuildVariantRevisionPersistencePort` into `build.revision.internal`.
  This port is a Build-internal lifecycle/read contract, not an externally published API.
- Retain one `BuildVariantRevisionPersistenceAdapter` that owns the complete ordered draft
  snapshot and the lifecycle transitions. Do not split requirement snapshot persistence across
  capability abstractions solely for package symmetry.
- Preserve revision identity invariants, conditional lifecycle transitions, the PostgreSQL
  partial unique active-revision constraint, exact constraint-name conflict translation,
  targeted JPA entity refresh after bulk updates, and the no-silent-retry policy from §6.
- Keep `DevelopmentBuildAccess` at the Build root as the existing property-gated fixture
  access point. Its direct use of Build-owned persistence adapters does not make those
  adapters public cross-module APIs.
- Mirror the existing `BuildPersistenceIntegrationTests` into
  `build.revision.persistence`. The suite exercises cohesive revision snapshot/schema
  invariants and uses identity persistence as test setup; retain its existing assertions.
  Tests of unchanged root-level services and Browser controllers remain at their owning
  package locations, with imports updated.
- Do not change JPA mappings, SQL, database schema, transaction boundaries, domain behavior,
  HTTP routes, fixture behavior, or readiness semantics.

### Verification

Keep all eight Spring Modulith modules and `ApplicationModules.verify()`; explicitly verify
that representative identity/revision persistence adapters and the revision port are not
exposed to other modules. Preserve the existing PostgreSQL integration tests for ordered
snapshot round trips, empty revisions, immutable activated semantics, stale draft conflicts,
active-revision uniqueness and competing activations, as well as fixture and Browser tests.
Require the full `./gradlew build` and green CI.

---

## 33. Build Identity, Revision and Requirement Named Interfaces (Milestone 5.2 PR B)

### Context

Build persistence is now capability-owned after §32. Stable archetype/variant identities,
versioned build revision lifecycle and curated Unique requirement expressions still share
the broad `build` module root alongside readiness evaluation and browser-facing queries.
Browser and the development fixture already consume this stable domain surface.

### Decision

- Keep Build as one Spring Modulith module, and move the existing domain/application files
  into `build.identity` (four stable identity types), `build.revision` (six revision and
  lifecycle types), and `build.requirements` (four authored requirement types).
  Add one package-level named interface for each capability.
- Preserve all constructors, services, return shapes and domain invariants; these are
  package/import moves, not domain or lifecycle redesigns. Requirement group definitions
  become a reusable API but remain owned and persisted as part of a revision snapshot
  under `build.revision.persistence`, with its existing internal revision port.
- Move seven existing corresponding tests into mirrored `identity`, `revision` and
  `requirements` test packages without changing their assertions. Keep the cohesive
  PostgreSQL persistence suite under `revision.persistence`; readiness tests remain in
  the root until their production owner moves in the separately reviewed PR C.
- Update Build, Browser, fixture and test consumers directly. Browser must list
  `build :: identity`, `build :: revision` and `build :: requirements` as allowed
  dependencies but retain the root `build` dependency temporarily for readiness and
  read-model services still in the module root. PR C will finalize the dependency list.
- Keep `DevelopmentBuildAccess` in the Build root as the property-gated development
  fixture entry point. Do not introduce authoring endpoints, migration shims,
  extra adapters, events, or DTO conversions merely for the relocation.
- Preserve the draft/active/superseded lifecycle, unique active revision per
  `(BuildVariantId, CompatibilityVersion)`, immutable activated requirements, ordered
  revision snapshots, explicit activation conflict behavior and no-silent-retry policy
  established in §6. Empty revisions remain valid; individual requirement groups do
  not become empty. Readiness and shared ownership semantics remain unchanged.

### Verification

Add Modulith named-interface exposure assertions for the three new capability APIs and
retain the existing assertions excluding nested persistence/internal types. Verify all eight
modules, existing revision lifecycle/concurrency and persistence tests, fixture and Browser
integration journeys, and the full `./gradlew build`. No JPA/SQL/schema, transaction,
HTTP, or product behavior changes belong in this PR.

---

## 34. Build Readiness and Read-Model Named Interfaces (Milestone 5.2 PR C)

### Context

After decisions §32–33, the Build root retains only five pure Unique-readiness
evaluation files, three application query files, the Build Modulith metadata, and
the property-gated development fixture access. Browser still temporarily allows
the broad Build root dependency to consume its catalog/readiness query services.
The earlier capability named interfaces have already separated identity, revision
lifecycle, authored requirements, and private snapshot persistence.

### Decision

- Move the five existing pure evaluation files (`RequirementEvaluation`,
  `RequirementGroupEvaluation`, `UniqueReadiness`, `UniqueReadinessEvaluator`,
  `UniqueReadinessResult`) to `build.readiness`. Preserve the evaluator's
  non-consuming, per-group `ALL`/`ANY` semantics, ENABLING/CORE precedence,
  independent UPGRADE evaluation, zero-group behavior and explainable results.
- Move `BuildCatalogQueryService`, `BuildReadinessService`, and
  `BuildReadinessQueryResult` to `build.readmodel`. Keep the `BuildCatalogEntry`
  result type declared in its existing service file. Retain the sealed result
  outcomes for missing account selection, missing compatible active revision,
  and successful evaluation; do not flatten them into an ambiguous readiness flag.
- Publish `readiness` and `readmodel` as package-level Spring Modulith named
  interfaces. Browser uses only `build :: identity`, `build :: revision`,
  `build :: requirements`, `build :: readiness`, and `build :: readmodel`
  for Build dependencies, with no broad `build` allowance. Keep other module
  dependencies unchanged.
- Move the corresponding pure evaluator and PostgreSQL-backed service integration
  test classes to mirrored `readiness` and `readmodel` test packages. Preserve
  test assertions and all existing other domain, persistence, fixture and Browser tests.
- Keep `ModuleMetadata` and the property-gated `DevelopmentBuildAccess` as the
  only two Build root production files. Do not expose nested revision/internal or
  persistence packages. Existing Build named interfaces remain intact.
- Do not change the globally selected evaluation context, per-account ownership,
  batched service evaluation/ownership snapshot behavior, `@Transactional(readOnly = true)`
  on the application query service, or Browser's enclosing `REPEATABLE_READ`
  transaction. Those boundaries belong to subsequent two-user authorization work,
  not the staged structural migration.
- Do not change domain rules, readiness calculations, constructors/return contracts,
  JPA/SQL/schema, revision lifecycle/concurrency guarantees, fixtures, HTTP routes,
  templates, or introduce wrappers, events or new product functionality solely
  for package organization.

### Verification

Verify exactly eight Spring Modulith modules and `ApplicationModules.verify()`;
assert all five named Build capability APIs, the public query/result types,
the intentionally exported fixture access, and representative unexposed internal
persistence types. Assert Browser's exact allowed dependencies without the broad
`build` root. Preserve the pure readiness tests, PostgreSQL lifecycle and
read-model integration tests, Browser navigation/Build X journey, fixture gating
and the full `./gradlew build`. The separate structure-refactor checkpoint
in issue #22 follows this PR and is not a reason for extra refactoring here.

---

## 35. Stable Application Identity, Context Ownership and Per-User Selection (Milestone 5.2 PR A)

### Context

Milestone 5.2 expands V0.1 from one configured application login to two distinct configured logins in
stages. The old `account_evaluation_selection` stores a database-wide singleton, and personal
facts (manual ownership, saved attempts) are keyed by `AccountContextId` but were previously
reachable without actor-aware authorization. Enabling a second login before restricting that
surface would expose another person's collection and derived readiness.

The two-user login mechanism is temporary development/test scaffolding. Future public browsing and
guest probability calculations must not require login; optional GGG OAuth may later map an external
provider subject to the same stable local application identity. OAuth subjects/usernames/roles are
**not** the owner's database key.

### Decision

- Add `security.identity.ApplicationUserId` using the project's existing `data class(UUID)`
  value-object convention, not a Kotlin inline class or a username string. Seed two deterministic
  local UUID identities in PostgreSQL with no password, role or OAuth credentials stored there.
  Keep the existing single USER form-login configuration until PR B.
- Publish a small `security :: identity` interface for the authenticated current-user identity.
  League depends only on that API; the Spring Security implementation stays internal. Do not
  conflate a logged-in app user, owned PoE `AccountContext`, and selected `AccountEvaluation`.
- Make account-context ownership mandatory and immutable alongside league/ruleset meaning.
  `AccountContextAccessService` is the central owner-scoped service boundary, denying missing
  and foreign contexts uniformly. ManualOwnership, CurrentOwnership, saved AvailableAttempts
  (read/write/clear), and selection validate the caller at the application-service boundary.
  Do not assume Modulith package rules constitute authorization.
- Flyway V018 assigns *all* legacy account contexts to the seeded USER UUID without changing
  their identities, manual quantities or saved attempt counts. Convert the global singleton
  selection (when present) into the USER's persisted per-user selection while preserving
  compatibility version and optional patch. A composite FK from
  `(account_context_id, application_user_id)` to the context's unique
  `(id, owner_application_user_id)` additionally forbids forged foreign selections.
  Existing migrations V001–V017 remain immutable; a missing legacy selection stays missing.
- Keep production services fail-closed without an authenticated principal. For the dev fixture,
  the existing `poe-companion.dev-fixture.enabled` gate enables a scoped legacy-actor executor:
  install the single configured authenticated identity only for the initializer operation,
  restore the prior security context in `finally`, and keep the existing dev-profile
  startup opt-in. Integration tests of personal services use an explicit test actor.
- Keep canonical Catalog/Build/Acquisition knowledge shared, and preserve evaluation logic,
  transaction boundaries, fixture A/B/C → Build X → Boss Y values, runtime HTTP routes and
  the single active login. Second login activation, ADMIN curator checks, richer independent
  personal fixtures and a context switcher are separate later PRs.
- This decision explicitly evolves the original single-user MVP scope; it does not silently
  edit the historical scope freeze. Long-term anonymous public access and GGG OAuth remain
  deferred and independent of the internal ownership key.

### Verification

Require exactly eight Spring Modulith modules and `ApplicationModules.verify()`, correct
`security :: identity` exposure and League dependency, forward-only PostgreSQL migration
verification, unknown/foreign context rejection, immutable owner persistence, per-user
selection integrity, existing fixture startup/idempotency and ownership/attempt semantics,
and the full Gradle/CI/CodeQL/CodeRabbit review before merging.

---

## 36. Two Configured Logins and Canonical Curator Authorization (Milestone 5.2 PR B)

### Context

PR A introduced application-owned UUID identities, mandatory AccountContext ownership and
per-user selections without activating a second login. V0.1 now needs an independent ADMIN
principal to verify privacy boundaries and curate shared PoE knowledge, while preserving
the existing legacy USER fixture and postponing broader ADMIN fixture data to PR C.

### Decision

- Configure exactly two temporary local form logins under sibling
  `poe-companion.security.user` and `poe-companion.security.admin` properties.
  Environment variables are `POE_COMPANION_APP_USER_USERNAME`,
  `POE_COMPANION_APP_USER_PASSWORD`, `POE_COMPANION_APP_ADMIN_USERNAME` and
  `POE_COMPANION_APP_ADMIN_PASSWORD`. The previous flat variables are intentionally
  removed before deployment. Usernames must differ to keep identity resolution
  unambiguous; strong, distinct passwords are recommended but not required.
- Keep credentials and Spring Security roles out of the domain identity and PostgreSQL
  `application_identity` rows. Resolve a configured authenticated username **and**
  its expected authority to the existing fixed USER/ADMIN `ApplicationUserId` UUID;
  reject anonymous, unknown and mismatched-role principals. Preserve form login,
  logout, secure session behavior, CSRF and anonymous health checks.
- Keep League's existing owner-only `AccountContextAccessService` authorization for
  **both** roles. ADMIN may not select, read, modify or clear another user's personal
  context, ownership or saved attempts. Browser/read-model results continue to derive
  personal facts from the current user's own selection.
- Introduce a small `security :: authorization` named interface and enforce ADMIN
  inside existing canonical application commands: Boss creation, encounter knowledge,
  drop relationship knowledge, fixed-probability publication/withdrawal and Build
  revision activation. Shared reads remain available to either authenticated user.
  Do not add speculative canonical CRUD controllers or public authoring APIs.
- Keep the development initializer behind its existing property/profile controls.
  Canonical seeding runs with an explicitly scoped trusted ADMIN principal, while
  legacy USER AccountContext ownership, attempts and selected evaluation are
  initialized only as USER. Each scope restores the previous SecurityContext, even
  on failure. Preserve atomicity, deterministic IDs and player edits on reruns.
- Do not introduce schema changes, new OAuth/provider identities, public guest
  access, full user management, an ADMIN AccountContext fixture, or a context
  selector UI. The last two belong to PR C.

### Verification

Exercise both actual configured form logins and role mapping, wrong/missing credentials,
anonymous and USER canonical-write rejection, ADMIN canonical authoring, both-role
personal service isolation and Browser composition, CSRF, session independence,
fixture idempotency and PostgreSQL-backed existing knowledge tests. Preserve the
Spring Modulith eight-module boundary and full Gradle/CI/CodeQL checks.

---

## 37. Additive Synthetic Fixture and Independent Personal Contexts (Milestone 5.2 PR C1)

The development fixture expands shared synthetic data to twelve Uniques, four Builds and four
Acquisition sources, retaining original A/B/C → Build X → Boss Y identities. New scenarios
exercise ENABLING/CORE/UPGRADE, ANY/ALL, multi-copy, upgrade-only, source availability,
CANNOT_DROP relationships and absent probability estimates.

USER retains its legacy Challenge SSF AccountContext; ADMIN gets independent Challenge SSF
and Standard SSF contexts. These contexts are personally owned, but LeagueDefinitions and
canonical game knowledge are shared. Fixture context counts must **not** determine which
leagues/versions other users may browse; global game-context selection and private personal
context selection remain distinct future UI responsibilities.

The opt-in fixture supports first creation, atomic upgrade from a complete legacy A/B/C
fixture, and re-verification of the complete expanded fixture. It refuses partial/corrupt
canonical data and checks AccountContext ownership, without resetting personal quantities,
attempt records (including recorded zero versus absence) or changed/cleared selections.
Initial selections are written only when their personal contexts are first seeded.
No public guest access, new migration, or production fixture startup is introduced.
---

## 38. Independent Game-Environment Selection Storage (Milestone 5.2 C2A)

**Canonical authority:** V2.1.3 Game Context and Identity amendment. The following are V0.1 persistence/API decisions, not a new product scope.

- Flyway V019 fails explicitly if contexts duplicate `(owner_application_user_id, league_definition_id, ruleset)`, then enforces that unique key. It never merges or discards personal data.
- Evolve existing per-user `account_evaluation_selection` storage in place: replace its required owned `account_context_id` with `league_definition_id` and `ruleset`, preserving compatibility version, optional patch, current user and missing selections. Retain the historical table and internal persistence names for the narrow transition, without adding a second selection source of truth.
- `GameEnvironmentSelectionService` validates known curated leagues and the V0.1 Normal ruleset for new selections. Full league lifecycle/version option discovery requires future curated metadata. Do not silently rewrite historical selections.
- Resolve owned contexts through an authenticated, owner-scoped league/ruleset lookup. Keep `AccountEvaluationService` as a transitional API for existing personal Browser and fixture consumers; game selection remains the sole persisted selection.
- Public guest access, external sign-in, GGG sync, UI selectors and automatic first-write creation remain outside C2A.

---

## 39. Curated Game Environment Options and Contextual Browser Reads (Milestone 5.2)

**Canonical authority:** V2.1.3 Game Context and Identity amendment. These are implementation-specific choices, not new lifecycle or public-access requirements.

- Flyway V020 stores explicitly recognized `(LeagueDefinitionId, CompatibilityVersion)` choices. The table is an opt-in selector registry only; a row does not assert that a Challenge League is active, that an update launched that league, or that all scoped facts have been verified. No LeagueDefinition is synthesized from a submitted form.
- The League module resolves the structured browser choice (version, Challenge/Standard, mortality, participation, Normal ruleset, PC realm) against that registry. A structured choice must identify exactly one curated league; where multiple leagues share the same dimensions, an optional explicit LeagueDefinitionId disambiguates. Missing, uncurated or unresolved ambiguous choices are rejected without replacing the saved selection. The fixture registers only its actual 3.30 Challenge and Standard choices and does so idempotently.
- Selection remains a per-authenticated-user environment preference. Existing saved choices not currently in the registry remain visible and unchanged; resaving the same league/version preserves its exact patch, while selecting a different recognized pair does not carry an unrelated patch.
- The selector keeps separate compatibility version, Challenge/Standard, mortality, participation and Normal-ruleset controls. Thymeleaf renders the choices; a dedicated Browser parser safely converts form text to typed inputs, and League resolves only curated combinations. Invalid or ambiguous submissions redisplay the values and a useful message; an ambiguous pair exposes an additional explicit-league choice. No JavaScript is required for this version of the form. Successful selection uses a CSRF-protected POST/Redirect/GET flow; the shared header is derived from League metadata rather than AccountContext IDs.
- Curated league/version persistence follows the §18 escalation policy: JPA entity + Spring Data repository + League persistence adapter. The atomic idempotent fixture insert uses PostgreSQL `ON CONFLICT DO NOTHING` through a narrowly targeted Spring Data JPA native query, not direct JDBC. V020 names its PK, FK and version CHECK constraints.
- Build's active revision is selected by EvaluationContext before reading personal ownership. A compatible revision with no matching AccountContext exposes canonical requirement groups but no evaluated ownership or satisfaction. Empty required groups remain meaningful canonical knowledge.
- Acquisition source/relationship/probability applicability uses EvaluationContext independently of saved attempts. Accepted estimates can produce fixed-model thresholds and read-only probability previews from an input attempt count; this input is not persisted as AvailableAttempts or bound to a fabricated AccountContext.
- Personal ownership and recorded available attempts remain owner-scoped and are not created during browsing or environment switching. Authenticated first-write context creation belongs to the separately scoped follow-up. Public guest access, external identity providers, league lifecycle curation, and Ruthless are not implemented here.


## 40. Transactional First Personal Write (Milestone 5.2 C2C)

**Canonical authority:** V2.1.3 Game Context and Identity amendment, §§4–5.

- A personal `AccountContext` is still uniquely identified by `(owner ApplicationUserId, LeagueDefinitionId, Ruleset)`; the selected compatibility version and optional game patch are not part of its identity. No new schema or migration is needed after V019.
- The League application boundary resolves an existing context for the signed-in user before considering creation. Existing contexts remain usable for historical, now-unrecognized saved selections. A missing context may be created only for a `NORMAL`-ruleset league/version association recorded in the curated selector registry. Ownership of the context is derived from the authenticated application user, never a submitted UUID.
- First-write creation uses a narrowly scoped Spring Data JPA native `INSERT ... ON CONFLICT ON CONSTRAINT uq_account_context_owner_league_ruleset DO NOTHING`, followed by an owner/league/ruleset lookup, inside the same `READ COMMITTED` write transaction. Concurrent first writes reuse the winning identity rather than manufacturing separate contexts or relying on pre-insert existence checks alone.
- Account owns sparse manual-ownership writes: a positive quantity creates/reuses the selected context and persists the fact atomically, while a zero edit with no context is a no-op. Acquisition owns recorded available-attempt writes: saving zero creates/reuses the context and persists explicit zero; clearing an absent count does not create a context. Existing explicit-context operations continue to require owner authorization.
- The authenticated Unique Browser exposes the ownership form when a matching personal context exists or the selected environment is recognized for new personal data. It validates catalog identity and quantities before writing, then redirects after a successful save. An unrecognized historical selection without an existing context cannot create new personal data and receives an explicit conflict. Acquisition probability previews remain read-only; a saved-attempt editing form is a separate Browser follow-up.
- First-write context creation and the corresponding personal fact commit or roll back together. Browser selection, canonical reads, and probability previews never create a personal context. The USER and ADMIN retain separate personal storage for the same canonical environment.


## 41. Acquisition Saved-Attempt Editing in the Browser (Milestone 5.2)

- Authenticated Acquisition Detail provides a separate saved personal available-attempt form and the existing read-only probability-preview form. Only POST commands change saved facts; preview remains GET and does not persist hypothetical values. Successful POST uses PRG back to source detail without a preview query parameter.
- An explicit saved zero is a real AvailableAttempts record; missing means no count is recorded. The page labels the removal action "Remove saved count", displays it only when a record exists (including zero), and briefly explains the difference.
- Browser validates a known canonical AcquisitionSource before issuing a selected-environment write, avoiding unintended AccountContext creation for an unknown source. The first save delegates to C2C's transactional creation/reuse logic. Historical unrecognized selections can update an existing personal context but cannot initialize a new one.
- Clearing deletes only the source's saved attempt fact; if no context exists it is a no-op. USER and ADMIN personal data remain isolated. Accepted acquisition source/relationship/estimate knowledge does not control whether a personal count can be entered.
- Server-side validation rejects missing, malformed, negative and overflowing counts. CSRF protects POST actions, and the Browser returns explicit missing-selection, unrecognized-environment, and unknown-source errors without modifying personal facts. No schema, probability model or generalized curator UI changes are needed.


## 42. Acquisition Browser Without a Selected Game Environment (Milestone 5.2)

- Without a selected game environment, canonical Acquisition source identities and relationships remain browsable, but contextual eligibility and calculations stay unknown. Browser knowledge labels refer to the missing game environment, not a missing personal account.
- The Acquisition source detail hides the recorded-attempt field and both saved-attempt and probability-preview forms until an environment is selected. Once selected, absent saved attempts and explicit saved zero remain distinct.
- An explicit non-negative probability preview query with no environment returns HTTP 409 and a link to the environment selector on the source detail page. Browsing without a preview query remains read-only and successful; no AccountContext is created. Acquisition's knowledge, probability and persistence behavior remains unchanged.


## 43. Contextual Acquisition Source Names and Presentation (Milestone 5.2 PR D)

**Authority:** V2.1.2 Acquisition amendment, §§3–5 and 11–13; implementation decisions §§10, 17, 20, 27 and 42. This records Browser presentation, not a change to source identity or accepted knowledge.

- A stable `AcquisitionSource` is known independently of its context-scoped `BossEncounterKnowledgeSnapshot.displayLabel`. Browser displays a label only when accepted encounter knowledge resolves unambiguously for the selected game environment. It must not select the latest/arbitrary snapshot or infer a source name from a participating `BossDefinition`.
- Where no contextual label resolves, Browser uses an explicitly non-identifying fallback: page-local numbered `Acquisition source N` on the list, and `Acquisition source` on detail. UUIDs remain navigation keys, not player-facing metadata. Presentation state determines whether the name resolved; string comparison against fallback text is not allowed.
- Without a selected environment, explain prominently that source identities exist but their contextual names, availability and drop information have **not been evaluated**; link to the selector. With an environment but missing, unknown, non-applicable or ambiguous knowledge, explain the actual outcome instead—selecting an environment is not guaranteed to resolve a label.
- Source availability, relationship state and estimate remain independent. A recorded accepted fixed estimate is still visible when not actionable. Display applicable chances and probability outcomes prominently, with complete thresholds and estimate provenance/evidence/timestamps in accessible secondary disclosure. Presentation formatting must not round a small positive cumulative chance to 0% or a sub-certain one to 100%.
- Saved AvailableAttempts (including explicit zero) and the optional GET-only hypothetical preview remain separate; no presentation change may mutate calculated eligibility, owner-scoped persistence, first-write rules or authorization.

**Deferred:** A separately curated, context-independent source discovery label and richer encounter/access ingestion require a future Acquisition-specific design and workflow. This is not automatically covered by Milestone 7 Unique Catalog ingestion. See [issue #22's detailed examples and revisit triggers](https://github.com/Colin23/poe-companion/issues/22#issuecomment-6402358187).

## 44. Progressive Enhancement of Curated Game-Environment Selection (Milestone 5.2)

**Authority:** Canonical V2.1.3 game-context/identity amendment §2 and implementation decision §39. This is a Browser interaction refinement, not a change to environment identity, applicability, or owner-scoped state.

- Keep the single player-facing selector structured by compatibility version, Challenge/Standard type, mortality and participation; Normal ruleset and PC realm remain fixed while they are the only supported choices. Avoid a flat card/list for every recognized environment: the interface should remain navigable as the curated registry grows.
- The League module's actual `GameEnvironmentOption` entries are the **only** source of eligible combinations. Browser renders that list as escaped HTML data attributes; a dedicated external script filters downstream native `select` options and previews an exact match. Never construct a cross-product of independent values and present it as automatically recognized.
- Keep a previously selected downstream value only if it remains valid after an upstream change. Automatically choose an alternative only when exactly one value remains; otherwise require an explicit choice. Where multiple real curated leagues share the same structured dimensions, require an explicit league identity. A draft selection never changes the saved environment without an explicit submit.
- The browser JavaScript is a small, focused progressive enhancement analogous to the mobile navigation script. It belongs in a separate static `.js` file, with no inline event handlers, frontend framework, or client-side persistence. It may handle UI filtering and accessible preview text; `GameEnvironmentFormParser` and `GameEnvironmentOptionService.select()` remain the authoritative HTTP/domain validation and save boundary.
- Without JavaScript, the server-rendered form must remain usable: it renders all curated dimension values, validates complete submissions, redisplays invalid inputs and errors, and reveals an explicit league choice when a submitted combination is ambiguous. JavaScript is not a security or domain-integrity prerequisite.
- Preserve CSRF and POST/Redirect/GET, selection ownership, old selections absent from the current registry, exact-patch preservation for reselection of the same league/version, and the rule that selection never initializes `AccountContext` or triggers synchronization.

**Non-scope:** Real league lifecycle discovery, historical league search, Ruthless, GGG integration, new persistence/schema, a general JavaScript architecture, or replacing the server-rendered Browser.
