# PoE Companion — Core User Journeys V2.1.1

**Status:** Canonical behavioural specification **Purpose:** Describe observable end-to-end behaviour without
prescribing database schema or frontend framework.

---

## 0. Independent Application States

Keep separate:

```text
Application Access
PoE Account Connection
Account Synchronization
```

Examples of valid combinations:

```text
App authenticated + PoE not connected
PoE connected + never synchronized
PoE connected + last sync failed + previous snapshot still active
```

---

# Journey 1 — Use the Companion Without PoE Connection

The authenticated single-user application remains useful without a connected PoE account.

The user can:

- browse Uniques,
- browse builds,
- browse AcquisitionSources,
- use supported probability calculators,
- maintain manual collection/attempts/goals in V0.1.

PoE account connection is not a prerequisite for the core product.

---

# Journey 2 — Select an Account Context

The user chooses a human-friendly context such as:

```text
CURRENT
SSF 3.30

PREVIOUS
SSF 3.29
```

Internal flags/IDs are not exposed as the primary UI.

Previous AccountContexts preserve their own historical state. Every build/acquisition evaluation also uses an explicit
EvaluationContext (CompatibilityVersion and Ruleset, plus patch and league applicability when relevant) so historical or
Standard account state is never accidentally evaluated against unrelated current knowledge.

Ruthless may be visible as unsupported but never silently treated as normal ruleset.

---

# Journey 3 — Start a Fresh League

A new AccountContext begins with fresh personalized state:

```text
Manual ownership: empty
Available attempts: empty
Goals: empty unless explicitly copied
Snapshots: none
```

Canonical game/build knowledge may be reused only when verified/available for the new compatibility context.

Post-MVP, reusable GoalTemplates may be copied with fresh progress. V0.1 does not require this behaviour.

---

# Journey 4 — Current vs. OUTDATED Builds

If the EvaluationContext's CompatibilityVersion is `3.31` and a Variant was last verified for `3.30`, its current
revision is `OUTDATED`.

By default it is excluded from:

```text
Build discovery
Unique Readiness
account matching
```

The user may show historical revisions explicitly.

No current-account readiness is displayed for an OUTDATED revision.

Curator review creates/verifies a current revision.

---

# Journey 5 — Browse the Unique Catalog

The catalog contains all known relevant Uniques, independent of ownership.

The user can browse/filter at least by useful V0.1 dimensions such as:

```text
All / Owned / Missing
name
item class
tier when known
```

Each item has application-owned identity even when imported from external sources.

---

# Journey 6 — Change Manual Ownership

The user changes:

```text
Unique X: 0 → 1
```

The current collection updates immediately.

Derived readiness is recalculated from CurrentOwnership.

No items are reserved for other builds.

One owned Unique may therefore affect many BuildVariantRevisions at once.

---

# Journey 7 — Inspect a Unique

A Unique detail view acts as a junction for:

```text
identity/display metadata
current ownership + quantity
Build associations + requirement importance
AcquisitionSources
external references
```

If there are no curated build associations, display that fact without concluding the item is useless.

---

# Journey 8 — “What Can I Build?”

The user browses current verified BuildVariants grouped by:

```text
UNIQUE_READY
PARTIAL
BLOCKED
```

Within a readiness group, upgrade completeness may help ordering.

The list does not claim which build is stronger or preferable.

Each build is evaluated independently against the entire current account ownership.

---

# Journey 9 — Inspect a Build Variant

The view shows:

```text
Archetype / Variant
CompatibilityVersion / verification state
UniqueReadiness
ENABLING groups
CORE groups
UPGRADE groups
owned/missing details
BuildReferences
```

Meaning:

```text
BLOCKED
→ at least one ENABLING group missing

PARTIAL
→ all ENABLING satisfied, at least one CORE missing

UNIQUE_READY
→ all ENABLING and CORE satisfied
```

---

# Journey 10 — Variant With No Required Uniques

If ENABLING and CORE are both empty:

```text
No required Uniques
```

is the primary UI message.

UPGRADEs may still be shown separately.

---

# Journey 11 — Alternatives and Quantities

### ANY

```text
CORE
ANY OF:
✓ A
□ B
```

The group is satisfied.

### Quantity

```text
CORE
Ring X
1 / 2
```

The requirement is not satisfied until quantity is 2.

### V0.1 predicate semantics

Groups are evaluated independently and do not consume items from a shared allocation pool.

Example:

```text
Group 1: A
Group 2: A OR B
Owned: one A
```

Both groups are true in V0.1.

### Known unsupported combinatorial case

A build requiring:

```text
slot 1: A
slot 2: A OR B
```

cannot be modeled correctly by V0.1 when only one A is owned.

Curator UI must not pretend such a representation is exact. A future richer requirement model will support it.

