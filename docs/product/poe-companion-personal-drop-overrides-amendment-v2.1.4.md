# PoE Companion — Personal Drop-Rate Override Amendment V2.1.4

**Status:** Canonical normative amendment upon merge
**Date:** 2026-10-28
**Purpose:** Define V0.1 personal fixed-probability override scope, confirmations, reviewable suggestions, dismissals, and canonical-versus-personal calculator selection before Milestone 6 implementation.

This amendment refines personal DropRateOverride behavior and its player journey. It preserves the frozen V2.1.1 scope, V2.1.2 Acquisition semantics, and V2.1.3 game-environment/account-context separation. It does not prescribe a database schema, UI framework, or new probability model.

---

## 1. Canonical knowledge and personal input remain separate

A DropRateOverride is **player-owned probability input** for one stable DropRelationship and a supported probability-model/attempt/conditions context, not a correction to the canonical DropEstimate.

- Personal changes never overwrite canonical probability knowledge, provenance, publication history or withdrawals.
- The player may choose between an applicable accepted canonical probability and a confirmed personal probability for a supported calculation.
- There may be at most one current confirmed personal override per owner/account context and exact relationship/model/applicability/conditions key. Superseded or historical revisions may be retained without creating competing current values.
- V0.1 personal values use the existing FIXED_BERNOULLI constraints (`0 < p <= 1`, finite), supported independent-attempt semantics and conditions. A missing estimate is not `p = 0`.

## 2. Exact personal confirmation context

A confirmed override is scoped to the owning AccountContext, the **actual LeagueDefinition identity**, Ruleset, DropRelationship, supported probability model/attempt definition and conditions, CompatibilityVersion and **optional exact GamePatch** (including suffix).

- The AccountContext associates a player with a league/ruleset; CompatibilityVersion and GamePatch additionally distinguish **probability confirmations**, without changing AccountContext identity.
- League display names are not identities. Standard and Challenge are separate league identities; two different Challenge Leagues are distinct. HC/SC, SSF/Trade, realm and Normal/Ruthless differences do not permit implicit transfer.
- **Patch unspecified is its own confirmation context**, not a wildcard assertion about all patches. A personal estimate confirmed for 3.30 without a known exact patch is usable for that same patch-unspecified 3.30 context, but not automatically for 3.30.1 or 3.30.2. An estimate confirmed for 3.30.2 likewise does not establish a 3.30-wide rate.
- Do not conflate personal missing patch precision with canonical known-broad baseline or effective-from-patch semantics. The existing canonical timeline resolver and its separate precision rules remain authoritative for canonical knowledge.

## 3. Context changes preserve previous confirmations

Simply selecting or viewing another game environment must not create, delete, modify, invalidate or automatically mark an existing confirmed personal rate as stale; it must not create an AccountContext.

A confirmed source remains confirmed for **its original exact context**. Returning to that context restores its saved personal value and saved canonical/personal calculator preference without another review, even after visiting other leagues, rulesets, versions or patches.

The earlier V2.1.1 wording that an override "becomes STALE" is **refined**: a *proposed reuse of an earlier confirmed value in a different target context* needs review. The source record does not acquire a permanently stale flag simply because the user selected a different context. "Needs review" is an appropriate player-facing description.

## 4. Personal resolution outcomes

For an eligible known target context and relationship/model/conditions key:

1. **Confirmed:** a current personal override matches every required target dimension exactly. It is eligible as personal calculator input if the other calculation prerequisites hold; no suggestion replaces it.
2. **Needs review:** there is no exact confirmed value, but an eligible earlier confirmed value can be suggested. The player sees its percentage **and source context**. This is a derived proposal, not a persisted target override, and **cannot** power personal calculations until explicitly confirmed.
3. **Absent:** there is no confirmed target value and no eligible non-dismissed proposal. No personal rate is invented.

**Dismissal** is separate non-numeric personal decision state that can suppress a proposal. It is not a fourth kind of numeric DropRateOverride.

These distinctions must not erase unknown canonical knowledge or collapse missing saved attempts, recorded zero attempts, and absent personal probability into the same state.

## 5. Conservative suggestion boundaries and order

Suggestions are **forward-only** and require identical owner, actual LeagueDefinition, Ruleset, stable DropRelationship, supported model/attempt semantics and conditions.

- Never suggest across league identities or rulesets, even at an identical patch: HC SSF Standard 3.30.1 at 13% provides **no suggestion** for HC SSF Challenge 3.30.1, SC SSF Standard 3.30.2, or HC Trade Standard 3.30.2.
- Never suggest from a future CompatibilityVersion, or from a later exact patch within the same CompatibilityVersion to an earlier target.
- For an **exact-patch target**, prefer the most recent confirmed **earlier exact patch** of the same CompatibilityVersion. A confirmed **patch-unspecified** estimate in that version may be a review-only fallback. If neither exists, consider the latest earlier CompatibilityVersion with an eligible candidate.
- For a **patch-unspecified target**, do not suggest a patch-specific value from the **same** CompatibilityVersion: without target patch precision the within-version earlier/later relationship is not known. Earlier CompatibilityVersions remain eligible for review-only suggestions.
- Across earlier CompatibilityVersions, prefer the closest earlier version with an eligible candidate, then the latest ordered exact patch in that version when multiple candidates exist. Only compare GamePatch values within their CompatibilityVersion, using existing suffix/order rules.
- If there is no unambiguous most relevant prior candidate, do not arbitrarily choose one. An exact target confirmation always takes precedence over suggestions.

Suggestion order chooses a useful **proposal**, not an inference that the drop chance remained unchanged.

## 6. Confirmation, rejection and revisions

