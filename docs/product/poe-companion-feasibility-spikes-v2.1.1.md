# PoE Companion — Feasibility Spikes V2.1.1

**Status:** Research snapshot **Verified:** 2026-09-22 **Purpose:** Record external/provider feasibility facts that
materially influence product or architecture. This file is time-sensitive and should be reverified before relying on
changing external conditions.

## 1. Summary

```text
Unique Catalog             GREEN
League Discovery           YELLOW (API credentials/access dependency)
GGG Account Sync           YELLOW (technically supported; registration/approval external)
PoB Parsing                GREEN
Fixed Boss Probability     GREEN
Conditional Drop Models    YELLOW (known real requirement; mechanics/data-specific)
```

No spike invalidates V0.1. V0.1 remains fully useful without GGG OAuth.

## 2. Unique Catalog — GREEN

PoE Wiki explicitly recommends Cargo API use to tool developers for structured item/mod/game data instead of scraping
rendered pages.

Useful catalog data includes concepts such as:

```text
name
item class
base item
metadata/source identity
version data
drop enabled/restriction data
acquisition-related metadata
```

Architecture consequence:

```text
PoE Wiki Cargo
→ provider DTO/raw data
→ Catalog adapter
→ normalized candidate
→ identity reconciliation
→ CatalogRevision
→ local database
```

Runtime browsing never depends on live Wiki availability.

### Identity caveat

Do not collapse source rows by displayed name. The application owns `UniqueDefinitionId` and stores provider identity
mappings. Ambiguous reconciliation requires curator review.

### RePoE

Technically useful as a possible future enrichment/cross-reference source, but explicitly **out of V0.1**. PoE Wiki
remains the only planned V0.1 catalog provider.

### poe.ninja

Not needed for V0.1 catalog or Trade pricing.

## 3. League Discovery — YELLOW

Official GGG API provides:

```text
GET /league?type=main
scope: service:leagues
```

and for authorized accounts:

```text
GET /account/leagues[/<realm>]
scope: account:leagues
```

Automatic league discovery is technically straightforward once suitable API application access exists.

### Current registration reality

Verified 2026-09-22: GGG's current developer documentation explicitly states:

> **We are currently unable to process new applications.**

Therefore new OAuth/API application registration is currently unavailable through the documented process. This is an
external dependency outside the project team's control.

### V0.1 decision

Manual CompatibilityVersion/LeagueDefinition/AccountContext activation remains the V0.1 mechanism because application
approval is an unnecessary critical-path dependency.

Automatic discovery remains post-MVP or opportunistic if credentials are obtained naturally.

## 4. GGG Account Sync — YELLOW

Official GGG APIs support the required conceptual inputs.

Account Stashes (PoE1):

```text
scope: account:stashes
GET /stash/<league>
GET /stash/<league>/<stash_id>
```

Account Characters:

```text
scope: account:characters
GET /character
GET /character/<name>
```

PoE1 Character responses currently expose multiple item arrays including:

```text
equipment
inventory
rucksack
jewels
guardian
```

Therefore future sync must not hard-code only stash + equipment + inventory. It must enumerate every supported
observable item container and classify whether its contents contribute to transferable CurrentOwnership.

Expected product policy: stash/inventory/equipment/rucksack/passive-tree jewels contribute; Animate Guardian equipment
does not because it is not transferable player gear.

Future complete observation:

```text
all relevant stashes
+
all supported character item containers
→ classify container contribution
→ candidate complete AccountSnapshot / CurrentOwnership
```

### Item identity

GGG Item `id` is optional. Physical-instance movement tracking is useful when available but must not be required for
correct aggregate ownership.

### Registration/access status

Account sync is technically feasible but **currently externally blocked for a new application** because GGG states it
cannot process new applications.

It remains YELLOW rather than RED because the APIs themselves match the architecture and the blocker is
external/temporary rather than conceptual. It stays outside the V0.1 critical path.

## 5. GGG Robustness Requirements

GGG documents API details/availability as changeable and rate limits as dynamic.

Applications must respect returned rate-limit headers and `Retry-After`, not hard-code RPS assumptions.

Architecture consequences:

```text
provider adapter boundary
server-side credentials/tokens
rate-limit/header-aware client
last valid local state remains usable
partial sync never activates
```

GGG documents websites/web apps as the safest application shape for OAuth/API integration, supporting the hosted-web
architecture.

Only documented APIs/data exports should be used.

## 6. Provider Schema Change Risk

