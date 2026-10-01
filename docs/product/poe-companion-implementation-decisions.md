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
