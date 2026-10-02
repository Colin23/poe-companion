# PoE Companion — Implementation Decisions

**Status:** Evolving implementation clarifications
**Purpose:** Record durable product/domain/architecture decisions discovered while implementing the frozen V2.1.1 specification.

This document does not replace or silently modify the canonical V2.1.1 product specification. When implementation reveals an ambiguity, missing detail, or useful clarification, record the current implementation decision here. If a future implementation decision conflicts with the canonical specification, resolve that conflict explicitly rather than treating this document as an override.

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

### Deferred Design Work

Do not introduce generic patch ordering merely to implement `>=` comparisons.

Before effective-from semantics are implemented, define the ordering rules required for real GGG patch identifiers, including suffix-bearing patches such as `3.29.3b`. Hotfixes and maintenance events must not be folded into `GamePatch` ordering without an explicit domain decision.

The first concrete versioned-knowledge use case should drive the implementation. Current expectation is that acquisition `DropRelationship` applicability will be the first Slice 1 feature that requires this behavior.


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