Provider DTOs must never become Domain types.

Hard rule:

```text
External response type
→ adapter/normalization
→ internal model
```

No provider-specific response object crosses into unrelated domain modules.

## 7. PoB Parsing — GREEN

PoB share data is machine-readable without running the PoB UI.

Common process:

```text
URL-safe Base64
→ decode
→ DEFLATE inflate
→ XML
```

JVM support is routine. The hard problem is semantic classification, not decoding.

A normalized representation can be hashed to detect repeated **exact normalized references**. It cannot by itself detect
semantic or near-duplicate builds.

## 8. Fixed Probability Math — GREEN

For independent attempts with constant probability `p`:

```text
P(at least one in n attempts) = 1 - (1-p)^n
```

Vertical Slice:

```text
p = 0.10
n = 12
→ 71.757...% → 71.8%

95% threshold → 29 attempts
```

The important complexity is determining whether fixed-model assumptions are true for a particular DropRelationship.

## 9. Conditional Encounter Drops — YELLOW / Known Requirement

PoE does not have one universal rule that every boss-specific Unique has a modifier-independent constant drop chance.

Current PoE Wiki documentation records concrete Eldritch examples:

- Black Star and Infinite Hunger Unique drop chances scale with area item quantity.
- Forbidden Flesh/Forbidden Flame from Eater/Exarch have documented quantity-scaling behaviour.
- Eldritch invitations state that item quantity modifiers affect boss rewards.

Therefore the long-term domain must support probability models whose effective probability depends on encounter
conditions.

### V0.1 consequence

Implement only `FIXED_BERNOULLI` where constant per-attempt `p` is an honest model.

For other relationships:

```text
relationship can still exist
probability may be unsupported/conditional/unknown
no misleading fixed calculator result
```

### Future research before implementing a conditional model

Verify for the concrete drop:

```text
which drop scales
which quantity source matters
exact scaling rule
caps/nonlinear effects if any
whether attempts can have different conditions
```

Do not generalize from one encounter to all boss drops.

## 10. Unique Tier — GREEN as Enrichment

Community tiers are useful UI metadata but not canonical identity.

Treat them as versioned/reviewable enrichment. They do not block V0.1 catalog import.

## 11. Current Risk Register

### Low

```text
PoE Wiki Cargo catalog retrieval
manual collection
fixed probability math
Kotlin/JVM PoB decoding
server-rendered web application
```

### Medium / domain-data quality

```text
Unique identity reconciliation across odd variants
Wiki acquisition metadata → clean AcquisitionSource model
maintaining curated BuildVariant requirements
catalog changes between versions
conditional drop-rate mechanics
```

### External dependency

```text
GGG application registration/approval timing
GGG API availability/rate limits
```

These remain outside the V0.1 critical path.

## 12. Architecture Implications Confirmed

1. External provider adapters are mandatory boundaries.
2. Manual and synchronized ownership converge on CurrentOwnership.
3. Manual and synchronized sources are never implicitly added.
4. Catalog import is application ingestion, not a live frontend dependency.
5. PoB is a reference/import format, not Build identity.
6. GGG failure leaves previous local state usable.
7. Probability numbers require explicit model/attempt/condition semantics.
8. Conditional probability is an expected future capability, not speculative overengineering.

## 13. Feasibility Verdict

V0.1 chain is feasible without GGG OAuth:

```text
PoE Wiki
→ Unique Catalog
→ Manual CurrentOwnership
→ Curated Build Requirements
→ Unique Readiness
→ Missing Unique
→ AcquisitionSource
→ Supported fixed DropEstimate
→ Manual AvailableAttempts
→ Probability
```

Later synchronization is feasible in principle:

```text
GGG OAuth
→ stashes + characters
→ complete AccountSnapshot
→ CurrentOwnership
```

No external result justifies expanding V0.1 before the first vertical slices are built.

## 14. Provider Compliance Notes — Reverify Before Public Deployment

Current provider documentation also creates operational/compliance requirements that do not alter the V0.1 architecture:

- GGG API clients must follow current API policy, including an identifiable User-Agent and provider-required
  request/rate-limit behaviour.
- Before a public/broadly available release, verify and display any current GGG-required non-affiliation/non-endorsement
  notice.
- PoE Wiki Cargo is appropriate for tooling, but public redistribution/use of Wiki-derived content must be reviewed
  against the Wiki's current license/attribution terms.

These requirements are time-sensitive and should be reverified at the point they become relevant. They do not block the
private single-user V0.1.