---

# Journey 12 — Build Missing a Unique → Item Detail

From a PARTIAL/blocked Build detail, the user follows a missing Unique to its Item detail.

The product does not immediately prescribe one farming source because an item can have multiple AcquisitionSources.

---

# Journey 13 — Browse an AcquisitionSource Directly

The user can navigate directly to an encounter such as:

```text
Acquisition Sources
→ Bosses
→ Uber Eater of Worlds
```

The source view can show known target items and probability availability independently of any Build or Goal.

---

# Journey 14 — Fixed-Probability Target Farming

For a supported V0.1 DropEstimate:

```text
Target: Item C
Source: Boss Y
Attempt unit: one completed Boss Y encounter
Probability model: FIXED_BERNOULLI
p = 10%
Available attempts = 12
```

show:

```text
Probability of >=1: 71.8%
Expected attempts: 10
50%: 7
75%: 14
90%: 22
95%: 29
99%: 44
```

Expectation is not presented as guarantee.

---

# Journey 15 — Unknown or Unsupported Probability

If the DropRelationship is known but no supported DropEstimate exists:

```text
Drops from Boss X
Drop chance: Unknown
```

No fake calculator result is shown.

Likewise, if the real drop probability materially depends on modifiers that V0.1 cannot model, the product should mark
the calculation unsupported/conditional rather than flattening it into a misleading fixed `p`.

---

# Journey 16 — Personal Drop-Rate Override

The source can display:

```text
Default estimate: 2.0%
My estimate: 2.5%
```

The user selects which supported value powers the calculator.

The personal override never overwrites canonical data.

If its probability applicability context no longer matches the active EvaluationContext — including a relevant mid-cycle
GamePatch change — it displays `STALE` until reviewed.

---

# Journey 17 — Goals as Context

The user creates a Goal with free text and optionally one typed target.

Examples:

```text
Acquire Nimis
→ target UniqueDefinition(Nimis)

Prepare RF Chieftain
→ target BuildVariant(RF Chieftain / Variant X)

Farm Boss Y
→ target AcquisitionSource(Boss Y)

Get 4 Voidstones
→ no typed target required
```

The Goal view derives existing domain context rather than copying it.

---

# Journey 18 — Favorite Goals Across Leagues (Post-MVP)

A recurring goal can become a GoalTemplate.

At new league start, copying it creates a new Goal in the new AccountContext with fresh completion state.

Historical completion remains historical.

---

# Journey 19 — Connect PoE Account (Post-MVP)

Application login already exists.

The user authorizes a PoEConnection separately.

Connection alone does not create CurrentOwnership.

State may be:

```text
CONNECTED
NEVER SYNCED
```

---

# Journey 20 — Successful Account Sync (Post-MVP)

The user triggers synchronization.

Friendly progress may show stages such as:

```text
Reading stashes
Reading characters
Normalizing
Validating
Updating collection
```

The sync adapter enumerates every supported observable item container and applies an explicit contribution policy.
Transferable stash/inventory/equipment/rucksack/passive-jewel contents contribute to CurrentOwnership; permanently
bound/consumed containers such as Animate Guardian equipment do not.

Only after complete validation does the new snapshot become active.

OwnershipMode becomes/uses SYNCHRONIZED according to the future explicit transition workflow; manual and synchronized
quantities are never implicitly added.

---

# Journey 21 — Failed or Partial Sync

Example:

```text
stashes ✓
characters ✗
```

Result:

```text
Sync failed
previous active snapshot remains current
```

No partial ownership becomes visible as authoritative state.

---

# Journey 22 — Item Moves Between Stash and Character

If the same Unique moves location, aggregate CurrentOwnership remains unchanged.

Stable item IDs may preserve physical instance history, but readiness depends only on authoritative aggregate quantity.

---

# Journey 23 — Item Actually Disappears

After a later **complete** snapshot shows lower quantity, CurrentOwnership changes.

Readiness may move backwards, for example:

```text
UNIQUE_READY → PARTIAL
```

The application does not guess why the item disappeared.

---

# Journey 24 — Recent Account Changes

Comparing complete snapshots can derive:

```text
+ Item A
- Item B
quantity changes
Build X: BLOCKED → PARTIAL
Build Y: PARTIAL → UNIQUE_READY
```

No modal interruption is required.

A “Recent changes / Since last sync” surface is preferable.

---

# Journey 25 — Import a BuildReference (Post-MVP)

The user supplies a PoB/reference.

A future PoB importer may decode and normalize it.

If the normalized reference hash matches an existing reference exactly, reuse it.

Do not claim near-duplicate or semantic identity from the hash.

---

# Journey 26 — Classify a BuildReference

The curator associates a BuildReference with a BuildVariantRevision.

