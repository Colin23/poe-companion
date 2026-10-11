# PoE Companion — Acquisition Domain Amendment V2.1.2

**Status:** Canonical normative amendment
**Date:** 2026-10-09
**Purpose:** Refine the V2.1.1 Acquisition domain before Milestone 4 implementation, based on real Path of Exile encounter structures.

This document is authoritative for the Acquisition concepts it explicitly refines.

Everything not addressed here retains its V2.1.1 meaning and scope.

---

## 1. Why the Refinement Is Needed

V2.1.1 intentionally used the broad concept `AcquisitionSource` and required Normal/Uber encounters to be separate sources when relevant behaviour differs.

That foundation is still correct, but a first implementation shape such as:

```text
AcquisitionSource
    id
    name
    type
```

would leave important domain questions unresolved.

Real Path of Exile content demonstrates all of the following:

```text
one boss can appear in several materially different encounters
one encounter can contain several bosses
the same displayed arena name can refer to different encounter data
regular and Uber encounters can share a boss/arena family but differ in access, level, mechanics, and drops
encounter access may depend on fragments, witnessed bosses, biome/depth, or other mechanics
encounter availability can change independently from any one source→item relationship
provider data spreads these facts across several record types rather than one canonical "encounter" object
```

The Companion therefore owns a normalized product-domain representation rather than copying PoEDB, PoE Wiki, or game-data table structure.

---

## 2. BossDefinition

`BossDefinition` represents the stable application-owned identity of a boss/game entity.

Conceptually:

```text
BossDefinition
    BossDefinitionId
    display metadata
```

Hard rules:

```text
BossDefinitionId is opaque and application-owned
display name does not define identity
provider monster IDs do not define identity
historical boss identity is not deleted merely because current content no longer exposes that boss
```

Examples:

```text
The Eater of Worlds
Atziri, Queen of the Vaal
The Elder
The Shaper
Aul, the Crystal King
Incarnation of Fear
```

A `BossDefinition` is not itself an AcquisitionSource.

The same boss may participate in several different AcquisitionSources.

---

## 3. AcquisitionSource

`AcquisitionSource` remains the generic canonical concept for one independently browsable way an item may be obtained.

It has stable, opaque, application-owned identity:

```text
AcquisitionSourceId
```

Display labels, arena names, provider IDs, area IDs, and participating boss sets do not define that identity.

For V0.1, the only product-required source kind is:

```text
BOSS_ENCOUNTER
```

Other long-term AcquisitionSource categories remain possible domain directions but are not required to be stored, curated, or displayed in V0.1.

### 3.1 BOSS_ENCOUNTER Meaning

A `BOSS_ENCOUNTER` AcquisitionSource represents:

> one independently meaningful acquisition opportunity whose encounter/access/drop semantics are coherent enough for the player to browse, target, and reason about as one source.

This is a product-domain identity, not a provider-record identity.

Normal/Uber or other variants are separate AcquisitionSources when their acquisition semantics are materially different, for example because access, drop pool, rate assumptions, encounter conditions, or relevant mechanics differ.

Do not split or merge source identity solely because:

```text
area level differs
provider area record differs
displayed arena name is the same
displayed arena name differs
provider monster variant differs
```

Those are evidence, not identity algorithms.

Whether two observations describe the same AcquisitionSource is a curator/domain decision based on product-relevant acquisition semantics.

---

## 4. Boss Participation

A BOSS_ENCOUNTER source may involve:

```text
one BossDefinition
multiple BossDefinitions
a configuration-dependent or witnessed roster
```

Therefore the model must not permanently assume:

```text
AcquisitionSource -> exactly one BossDefinition
```

and source identity must not be reconstructed from the sorted set of participating boss IDs.

Boss participation is canonical encounter knowledge useful for browsing, explanation, and curation.

It is not the source primary key.

Examples:

```text
regular Eater
    -> The Eater of Worlds

Uber Elder
    -> The Elder + The Shaper

Maven Crucible invitation
    -> several bosses in one encounter

generic/roster-driven Crucible progression
    -> participant set can depend on witnessed/configured bosses
```

---

