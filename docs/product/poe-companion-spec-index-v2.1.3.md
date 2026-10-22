# PoE Companion — Specification Index V2.1.3

**Status:** Canonical — Game-context and identity amendment (upon merge)  
**Date:** 2026-10-22  
**Purpose:** Publish the authoritative V2.1.3 specification set while preserving the frozen V2.1.1 baseline and the V2.1.2 Acquisition refinement.

V2.1.3 records product, domain and user-journey decisions established during Milestone 5.2 C2 planning. It refines player-facing game-environment selection, the relationship between canonical and personal contexts, and the phased future guest/authentication direction. It is not a general rewrite of existing domain or Acquisition semantics and does not require public features in V0.1.

---

## 1. Canonical document set

| Document | Authority |
|---|---|
| `poe-companion-spec-index-v2.1.3.md` | Canonical entrypoint and amendment precedence |
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

The V2.1.1 and V2.1.2 files are intentionally not rewritten or duplicated. Earlier specification indexes remain historical entrypoints; use V2.1.3 for active authority.

## 2. Authority and precedence

The closest domain-specific normative document governs the question:

- For game-environment selection, automatic personal-context resolution, public guest/authentication direction and the refined C2 boundaries, **the V2.1.3 game-context/identity amendment** governs.
- For Acquisition identity, encounter semantics, availability/access/relationship/probability distinctions, **the V2.1.2 Acquisition amendment** continues to govern.
- Everywhere else, the relevant V2.1.1 product decision, domain model, user journey, MVP scope, technical design or implementation plan governs as described in the V2.1.1 specification index.

Where an amendment explicitly contradicts or narrows older wording in its own scope, the later, more specific amendment wins. The new amendment does not override the Acquisition amendment or authorize unrelated scope changes.

The evolving `poe-companion-implementation-decisions.md` is **non-canonical**. It records concrete persistence, API, framework and milestone-specific decisions, but cannot silently change domain meaning or expand the scope freeze.

## 3. Principal V2.1.3 refinements

- **One user-facing environment selection**, internally constructing an `EvaluationContext`, with no mandatory personal AccountContext dropdown.
- Curated `LeagueDefinition` identities and structured dimensions; Challenge League lifecycle is **not** equivalent to compatibility-version identity. Standard survives across compatibility versions.
- Automatic optional matching of one owned AccountContext by `(ApplicationUserId, LeagueDefinitionId, Ruleset)`, with uniqueness enforced for V0.1.
- Canonical knowledge remains available without personal ownership; “no personal context”, “zero owned”, “missing attempts” and “recorded zero attempts” are distinct.
- Personal contexts are created only for meaningful authenticated personal writes, not as a side effect of browsing.
- The initial public product can allow guest reading and temporary user-input calculation; authenticated personal persistence can use an established identity provider without depending on GGG API registration.
- Optional later GGG connection/sync is separate from application authentication. At most one connected GGG account per Companion identity is the intended product constraint.

## 4. Scope boundaries

V0.1 and current Milestone 5.2 remain protected by the existing temporary USER/ADMIN login mechanism. C2 implements selection, context resolution and applicable Browser behavior; it does **not** release unauthenticated routes, implement identity-provider login, connect to GGG, store anonymous guest drafts or ingest real league lifecycle data.

This is a targeted product-model clarification, not a new broadly expanded MVP.

## 5. Change discipline

Preserve stable application-owned IDs, strict `EvaluationContext` applicability, owner-scoped personal data, canonical vs personal separation, and the V2.1.2 Acquisition distinctions.

When implementation uncovers a genuine product/domain ambiguity, decide it explicitly in an appropriately scoped canonical amendment. Record concrete implementation contracts separately and keep them consistent with these normative decisions.
