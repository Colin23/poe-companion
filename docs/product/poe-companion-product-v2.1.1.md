# PoE Companion — Product V2.1.1

**Status:** Canonical product vision
**Target Game:** Path of Exile 1
**Primary Mode:** Solo Self-Found
**Purpose:** Describe why the product exists, who it serves, and the long-term product shape without duplicating detailed domain or MVP rules.

---

## 1. Product Vision

PoE Companion answers one central question:

> **What can my Path of Exile account do right now, and what can it become next?**

Path of Exile already exposes enormous information about individual items, characters, encounters and builds. SSF creates a different information problem: the value of an item depends heavily on the state of one specific account.

The Companion connects:

```text
what the account owns
        ↓
what builds those items make possible
        ↓
what important pieces are missing
        ↓
where those pieces can be obtained
        ↓
how realistic a target farm is
```

The product is an **account progression companion**, not a Trade-economy optimizer.

---

## 2. Product Philosophy

### SSF-first

The primary product language is account access, optionality and progression rather than market value.

Trade prices are not part of the core product.

### Account state matters

The same Unique can be transformative for one account and irrelevant to another because complementary items differ.

### Explainability over scores

The product should prefer:

```text
PARTIAL
All enabling groups satisfied
Core: 4 / 5
Missing: Item X
Item X has these acquisition sources
```

instead of pseudo-precise readiness percentages.

### Present possibilities, not instructions

The Companion may show:

```text
what is possible
what is missing
what can produce it
how many attempts exist
what the probability is
```

It does not tell the player:

> Farm X next.

### Progression should be visible

A useful SSF account grows in capability:

```text
larger Unique collection
new build options
more encounter access
completed targets
better future optionality
```

The product should make those changes understandable.

---

## 3. Primary User

The initial user is an experienced PoE1 SSF player who:

- understands the game already,
- plays a league for many hours,
- thinks in terms of account progression rather than one isolated character,
- collects build-enabling Uniques,
- farms bosses with explicit item targets,
- values future character optionality,
- already keeps mental or written account goals.

V0.1 is a power-user tool and does not need beginner onboarding.

---

## 4. Core Product Loop

```text
Observe current account
        ↓
Understand current capabilities
        ↓
Inspect build possibilities
        ↓
Identify missing requirements
        ↓
Inspect acquisition paths
        ↓
Evaluate target-farm probability
        ↓
Player chooses what to pursue
        ↓
Account changes
        ↓
New possibilities appear
```

The emotionally important product moment is:

> **A drop can change the possibility space of the account.**

---

## 5. Major Product Areas

### Account & Collection

Represent current ownership for one isolated AccountContext.

### Unique Catalog

Maintain knowledge of all relevant Uniques, not only owned ones.

### Build Knowledge

Represent curated BuildArchetypes, meaningful BuildVariants, versioned BuildVariantRevisions and their Unique requirements.

### Unique Readiness

Answer whether the account satisfies the tracked Unique setup for a particular BuildVariantRevision.

Canonical states are:

```text
BLOCKED
PARTIAL
UNIQUE_READY
```

This is **Unique Readiness**, not full character readiness or build quality.

### Acquisition & Target Farming

Connect missing Uniques to possible AcquisitionSources and model target-drop probabilities where the game mechanics are sufficiently understood.

### Goals

Allow the player to keep account intentions visible without turning the product into a generic task-management system.

---

## 6. Build Optionality / Reroll Capital

A core long-term idea is that an SSF account accumulates **reroll capital**: assets that expand the set of plausible future characters.

The Companion should not assign a permanent intrinsic value to an item.

Instead, value emerges from relationships:

```text
Unique A alone
→ limited known relevance

Unique A + B
→ several partial builds

Unique A + B + C
→ Build X becomes UNIQUE_READY
```

A Unique with no current Build association is not “useless”. It merely has no known association in the current curated corpus.

---

## 7. Item → Build Discovery

The inverse of “what can I build?” is equally important:

> **What did this item unlock?**

When ownership changes, the product may later surface derived effects such as:

```text
+ Item X

Build A
BLOCKED → PARTIAL

Build B
PARTIAL → UNIQUE_READY
```

This should be informative rather than interruptive.

---

## 8. Build Data Philosophy

A build is not a PoB code.

The Companion separates:

```text
BuildArchetype
      ↓
BuildVariant
      ↓
BuildVariantRevision
      ↓
BuildReference[]
```

References are evidence.

Curated BuildVariantRevision requirements define the semantic Unique setup.

Population popularity, item co-occurrence and PoB equipment may help discovery later but do not automatically define requirements.

---

## 9. Acquisition Philosophy

`AcquisitionSource` is broader than `Boss`.

Possible sources include:

```text
boss / encounter
league mechanic
divination card
vendor recipe
global drop
other deterministic source
```

V0.1 provides rich probability support only for suitable encounter-style sources.

The product should never fabricate a drop rate to make a calculator work.

---

## 10. Probability Philosophy

The product answers questions such as:

> Given these attempts and this supported DropEstimate, how likely am I to see at least one target drop?

Expected attempts are shown as expectation, not guarantee.

Probability is conditional on the assumptions of the selected estimate.

Some real PoE drops are affected by encounter modifiers such as area quantity. The long-term product therefore needs probability models richer than one universal fixed percentage, but V0.1 intentionally implements only the fixed independent-attempt case.

---

## 11. Account Synchronization Direction

The long-term preferred account experience uses official GGG account APIs to observe stash and character inventory/equipment.

However, the product hypothesis must not depend on external OAuth access.

Manual ownership is a first-class V0.1 mode.

Future synchronized ownership produces the same conceptual `CurrentOwnership` consumed by downstream product logic. `CurrentOwnership` represents transferable account gear available to satisfy build requirements; provider-observable but permanently bound/consumed gear is not counted merely because it is visible to the API.

---

## 12. Goals

Goals provide player context, for example:

```text
Acquire Nimis
Prepare Build X
Get four Voidstones
```

They remain thin references to existing domain knowledge rather than copied boss/build/item data.

Recurring personal milestones may later be saved as reusable GoalTemplates across AccountContexts with fresh progress.

---

## 13. Primary Product Screens

The core product naturally leads to:

```text
Dashboard / account overview
Unique Catalog
Unique Detail
Build Browser
Build Detail
Acquisition Browser
Acquisition Detail / Probability
Goals
Curator views
```

Exact interaction design is specified as each screen is implemented. Product behaviour matters more than pixel-perfect upfront layout.

---

## 14. Explicit Non-Goals

The product does not initially attempt to become:

```text
Trade price tracker
Div/hour optimizer
full PoB replacement
complete rare-item evaluator
craft planner
passive-tree evaluator
automatic farming-strategy engine
in-game overlay
social/community platform
ML recommendation system
```

---

## 15. Product Success

The product is useful when it repeatedly creates moments such as:

```text
I found this Unique.
→ I can see what it changes.
→ I discovered a newly plausible build.
→ I can see its remaining missing item.
→ I can see realistic acquisition options.
→ I understand the target-farm probability.
→ I decide whether I care.
```

---

## 16. One-Sentence Definition

> **PoE Companion turns account ownership into understandable build optionality and target-farming context.**

---

## 17. Guiding Question

Whenever a feature is considered, ask:

> **Does this help the player understand what their account can do now or what it could become next?**