## 5. AcquisitionSourceKnowledge

Mutable facts about a stable AcquisitionSource are conceptually separate from source identity.

This specification uses the term `AcquisitionSourceKnowledge` for that semantic boundary.

The name does **not** require one Kotlin class, database table, or lifecycle aggregate.

It may include context-dependent facts such as:

```text
availability
human-facing source label
arena/area information
participating bosses
access explanation
fixed area level when genuinely useful
other encounter details needed for browsing/explanation
```

Such knowledge is evaluated under explicit `EvaluationContext` applicability rather than an implicit global current version.

The eventual implementation may use revisions, change-point facts, or another explicit representation, provided the semantic separation remains intact.

---

## 6. Source Availability

A curated `AcquisitionSource` has stable source identity independently of whether that source is currently available in a particular game context.

Whether that already-known source is available as an acquisition opportunity in a given `EvaluationContext` is a source-level fact.

It is **not** inferred from the presence or absence of DropRelationships.

Canonical authored availability facts are:

```text
AVAILABLE
UNAVAILABLE
```

Evaluation can also yield:

```text
UNKNOWN / NOT VERIFIED
```

when no sufficiently applicable source-availability knowledge exists.

Meaning:

```text
applicable AVAILABLE knowledge
    -> the already-known source is available as an acquisition opportunity in this EvaluationContext

applicable UNAVAILABLE knowledge
    -> the source is positively known not to be available in this EvaluationContext

no sufficiently applicable knowledge
    -> do not guess; current availability is unknown/not verified
```

A source becoming unavailable does not delete its stable identity.

Historical contexts may still browse/evaluate historical source knowledge.

If essentially the same encounter later returns, the same stable AcquisitionSource may become AVAILABLE again. If the reintroduced content is materially a different acquisition opportunity, curator judgment may create a new AcquisitionSource identity.

---

## 7. Source Availability Is Not Drop Applicability

Keep these claims separate:

```text
"This encounter exists in this context."
"This encounter can produce Item X in this context."
```

The first belongs to source-level availability/knowledge.

The second belongs to `DropRelationship`.

Therefore:

```text
source AVAILABLE
+
DropRelationship for Item X DOES_NOT_APPLY
```

is valid.

Likewise:

```text
source UNAVAILABLE
```

must not be represented merely by deleting or disabling all of its DropRelationships.

---

## 8. Access Knowledge Is Not Probability Knowledge

The Acquisition domain distinguishes at least five questions:

```text
SOURCE AVAILABILITY
    Does this encounter exist/become runnable in this game context?

ACCESS KNOWLEDGE
    What enables or locates an attempt?
    Examples: fragments, invitation/witness state, Delve biome/depth.

DROP RELATIONSHIP
    Can this source produce Item X?

DROP ESTIMATE CONDITIONS
    Under what assumptions does probability p apply?

AVAILABLE ATTEMPTS
    How many attempts does this AccountContext currently have?
```

Do not collapse these into one generic `conditions` object.

A fact may matter to more than one question, but that relationship must be explicit rather than assumed.

Example:

```text
Aul requires suitable Delve context/depth to encounter.

That does not by itself prove:
AulUniqueDropProbability = f(depth)
```

V0.1 must not infer probability conditions from access conditions without evidence.

---

## 9. V0.1 Access Representation

V0.1 does not require a generic machine-evaluable encounter-access rules engine.

Because `AvailableAttempts` is manually maintained in V0.1, access knowledge may initially be curator-authored explanatory information sufficient for browsing and understanding.

Do not prematurely introduce a generic hierarchy such as:

```text
MinimumDepth
RequiredFragmentSet
RequiredWitnesses
RequiredBiome
RequiredQuestState
...
```

merely to make all known encounters machine-evaluable.

Structured access semantics may be added later when a concrete feature requires automatic attempt derivation or access validation.

---

## 10. Area and Arena Metadata

Area/arena information is useful encounter knowledge but does not define source identity.

Do not assume every BOSS_ENCOUNTER has one permanent integer:

```text
areaLevel
```

Real encounters may have:

```text
different levels for quest/repeatable/Uber forms
provider template levels that do not directly describe generated gameplay
version-dependent levels
variable or derived levels
no product need for area level at all
```

V0.1 may store/display exact area-level knowledge when it is known, fixed, and useful, but the domain must not require one universal permanent area-level field on AcquisitionSource identity.

The same principle applies to arena name and access item names.

---

## 11. Labels, Names, and Duplicate Protection

Human-facing names are metadata.

Canonical identity rules:

```text
BossDefinition.displayName != BossDefinition identity
AcquisitionSource.displayLabel != AcquisitionSource identity
areaName != AcquisitionSource identity
```

Hard domain validation should reject meaningless presentation data such as blank required labels where a label is required by the concrete workflow.

Do **not** establish identity with a constraint such as:

```text
UNIQUE(lower(trim(display_label)))
```

merely to prevent accidental duplicate curator entries.

Suspiciously similar labels are initially a curator-quality concern, not a proof that two source identities are impossible.

The curator/application boundary may normalize trivial presentation whitespace and warn about likely duplicates without making display text canonical identity.

---

## 12. Curator vs Player Ownership

Canonical Acquisition knowledge is curator/admin managed:

```text
BossDefinition
AcquisitionSource
source availability/details
DropRelationship
canonical DropEstimate
```

Personalized AccountContext state is player managed:

```text
AvailableAttempts
DropRateOverride
Goals referencing an AcquisitionSource
```

The ordinary player does not create a canonical "Uber Elder" or "Aul" source as part of normal target-farming use.

In V0.1 the developer may act as both curator and player, but the semantic responsibilities remain separate.

The minimal curator/admin capability required by the V0.1 scope may remain developer-oriented and visually simple.

---

## 13. Provider Boundary

PoEDB, PoE Wiki, and underlying game data are evidence sources, not domain schemas.

Provider data may expose relevant facts through separate records such as:

```text
world areas
monster definitions
map-device recipes
fragments/keys
invitations
Delve biomes/nodes
drop tables
```

The Acquisition module must normalize provider-specific observations into Companion-owned identities and canonical knowledge.

Never:

```text
provider area ID
provider monster ID
provider item key
    -> canonical AcquisitionSource identity by default
```

Provider identity mappings may be introduced explicitly when real ingestion requires them, just as Catalog external identity is separate from application-owned identity.

---

## 14. Model-Breaking Encounter Corpus

The following examples are normative design tests, not seed-data requirements.

Their purpose is to prevent implementation from choosing a representation that only works for simple one-boss/one-area encounters.

### 14.1 Eater / Uber Eater

Observed evidence:

```text
boss:
    The Eater of Worlds

arena/displayed area:
    Absence of Symmetry and Harmony

access examples:
    Screaming Invitation for regular forms
    Devouring Fragments for Uber

PoEDB exposes the same encounter-area family with level 83/84/85 recipes.
```

Model implication:

```text
boss identity != source variant
arena name != source identity
area level alone does not define identity
regular/Uber acquisition semantics may require separate source identities
```

Evidence:
- https://poedb.tw/us/Absence_of_Symmetry_and_Harmony
- https://poedb.tw/us/Devouring_Fragment

### 14.2 Atziri Variants

Observed evidence:

```text
boss:
    Atziri, Queen of the Vaal

different acquisition contexts include:
    The Apex of Sacrifice
    The Alluring Abyss / Uber Atziri
    Throne of Atziri in Temple of Atzoatl
```

Normal and Uber Atziri have materially different drop pools.

Model implication:

```text
one BossDefinition can participate in several AcquisitionSources
boss display name cannot define source identity
source availability/access must be encounter-specific
```

Evidence:
- https://www.poewiki.net/wiki/Atziri,_Queen_of_the_Vaal
- https://ptr.poedb.tw/us/Atziri%2C_Queen_of_the_Vaal

### 14.3 Uber Elder / The Shaper's Realm

PoEDB exposes several internal area records under the displayed name `The Shaper's Realm`.

One ordinary record lists The Shaper; the Uber Elder arena record `MapWorldsElderArenaUber` lists both The Elder and The Shaper.

Model implication:

