# PoE Companion — Specification Index V2.1.4

**Status:** Canonical — Personal drop-rate override amendment (upon merge)
**Date:** 2026-10-28
**Purpose:** Publish the authoritative V2.1.4 specification set, preserving the frozen V2.1.1 baseline and V2.1.2/V2.1.3 amendments.

V2.1.4 records Milestone 6 personal fixed drop-rate override semantics: exact confirmation scope, reviewable forward-only suggestions, persistent dismissals and explicit canonical/personal calculator choice. It refines older 'override becomes STALE' wording without invalidating previously confirmed values, and does not expand V0.1 scope.

---

## 1. Canonical document set

| Document | Authority |
|---|---|
| `poe-companion-spec-index-v2.1.4.md` | Canonical entrypoint and amendment precedence |
| `poe-companion-personal-drop-overrides-amendment-v2.1.4.md` | Normative personal DropRateOverride scope, confirmation, review, dismissal and calculator choice |
| `poe-companion-game-context-and-identity-amendment-v2.1.3.md` | Normative game-environment, personal-context and public-access refinement |
| `poe-companion-acquisition-domain-amendment-v2.1.2.md` | Normative Acquisition-domain refinement, unchanged |
| `poe-companion-product-v2.1.1.md` | Frozen product vision |
| `poe-companion-decision-v2.1.1.md` | Frozen cross-cutting product/domain decisions |
| `poe-companion-domain-model-v2.1.1.md` | Frozen domain vocabulary/invariants, subject to amendments |
| `poe-companion-journeys-v2.1.1.md` | Frozen observable journeys, subject to amendments |
| `poe-companion-mvp-scope-freeze-v2.1.1.md` | V0.1 scope baseline; later public behavior is not automatically V0.1 work |
| `poe-companion-feasibility-spikes-v2.1.1.md` | Time-sensitive external evidence, reverify when material |
| `poe-companion-technical-design-v2.1.1.md` | Frozen architecture, subject to amendments |
| `poe-companion-tech-stack-decision-v2.1.1.md` | Engineering/technology baseline |
| `poe-companion-bootstrap-impl-plan-v2.1.1.md` | Historical implementation sequence, interpreted through amendments |

The frozen V2.1.1 documents and V2.1.2/V2.1.3 amendments are intentionally not rewritten or duplicated. Earlier indexes remain historical entrypoints; use V2.1.4 for active authority.

## 2. Authority and precedence

The closest domain-specific normative document governs the question:

- For personal drop-rate override confirmation, stale suggestions, dismissal and calculator selection, **the V2.1.4 personal drop-rate override amendment** governs.

- For game-environment selection, automatic personal-context resolution, public guest/authentication direction and the refined C2 boundaries, **the V2.1.3 game-context/identity amendment** governs.
- For Acquisition identity, encounter semantics, availability/access/relationship/probability distinctions, **the V2.1.2 Acquisition amendment** continues to govern.
- Everywhere else, the relevant V2.1.1 product decision, domain model, user journey, MVP scope, technical design or implementation plan governs as described in the V2.1.1 specification index.

Where an amendment explicitly contradicts or narrows older wording in its own scope, the later, more specific amendment wins. V2.1.4 refines the V2.1.1 personal override stale wording without changing V2.1.2 canonical Acquisition authority or V2.1.3 game-environment and AccountContext rules.

The evolving `poe-companion-implementation-decisions.md` is **non-canonical**. It records concrete persistence, API, framework and milestone-specific decisions, but cannot silently change domain meaning or expand the scope freeze.

## 3. Principal refinements through V2.1.4

- **Personal drop-rate overrides:** Confirmed for an exact owner/league/ruleset/version/optional-patch and relationship/model/conditions context; patch unspecified is not broad. A context switch never invalidates a source confirmation. Earlier compatible values are review-only suggestions which can be dismissed; a confirmed personal rate can substitute for a missing canonical number only when independent acquisition/model prerequisites hold.
- **One user-facing environment selection**, internally constructing an `EvaluationContext`, with no mandatory personal AccountContext dropdown.
- Curated `LeagueDefinition` identities and structured dimensions; Challenge League lifecycle is **not** equivalent to compatibility-version identity. Standard survives across compatibility versions.
- Automatic optional matching of one owned AccountContext by `(ApplicationUserId, LeagueDefinitionId, Ruleset)`, with uniqueness enforced for V0.1.
- Canonical knowledge remains available without personal ownership; “no personal context”, “zero owned”, “missing attempts” and “recorded zero attempts” are distinct.
- Personal contexts are created only for meaningful authenticated personal writes, not as a side effect of browsing.
- The initial public product can allow guest reading and temporary user-input calculation; authenticated personal persistence can use an established identity provider without depending on GGG API registration.
- Optional later GGG connection/sync is separate from application authentication. At most one connected GGG account per Companion identity is the intended product constraint.

## 4. Scope boundaries

V0.1 retains the temporary USER/ADMIN login mechanism. V2.1.4 defines required Milestone 6 personal override behavior; it does **not** release unauthenticated routes, implement identity-provider login, connect to GGG, store anonymous guest drafts, introduce unsupported probability models or ingest real league lifecycle/patch data.

This is a targeted product-model clarification, not a new broadly expanded MVP.

## 5. Change discipline

Preserve stable application-owned IDs, strict `EvaluationContext` applicability, owner-scoped personal data, canonical vs personal separation, and the V2.1.2 Acquisition distinctions.

When implementation uncovers a genuine product/domain ambiguity, decide it explicitly in an appropriately scoped canonical amendment. Record concrete implementation contracts separately and keep them consistent with these normative decisions.