The reference provides evidence.

The curator owns ENABLING/CORE/UPGRADE semantics.

A future multi-user submission must not automatically rewrite canonical classification.

---

# Journey 27 — Curate Requirements

The curator defines requirements for a BuildVariantRevision.

V0.1 validation should support:

```text
positive quantities
ALL/ANY groups containing at least one Requirement
no duplicate UniqueDefinition inside one group
resolvable UniqueDefinition references
clear warning/documentation for unsupported combinatorial physical-allocation cases
```

If a real build cannot be represented honestly, the model should be extended later rather than encoding known-wrong
readiness.

---

# Journey 28 — Revise a Build Between Compatibility Versions

A new CompatibilityVersion makes the old revision OUTDATED.

Curator review may create/verify a new revision. Draft revisions may be edited while being prepared. Once
activated/verified, readiness-relevant semantic contents are immutable; a later semantic correction creates a new
revision which atomically supersedes the previous active revision. For each `(BuildVariantId, CompatibilityVersion)`, at
most one revision may remain active/eligible for matching. Older/superseded revisions remain inspectable for historical
contexts.

---

# Journey 29 — Catalog Import Identity Conflict

A catalog refresh sees an external record that cannot be matched unambiguously to a UniqueDefinition.

The importer does not guess.

Result:

```text
candidate CatalogRevision invalid
no activation
identity conflict visible to curator
previous active CatalogRevision remains active
```

V0.1 does not partially activate the unaffected records. The curator resolves the mapping and rebuilds/retries the
candidate import. The same fail-safe applies when the importer cannot prove that the configured provider result is
complete, for example because pagination/batches are incomplete or unexpectedly truncated. Mere disappearance of a
previously observed provider record never silently deletes the existing UniqueDefinition or marks it retired.

---

# Journey 30 — Persistent Catalog Correction

The curator corrects provider-derived catalog data.

The correction is stored as app-owned data with provenance rather than mutating the upstream payload.

On the next provider refresh:

```text
provider data
→ normalize/reconcile identity
→ re-apply active catalog corrections
→ validate candidate
→ activate only if valid
```

A provider refresh never silently removes the correction.

---

# Journey 31 — Conditional Drop Model (Known Future)

A source/item relationship has drop probability influenced by encounter conditions such as item quantity.

V0.1 may display the relationship and explanatory note but does not use the fixed calculator unless an applicable fixed
estimate is valid.

Future UX may collect/model relevant conditions and calculate effective per-attempt probabilities.

The exact interaction is deliberately deferred until the first concrete implementation.

---

## Behavioural Requirements Summary

```text
R1  App auth, PoE connection and sync are separate.
R2  AccountContext is explicit and isolated.
R3  Unique Catalog exists independently of ownership.
R4  Current build matching uses only verified current revisions.
R5  UniqueReadiness uses ENABLING/CORE/UPGRADE semantics.
R6  Different builds never reserve ownership from each other.
R7  V0.1 groups are non-consuming predicates; combinatorial allocation is deferred.
R8  AcquisitionSources are independently browsable.
R9  Unknown/unsupported probability remains unknown/unsupported.
R10 DropEstimate includes meaningful attempt/condition semantics.
R11 Canonical and personal estimates remain separate.
R12 Goals have zero or one typed target and do not duplicate domain data.
R13 Future sync is atomic and one ownership authority is active.
R14 External import ambiguity is surfaced rather than guessed.
R15 PoB exact reference dedupe is not semantic build dedupe.
R16 Evaluations use an explicit EvaluationContext; AccountContext is not implicitly bound to a global current version.
R17 At most one BuildVariantRevision per (BuildVariant, CompatibilityVersion) is active for matching.
R18 DropRelationship applicability is version/context aware.
R19 CurrentOwnership includes only transferable contributing item containers; Animate Guardian gear does not contribute.
R20 Catalog identity conflict invalidates the whole V0.1 candidate revision; corrections persist as overlays.
R21 Activated BuildVariantRevision semantic contents are immutable; semantic changes create superseding revisions.
R22 EvaluationContext matching is strict; missing required patch/league/ruleset precision is not an implicit match.
R23 Catalog activation requires a complete provider observation; provider absence alone is not deletion/retirement evidence.
R24 Probability selection has at most one active canonical estimate and one active personal override per exact applicability/model key.
```

---

## Core Product Flow

```text
Select AccountContext
        ↓
Inspect CurrentOwnership
        ↓
Browse UniqueReadiness
        ↓
Inspect missing Unique
        ↓
Inspect AcquisitionSources
        ↓
Use supported probability model
        ↓
Player decides what to do
```

---

## Guiding Behaviour Rule

> **The UI should make domain state and uncertainty understandable without inventing certainty or making the player's
decision for them.**
