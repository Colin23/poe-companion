# PoE Companion — Game Context, Personal Identity and Public Access Amendment V2.1.3

**Status:** Canonical normative amendment upon merge
**Date:** 2026-10-22
**Purpose:** Record the agreed player-facing environment-selection behavior, account-context identity rules, and phased public-access direction discovered during Milestone 5.2 C2 planning.

This amendment is authoritative only for the product, domain and user-journey concepts it explicitly refines. The V2.1.1 baseline and V2.1.2 Acquisition amendment continue to govern everything else. It does not silently make post-MVP features part of V0.1.

---

## 1. Product principle: game knowledge versus personal state

PoE Companion uses the **same real, curated game knowledge** regardless of whether a visitor has persistent personal data. Application authentication determines access to durable personalized state; it must not determine whether Uniques, Build requirements, Acquisition sources or estimates are authentic or curated.

Do not introduce fake item definitions, fixture values or invented probabilities as a guest-mode substitute for missing production curation. Synthetic development fixtures remain test/development data, separate from production ingestion and metadata.

Keep distinct:

- **Game-environment selection:** what game, league and version knowledge should apply.
- **Personal AccountContext:** whose saved ownership, attempts, goals and later snapshots belong to a particular league/ruleset.
- **Authentication:** how an application user is identified when storing or retrieving personal state.
- **PoEConnection / synchronization:** optional authorization to observe a GGG account and the separately validated process of refreshing personal snapshots.

A visitor can understand game knowledge without an AccountContext or PoEConnection. Personal state is never inferred from the presence of canonical data.

## 2. One player-facing game-environment selector

The normal user journey presents **one coherent environment selection**, not separate mandatory game-context and AccountContext dropdowns.

The selectable concepts are structured, including:

- `CompatibilityVersion` (e.g. `3.30`), independently of league lifecycle.
- League type: `CHALLENGE` or `STANDARD`.
- Mortality: `SOFTCORE` or `HARDCORE`.
- Participation: `TRADE` or `SSF`.
- Ruleset: `NORMAL` for V0.1; `RUTHLESS` is deliberately unsupported.
- Optional precise `GamePatch` (including applicable suffixes), when supported metadata is available.
- `GameRealm` where relevant. V0.1 may fix the supported realm rather than exposing an unusable control.

The exact UI layout is not prescribed by this amendment. The user should not ordinarily have to choose or type a Challenge League's marketing name. Instead, the application resolves the requested structured environment to an **actual, curated `LeagueDefinition`**, and derives a human-readable name and short summary. A useful compact display convention is `3.30 SC SSF Hocus` or `3.30 HC SSF Standard` (Hocus is fictional example data). Normal and Trade may be omitted from the short label by convention; distinctions must remain explicit and accessible in the full selection.

Display the currently selected environment consistently on Browser pages, for example in the shared header. Do not expose UUIDs as the primary navigation labels.

The UI may offer dependent controls and omit options that are genuinely unavailable, but never creates a new `LeagueDefinition` from an arbitrary submitted combination. Unknown, ambiguous and unsupported combinations must have explicit outcomes rather than guessed identities.

## 3. Compatibility version is not Challenge League identity

A compatibility version is not interchangeable with the season that launches near it. Challenge Leagues have lifecycles; Standard is permanent, and a compatibility version may remain in use when no Challenge League is active. Standard also exists during Challenge Leagues.

Therefore:

- Never assume that each compatibility version has exactly one active Challenge League.
- Never infer that a Challenge League remains active solely from the selected version.
- Store and curate the actual league identity and its structured dimensions independently of version.
- Maintain enough league lifecycle/association metadata to distinguish upcoming, active, ended and historical choices when those features are implemented. The current `LeagueDefinition` does **not** yet provide this data.
- A version choice must remain meaningful for Standard without selecting a Challenge League.
- A historical view may require deliberately selecting an ended league; exact UX and lifecycle metadata requirements remain open for later curation work.

A recognized league is not a claim that every Build, Unique or Acquisition fact is verified there. **Discoverable environments** and **the applicability/availability of individual facts** remain separate. Resolve each fact through its applicable `EvaluationContext` and retain UNKNOWN/not-verified outcomes when evidence is insufficient.

The current `EvaluationContext` remains a value object, with `CompatibilityVersion`, `Ruleset`, optional `GamePatch` and optional league identity as applicable. Preserve strict dimension-aware matching. Patch-specific knowledge must not silently match a patchless selection that lacks required precision.

## 4. Personal contexts are resolved, not normally selected

`AccountContext` remains a stable personalized state boundary; it is not the user's primary selector. When an authenticated visitor selects a game environment, the application automatically resolves the matching personal context by:

```text
(owner ApplicationUserId, selected LeagueDefinitionId, selected Ruleset)
```

**V0.1 invariant:** at most one AccountContext exists per owner/league/ruleset combination. Enforce this at the database boundary, including concurrency; do not make ordinary users choose between duplicates.

`CompatibilityVersion` and `GamePatch` do not belong in that uniqueness key. In particular, Standard ownership persists as the selected compatibility version changes. The personal context remains the same identity while applicable game knowledge may change.

Resolution results:

- **Matching owned context:** use that context's authoritative personal data.
- **No matching context:** game knowledge remains browsable; personal quantities, attempts and readiness are unavailable/unevaluated, **not zero**.
- **Duplicate preexisting contexts:** fail safely on migration; never automatically merge, delete or arbitrarily choose one.

The authenticated owner constraint is mandatory even for ADMIN; curator privileges never confer access to another player's personal data. Do not infer ownership merely from a supplied UUID or from what a UI happens to render.

Selecting or switching an environment does not itself synchronize data, manufacture an AccountContext, or reset any ownership, attempts, goals or saved state. Returning to a previously selected league reuses its existing personal context.

## 5. First persistent personal edit

For an authenticated user, the first meaningful personal write in a recognized environment may create its missing `AccountContext` automatically within the same transaction as the write. The user must not be required to understand a separate AccountContext-creation form.

The write path must validate the environment, the user and the operation; create or reuse the unique owned context without a race; and apply the personal change atomically. No partial context creation should survive a failed write.

Maintain existing sparse-ownership semantics: absent ownership records represent zero owned copies **within an established personal context**. An initial no-op ownership quantity of zero need not create a context. Recorded zero `AvailableAttempts` is different from absent attempts and may require a context to persist.

Do not create personal records merely because a visitor browses or changes an environment.

## 6. Future visitor and identity experience

The intended public experience does not require a PoE Companion-specific password account:

1. **Current V0.1/development:** keep the site private behind the existing temporary configured USER/ADMIN logins. Their separate owner identities remain useful for isolation tests.
2. **Initial public release:** anonymous guests may browse the real curated corpus and use temporary, user-entered quantities/attempts for calculations. These are real calculations over canonical data; temporary inputs are neither authoritative saved player data nor synthetic fixture values.
3. **Persistent personalization:** guests may sign in using an established third-party authentication provider (Google is an illustrative option, not a mandated vendor). Map authenticated external identities to stable application-owned `ApplicationUserId` records. Avoid building a bespoke password-management system.
4. **Future GGG integration:** an authenticated user may connect at most one GGG account to enable authorized data synchronization, when access is actually available. GGG authorization is distinct from application sign-in and from the current browsing selection. A successful connection is not a successful sync.
5. **Optional future provider login:** if GGG provides appropriate identity/OIDC guarantees, it may be evaluated as an additional sign-in option; otherwise the existing sign-in provider can remain. Account linking must be deliberate and must never silently merge users by matching display names or email assumptions.

The public-release experience may thus include a guest, a signed-in user without GGG connection, and a signed-in user with connection; these are implementation states, not three mandatory account-configuration screens. There is no product requirement to support several simultaneously connected GGG accounts per Companion user.

No guest input may be written to personal PostgreSQL tables under an invented shared guest identity. A future browser/session-local draft can support experimentation; its lifetime, limits and sign-in handoff are not yet specified. Persistent edits require authentication and owner-scoped authorization.

## 7. PoE data synchronization remains a separate authority

Connection and synchronization remain post-MVP and dependent on external access. GGG availability must not block the public usability of manually maintained collections.

Changing league/version in the UI simply selects which **already stored** personal state is read; it must never automatically fetch or overwrite GGG data. Synchronization is a separately controlled, observable operation. Only a complete, validated `AccountSnapshot` can replace the active authoritative synchronized state. Partial failures preserve the previous active state.

The canonical ownership authority rule remains: **MANUAL or SYNCHRONIZED**, never an implicit sum. No speculative reconciliation or provider DTO leakage is authorized by this amendment.

## 8. Milestone 5.2 C2 scope and deferrals

C2 establishes the internal foundations for this experience while retaining temporary private-site authentication:

- Make game selection independent of owned AccountContexts, with validated structured choices.
- Preserve/migrate the per-user selected environment, including version/patch and explicitly absent selections.
- Enforce the per-owner/league/ruleset AccountContext uniqueness invariant.
- Resolve matching personal contexts automatically; first meaningful authenticated edit may create one transactionally.
- Let Browser/Acquisition knowledge be evaluated from a selected `EvaluationContext` even when no personal context exists.
- Separate no selection, no personal context, missing saved attempts, recorded zero attempts, absent knowledge, and zero ownership within a personal context.
- Keep selected environment visible and human-readable; use POST/Redirect/GET and CSRF for changes.
- Preserve existing fixture edits, owner isolation and restart safety, with PostgreSQL/MVC regression tests.

C2 does **not** introduce: unauthenticated HTTP access, public sign-in/identity providers, GGG OAuth or synchronization, guest draft storage, production league lifecycle ingestion, generic patch catalog ingestion, Ruthless, or a broad UI redesign. Synthetic development fixtures are not evidence that production league metadata is complete.

## 9. Change boundaries and remaining open questions

This is a product/domain and user-journey refinement, not an instruction to commit to a particular schema, class layout, authentication vendor, or controller shape. Those belong to reviewed implementation decisions.

For later work, settle: official league lifecycle/provider mapping and ambiguity handling; how real compatibility/patch choices are curated; exactly how historical league selection works; guest draft lifetime; account-linking and recovery policies; authentication security/operational review before public launch.

Where this amendment refines older phrases such as “select an AccountContext”, interpret them as **choose an environment and resolve an owned personal context automatically**. It does not eliminate AccountContext or EvaluationContext as distinct domain concepts.

---