```text
displayed arena name is not encounter identity
provider world-area record is not Companion source identity
one AcquisitionSource may involve multiple BossDefinitions
```

Evidence:
- https://poedb.tw/us/The_Shapers_Realm

### 14.4 Incarnation Regular/Uber Pair

For `Moment of Trauma`, PoEDB exposes:

```text
Incarnation of Fear
regular area record / Echo of Trauma access
Uber area record
Traumatic Fragment access
different area levels
```

Model implication:

```text
same boss/arena family can contain materially different acquisition opportunities
provider monster/area variants are evidence, not automatic identity
access, mechanics, area level, and drop semantics may vary independently
```

Evidence:
- https://poedb.tw/us/Moment_of_Trauma
- https://poedb.tw/us/Traumatic_Fragment

### 14.5 Aul / Delve

Aul demonstrates encounter access governed by Delve-specific context such as biome/node/depth rather than a simple map-device key.

Model implication:

```text
access semantics are not universally fragment-based
depth/biome access evidence must not automatically become a DropEstimate probability parameter
provider area-level metadata may not represent the product fact we need
```

Evidence:
- https://www.poewiki.net/wiki/Aul,_the_Crystal_King
- https://poedb.tw/us/Primeval_Citadel

### 14.6 Maven Crucible / Invitations

Maven invitations can require several specific bosses to have been witnessed and then open the Maven's Crucible, where those bosses are fought together.

Generic Maven progression can also involve collected/witnessed map bosses rather than one permanent fixed encounter roster.

Model implication:

```text
one source may contain several bosses
boss roster may be access/configuration-dependent
same destination arena can host materially different acquisition opportunities
fixed source.bossId and source.bossIds-as-identity are both insufficient
```

Evidence:
- https://poedb.tw/us/The_Maven
- https://poedb.tw/us/Mavens_Invitation%3A_The_Formed
- https://poedb.tw/us/Mavens_Invitation%3A_The_Twisted
- https://poedb.tw/us/Mavens_Invitation%3A_The_Forgotten

---

## 15. Hard Invariants Introduced by V2.1.2

Implementations must preserve:

```text
BossDefinition has application-owned stable identity
AcquisitionSource has application-owned stable identity
BossDefinition and AcquisitionSource are different concepts
BOSS_ENCOUNTER does not assume exactly one boss
display labels/area names/provider IDs do not define source identity
source availability is explicit version/context knowledge
UNAVAILABLE does not delete historical source identity
absence of applicable availability knowledge remains unknown/not verified
source availability is separate from DropRelationship applicability
access knowledge is separate from DropEstimate conditions
AvailableAttempts remains personalized AccountContext state
canonical source creation/editing remains curator/admin responsibility
```

---

## 16. Deliberately Deferred Design Work

V2.1.2 intentionally does **not** freeze:

```text
the exact Kotlin class/table name for AcquisitionSourceKnowledge
revision lifecycle vs change-point persistence for source knowledge
a generic access-condition expression language
automatic fragment/resource -> AvailableAttempts derivation
automatic Delve eligibility calculation
the exact representation of dynamic encounter rosters
the exact representation of variable/ranged/derived area level
provider identity mapping schema for boss/encounter ingestion
non-boss AcquisitionSource implementation
automatic source deduplication by names/provider records
```

Revisit these when a concrete product workflow or real provider ingestion requires them.

Implementation must document deliberate deferrals with a revisit trigger rather than leaving them only in chat.

---

## 17. Effect on Milestone 4 Implementation

Before implementing DropRelationships or probabilities, establish the minimum stable Acquisition foundation consistent with this amendment.

The first implementation PRs should be reassessed rather than blindly following the older tentative tracker wording.

Likely concerns to establish incrementally include:

```text
BossDefinition identity
AcquisitionSource identity for BOSS_ENCOUNTER
minimum source/encounter presentation metadata
source availability/applicability foundation
BossDefinition participation without assuming one boss per source
```

Do not introduce every future metadata field or access mechanic in the first PR merely because the amendment recognizes the distinction.

The implementation sequence should remain small, testable, and driven by the current vertical slice.
