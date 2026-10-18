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

The application initializer is a callable transactional Spring bean without autonomous startup behavior.
Automatic loading is restricted to an ApplicationRunner enabled by **both** the `dev` Spring profile and the
explicit `poe-companion.dev-fixture.enabled=true` flag. Production/default startup performs no synthetic inserts.
Tests invoke the initializer directly without these switches, using Testcontainers PostgreSQL; a separate startup
test verifies the enabled runner, and lightweight gating tests verify disabled conditions. Standard CI needs no
extra environment variables or workflow changes.

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
- Don't reorganize small modules just for symmetry, and don't create a separate `web` module merely to tidy files.
  Acquisition's large root is the first candidate for a dedicated behavior-preserving package reorganization.
- Before adding any service, adapter, interface or access wrapper, first decide whether an **existing** service
  can and should own that behavior without blurring responsibility. Introduce a new abstraction only when it
  materially improves cohesion, module boundaries, reuse, testability or clarity. A single implementation does
  not automatically require an interface, and duplicated forwarding methods aren't automatically justified.

### Development-only fixture lifecycle

- The fixture's optional startup **runner** already requires both the `dev` profile and the
  `poe-companion.dev-fixture.enabled=true` property. In a **separate focused cleanup**, also gate
  `DevelopmentFixtureInitializer` and `DevelopmentCatalogAccess`, `DevelopmentLeagueAccess`,
  `DevelopmentBuildAccess`, `DevelopmentAcquisitionAccess` on the opt-in property. Tests can turn that
  property on explicitly, invoke initialization directly without the `dev` profile, and verify normal
  applications do not instantiate those beans. Conditional beans remain present in the compiled artifact;
  no separate source set/module is needed for now. Do not alter existing initialization/idempotency semantics.
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

Keep PR #70 limited to the view-model file split, CSS sections, and this documentation update (alongside its
existing Unique browser behavior). Treat opt-in development bean gating and internal Acquisition package
organization as separate follow-up increments, with a briefing and tests before implementation.