A candidate suggestion is **not** automatically inserted as a stale database row when the player changes environments.

- **Accept:** explicitly create or replace a confirmed personal override for the *target* exact context using the proposed value. The original source confirmation remains unchanged; later editing either context does not rewrite the other.
- **Reject/dismiss:** persist the player's decision to decline reusing **that specific source estimate revision** for that target context, without creating a target numeric override or changing the source. The same rejected revision must not repeatedly reappear after reload or future context switches.
- Rejecting the best candidate must not automatically cascade to older candidates merely to replace the just-dismissed suggestion.
- A revised source estimate is a **different revision** and may be suggested again; the dismissal of its previous version does not hide the revised value. A newly confirmed more recent source can also become eligible under the ordinary selection rules.
- Rejection must not prevent the player from manually entering a fresh target estimate. Removing or editing a confirmed value affects that target, not unrelated confirmed contexts.
- Only explicit player writes persist confirmations, changes, dismissals or preferences. Mere browsing never does.

How revisions and dismissals are represented in persistence is an implementation decision; the visible lifecycle and ownership constraints above are normative.

## 7. Calculator selection and independent game knowledge

Preserve the player's **canonical-versus-personal input preference** for the exact context/relationship/model/conditions key. Switching to another context must not overwrite this preference; returning should restore the earlier choice.

A confirmed personal probability **may power a calculation even when no applicable accepted canonical numeric estimate exists**, but only if the source is independently known AVAILABLE, the relationship is independently resolved CAN_DROP, and the fixed independent-attempt model and its conditions are independently supported. A player typing 13% is not evidence for those prerequisites.

In particular, a personal rate cannot:
- change CANNOT_DROP into CAN_DROP, or unknown applicability into a verified drop;
- make an unavailable or unknown source actionable;
- make unsupported modifier-dependent probability mechanics into a supported fixed model;
- authorize calculation for an otherwise invalid/mismatched context.

Needs review suggestions cannot power personal calculations. Canonical calculations remain independent and usable when all canonical prerequisites hold. If the saved preferred input cannot currently be used, explicitly explain the unavailable input rather than silently calculate from a stale suggestion or claim to use a different value.

## 8. Privacy, context creation and write authorization

Only the owner may read or mutate their overrides, suggestion dismissals and calculation preferences, **including when someone has ADMIN curation authority**. Never treat a client-provided AccountContext identifier as proof of ownership.

Per V2.1.3, a valid first meaningful authenticated personal write may transactionally create/reuse the matching AccountContext for a recognized environment; invalid/foreign/unrecognized writes must not leave partial state. Browsing a new patch, league or version must create no personal records. Canonical shared knowledge stays independent of these owner-scoped writes.

## 9. Representative behavior

| Confirmed source | Selected target | Expected behavior |
|---|---|---|
| HC SSF Standard 3.30.1 — 13% | Same league/ruleset, 3.30.1 | Original confirmed 13%, preference restored |
| HC SSF Standard 3.30.1 — 13% | Same league/ruleset, 3.30.2 | Suggest 13% from 3.30.1; needs review |
| HC SSF Standard 3.30.1 — 13% | HC SSF Challenge 3.30.1 | Absent; no cross-league suggestion |
| HC SSF Standard 3.30.1 — 13% | SC SSF Standard 3.30.2 | Absent; no cross-league suggestion |
| HC SSF Standard 3.30 (patch unspecified) — 13% | Same league/ruleset, 3.30.1 | Review-only suggestion, not broad applicability |
| HC SSF Standard 3.30.2 — 11% | Same league/ruleset, 3.30 (patch unspecified) | No same-version precise-to-unspecified suggestion |
| HC SSF Standard 3.30.1 — 13%; 3.30.2 — 11% | Same league/ruleset, 3.30.3 | Suggest nearest earlier 11% from 3.30.2 |
| HC SSF Standard 3.30.1 — 13%, dismissed for 3.30.2 | Same league/ruleset, 3.30.2 reopened | Do not resuggest that source revision |
| HC SSF Standard 3.30.1 — 13%, independent confirmed 3.30.2 — 12% | 3.30.1, then 3.30.2 | Original confirmed 13%, then confirmed 12%; neither is overwritten |

The examples assume the same authenticated owner, relationship/model/conditions unless stated otherwise. The readable league names are illustrative: actual LeagueDefinition identity governs. A suggestion never implies the acquisition is currently calculable.

## 10. Explicit scope limits

This document defines **business semantics**, not an enum hierarchy, data-table design, exact UI labels, or API contracts. Milestone 6 can implement domain model, persistence and uniqueness, estimate selection, context-change behavior, and editing UI in focused increments.

The V2.1.3 environment selector permits optional precise GamePatch when supported metadata exists; a curated patch registry/selector is a separate concern. A missing registry must not force invented patches or block domain-level tests of exact-patch applicability.

Deferred: cross-league copying, implied Challenge/Standard equivalence, conditional probability engines, automatic game/patch inference, personal-authoritative source/drop applicability, anonymous persistent overrides, and speculative cross-context auto-activation.

## 11. Normative precedence

For personal DropRateOverride confirmation, stale suggestions, patch precision, dismissal and calculator choice, **V2.1.4 supersedes conflicting V2.1.1 language**, especially claims that a saved override itself permanently becomes STALE when the selected EvaluationContext changes.

V2.1.2 continues to govern independently accepted Acquisition knowledge; V2.1.3 continues to govern player game-environment selection, LeagueDefinition identity and automatic AccountContext resolution. Frozen V2.1.1 remains authoritative on unaffected matters. Implementation decisions cannot silently change these normative rules.
