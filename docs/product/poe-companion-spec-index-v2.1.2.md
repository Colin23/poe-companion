# PoE Companion — Specification Index V2.1.2

**Status:** Canonical — Acquisition-domain micro-release
**Date:** 2026-10-09
**Purpose:** Define the authoritative V2.1.2 specification set and the narrow Acquisition-domain refinement discovered during Milestone 4 implementation.

V2.1.2 is a deliberately small canonical micro-release. It does **not** reopen broad product architecture or V0.1 scope.

The V2.1.1 specification remains the frozen baseline. V2.1.2 adds one normative Acquisition-domain amendment that refines the previously under-specified meaning of boss encounters, AcquisitionSource identity, source availability, access knowledge, and the boundary between source knowledge and probability knowledge.

---

## 1. Canonical V2.1.2 Document Set

The active V2.1.2 specification consists of:

| Document | Authority |
|---|---|
| `poe-companion-spec-index-v2.1.2.md` | Canonical entrypoint, authority rules, and V2.1.2 change discipline |
| `poe-companion-acquisition-domain-amendment-v2.1.2.md` | Normative Acquisition-domain refinement introduced in V2.1.2 |
| `poe-companion-product-v2.1.1.md` | Product vision and philosophy, unchanged except where the V2.1.2 Acquisition amendment is more specific |
| `poe-companion-decision-v2.1.1.md` | Accepted cross-cutting product/domain decisions, unchanged except where refined by the V2.1.2 Acquisition amendment |
| `poe-companion-domain-model-v2.1.1.md` | Baseline domain terminology and invariants, unchanged except where refined by the V2.1.2 Acquisition amendment |
| `poe-companion-journeys-v2.1.1.md` | Baseline observable behaviour, unchanged except where refined by the V2.1.2 Acquisition amendment |
| `poe-companion-mvp-scope-freeze-v2.1.1.md` | V0.1 scope, unchanged except where the V2.1.2 Acquisition amendment clarifies the existing Acquisition scope |
| `poe-companion-feasibility-spikes-v2.1.1.md` | Time-sensitive external evidence |
| `poe-companion-technical-design-v2.1.1.md` | Baseline architecture, unchanged except where refined by the V2.1.2 Acquisition amendment |
| `poe-companion-tech-stack-decision-v2.1.1.md` | Technology baseline, unchanged |
| `poe-companion-bootstrap-impl-plan-v2.1.1.md` | Baseline implementation sequence, interpreted through the V2.1.2 Acquisition amendment |

The V2.1.1 files remain frozen and are intentionally not rewritten or duplicated for this micro-release.

---

## 2. Authority Rule

For questions outside Acquisition, V2.1.1 authority rules remain unchanged.

For Acquisition-domain questions covered by the V2.1.2 amendment:

```text
V2.1.2 Acquisition amendment
    wins over
conflicting or less-specific V2.1.1 Acquisition wording
```

This is a refinement, not permission to reinterpret unrelated V2.1.1 rules.

The evolving `poe-companion-implementation-decisions.md` remains non-canonical. It records implementation clarifications and deliberate deferrals, but it cannot override this canonical specification.

---

## 3. Why V2.1.2 Exists

Milestone 4 implementation exposed a real ambiguity in the frozen V2.1.1 Acquisition model.

V2.1.1 correctly established:

```text
AcquisitionSource
DropRelationship
DropEstimate
AvailableAttempts
EvaluationContext applicability
```

but it intentionally did not fully specify:

```text
boss identity vs encounter identity
one boss appearing in several acquisition contexts
one encounter containing several bosses
regular/Uber encounter identity
encounter availability across game versions
access mechanics vs drop applicability
area/arena metadata
curator lifecycle semantics for removed/reintroduced encounters
```

Real Path of Exile encounter families show that these distinctions are product-significant. Encoding a simple
`AcquisitionSource(id, name, type)` before resolving them would turn provider/presentation details into accidental domain semantics.

V2.1.2 resolves the domain meaning before production implementation proceeds.

---

## 4. V2.1.2 Acquisition Summary

The amendment establishes these canonical concepts:

```text
BossDefinition
    = stable application-owned identity for a boss/game entity

AcquisitionSource
    = stable application-owned identity for one independently meaningful acquisition opportunity

BOSS_ENCOUNTER AcquisitionSource
    = an encounter-level source
    = may involve one, many, or configuration-dependent BossDefinitions

AcquisitionSourceKnowledge
    = conceptual version/context-dependent knowledge about source availability and encounter details
    = not a mandated Kotlin class/table name

DropRelationship
    = separate versioned claim that a source can produce a UniqueDefinition

DropEstimate
    = separate probability/model knowledge for an applicable DropRelationship

AvailableAttempts
    = personalized AccountContext state
```

Key separations:

```text
Boss identity != encounter/source identity
area identity != encounter/source identity
display label != identity
source availability != DropRelationship applicability
access condition != probability condition
source unavailable != source deleted
no applicable source knowledge != positively unavailable
```

---

## 5. V0.1 Scope Is Not Broadened

V2.1.2 does not expand V0.1 beyond the existing MVP freeze.

V0.1 product support still requires only:

```text
BOSS_ENCOUNTER AcquisitionSources
curated DropRelationships
supported fixed DropEstimates
manual AvailableAttempts
personal fixed DropRateOverride
```

The amendment does **not** require V0.1 to implement:

```text
non-boss AcquisitionSource product flows
generic access-condition DSLs
automatic fragment/resource counting
automatic Delve-depth eligibility calculation
provider-schema mirroring
generic encounter simulation
conditional probability models
```

Those remain deferred unless a later canonical change explicitly promotes them.

---

## 6. Change Discipline

Future Acquisition implementation must preserve the V2.1.2 semantic separations even if the first persistence shape is intentionally small.

Implementation may choose concrete names such as revisions, change-point facts, projections, or persistence adapters later, but must not silently collapse:

```text
BossDefinition
AcquisitionSource
source availability
DropRelationship
DropEstimate
AvailableAttempts
```

into one mutable record merely for convenience.

If implementation discovers another domain-level contradiction rather than a framework/storage detail, feed it back deliberately through the canonical specification rather than burying it only in implementation notes.

---

## 7. Canonical Product Chain

The V2.1.1 product chain remains valid, with the Acquisition portion now interpreted more precisely:

```text
MISSING UNIQUE
      ↓
ACQUISITION SOURCE
      ↓
SOURCE AVAILABILITY / ENCOUNTER CONTEXT
      ↓
DROP RELATIONSHIP
      ↓
DROP ESTIMATE / CONDITIONS
      ↓
AVAILABLE ATTEMPTS
      ↓
TARGET DROP PROBABILITY
      ↓
PLAYER DECISION
```

The Companion still informs the final decision; it does not make it.
