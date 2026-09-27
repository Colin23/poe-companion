# PoE Companion — Repository Bootstrap & Vertical Slice Plan V2.1.1

**Status:** Canonical initial implementation plan **Purpose:** Turn the V2.1.1 product/domain/architecture decisions
into a concrete first repository and implementation sequence.

## 1. Starting Principle

Start with:

```text
1 repository
1 Gradle project
1 Kotlin Spring Boot application
1 PostgreSQL database
1 deployable
```

Do not initially create multiple Gradle modules, services, worker applications or a separate frontend project.

## 2. Initial Repository Shape

```text
poecompanion/
├── src/
│   ├── main/
│   │   ├── kotlin/.../
│   │   │   ├── PoECompanionApplication.kt
│   │   │   ├── catalog/
│   │   │   ├── league/
│   │   │   ├── account/
│   │   │   ├── build/
│   │   │   ├── acquisition/
│   │   │   ├── goal/
│   │   │   └── security/
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── db/migration/
│   │       ├── templates/
│   │       └── static/
│   └── test/kotlin/
├── gradle/libs.versions.toml
├── build.gradle.kts
├── settings.gradle.kts
├── compose.yaml
├── README.md
└── docs/
```

Provider adapters are nested in their owning module when introduced:

```text
catalog/integration/poewiki
account/integration/ggg
build/integration/pob
```

No top-level `import` package.

## 3. Domain-Oriented Packages

Do not create one global:

```text
controller/
service/
repository/
entity/
```

hierarchy.

A module may internally introduce `application`, `persistence`, `web`, `integration`, etc. once its complexity makes
those subdivisions useful.

## 4. Spring Modulith Bootstrap

Add Spring Modulith early and create an architecture verification test.

First purpose:

```text
find cycles
prevent access to module internals
make accidental coupling visible
```

Do not create application events or named interfaces merely for ceremony.

## 5. Initial Technology Baseline

Reference baseline:

```text
JDK 25 LTS
Kotlin 2.4.x
Spring Boot 4.1.x
Spring Modulith 2.1.x
Gradle 9.7.x
PostgreSQL 18.x
```

Use stable compatible patch releases when the repository is created.

## 6. Initial Dependencies

Start with only what the first vertical slice needs:

```text
Kotlin JVM
Kotlin Spring plugin
Kotlin JPA plugin

Spring Boot MVC
Spring Data JPA
Spring Security
Spring Validation
Spring Modulith
Thymeleaf
Actuator
DevTools (development)

PostgreSQL driver
Flyway
Jackson Kotlin

JUnit / Spring Boot Test
AssertJ
MockK when useful
Testcontainers PostgreSQL
Spring Modulith test support

Detekt
Spotless + ktlint
```

HTMX is a static frontend dependency and may be added when the first partial interaction uses it.

WireMock is added with Vertical Slice 2 (real HTTP provider).

jOOQ is not in bootstrap.

## 7. Build Configuration

Use:

```text
Gradle Kotlin DSL
Gradle wrapper
libs.versions.toml
Spring Boot BOM via Gradle native platform
```

Let Boot own versions of its tested ecosystem. Version catalog manages non-Boot-managed libraries/plugins.

Add dependency locking/verification after the basic build is stable.

## 8. Kotlin Baseline

```text
constructor injection
ordinary classes for JPA entities
data classes for DTOs/results
non-null by default
manual mapping
no field injection
no unnecessary lateinit
no coroutine architecture
```

No Java source tree.

## 9. Local Database

Use PostgreSQL from day one.

```text
local dev → Docker Compose PostgreSQL
tests     → Testcontainers PostgreSQL
production→ PostgreSQL
```

No H2.

## 10. Flyway

Schema lives in PostgreSQL SQL migrations:

```text
src/main/resources/db/migration/
V001__initial_schema.sql
...
```

Hibernate schema auto-update is not production schema management.

Prefer validation/detection of mismatch over silent DDL mutation.

## 11. JPA Baseline

Use Spring Data JPA/Hibernate for ordinary persistence.

Configure from the beginning:

```text
spring.jpa.open-in-view=false
```

Transactions live in application services, not controllers/templates.

## 12. Application Security Bootstrap

Initial production-like access:

```text
Spring Security
session login
one configured application user
```

Secrets outside Git.

Any internet-accessible deployment requires authentication and HTTPS.

GGG OAuth is not application login.

## 13. Presentation Bootstrap

Use Thymeleaf server-rendered HTML.

Start with ordinary forms/redirects.

Add HTMX only when a specific interaction benefits.

Templates render already-computed view models; they do not calculate readiness/probability.

Initial CSS target:

```text
readable
consistent
information-dense
usable
```

No design-system project.

## 14. V0.1 Domain Semantics to Encode Before UI Polish

Implement/test these contracts explicitly:

```text
readiness states
ALL/ANY
positive quantities
cross-build ownership is never reserved
V0.1 groups are non-consuming predicates
unsupported combinatorial allocation is known/deferred
fixed probability edge rules
unknown probability != zero
Goal has 0/1 typed target
```

## 15. Vertical Slice 1 Data

Development fixture:

```text
A = owned 1
B = owned 1
C = owned 0

Build X:
ENABLING A
CORE B
CORE C

Boss Y:
DropRelationship → C
Attempt: one completed Boss Y encounter
FIXED_BERNOULLI p = 0.10
AvailableAttempts = 12
```

## 16. Vertical Slice 1 Behaviour

```text
Collection shows A/B owned, C missing
→ Build X = PARTIAL
→ missing C links to Unique C
→ C links to Boss Y
→ Boss Y shows 12 attempts, p=10%
→ probability >=1 = 71.8%
→ 95% threshold = 29
→ user sets C quantity to 1
→ Build X = UNIQUE_READY
```

This proves domain, persistence and web flow together.

## 17. First Pure Domain Tests

### Readiness

```text
missing ENABLING → BLOCKED
all ENABLING + missing CORE → PARTIAL
all ENABLING + all CORE → UNIQUE_READY
missing UPGRADE does not change UNIQUE_READY
ANY works
quantity works
empty RequirementGroup is rejected
duplicate UniqueDefinition inside one RequirementGroup is rejected
```

### Cross-build independence

```text
Owned A = 1
Build X requires A
Build Y requires A
→ both can be UNIQUE_READY
```

### V0.1 within-build predicate semantics

```text
Group 1 requires A
Group 2 requires A OR B
Owned A = 1
→ both groups satisfied
```

Also document/test that this is not an allocation solver and does not claim support for `A AND (A OR B)` physical-slot
semantics.

### Probability

```text
n = 0 → 0 chance
p in (0,1) → formula correct
p = 1 → expected/thresholds = 1
unknown → no calculation
threshold rounding → ceil to first satisfying attempt
strict EvaluationContext matching → missing required GamePatch/League/Ruleset precision does not match
active probability input uniqueness → no ambiguous canonical/personal selection
```

## 18. First Persistence Integration Tests

Use real migrated PostgreSQL for:

```text
UniqueDefinition
ExternalIdentity basics if part of Slice 1 schema
AccountContext
EvaluationContext
ManualOwnership
BuildVariantRevision + groups + requirements
BuildVariantRevision lifecycle: draft editable, activated semantic contents immutable, superseding activation
AcquisitionSource
DropRelationship
DropEstimate
active probability-input uniqueness constraints
DropRateOverride where introduced in step 22A
AvailableAttempts
```

Do not mock repositories to prove persistence behaviour.

## 19. Modulith Architecture Test

Add early enough that violations are caught before patterns solidify.

Start with coarse module boundaries. Tighten explicit allowed dependencies as actual public module APIs become clear.

## 20. Vertical Slice 1 UI

Minimum routes/views:

```text
/uniques
/uniques/{id}
/builds
/builds/{id}
/acquisition
/acquisition/{id}
```

No Dashboard required yet.

Goals can wait until the central chain works even though they remain part of V0.1.

## 21. Slice 1 Data Seeding

Use a development-only fixture/seed mechanism for A/B/C/Build X/Boss Y.

Do not build full curator UI first.

Fixture must be clearly non-production catalog knowledge.

## 22. Architecture Checkpoint After Slice 1

Before adding real imports, inspect:

```text
Are module boundaries natural?
Is Kotlin + JPA pleasant?
Are persistence entities staying simple?
Is domain logic independent from Spring?
Are controllers thin?
Are view models sufficient?
Does Spring Modulith help or create noise?
Are migrations/tests pleasant?
```

This is the meaningful stack validation point.

## 22A. Complete V0.1 Probability Personalization

Immediately after the core A/B/C/Boss-Y flow works, complete the required personal estimate path before treating V0.1 as
done:

```text
persist DropRateOverride
enforce at most one active canonical estimate and one active personal override per exact applicability/model key
select canonical vs personal fixed probability
mark override STALE when its applicability context no longer matches EvaluationContext
UI + domain/integration tests
```

This is required V0.1 scope even though it is not necessary to prove the minimal first technical slice.

---

## 23. Vertical Slice 2 — Real Catalog Import

Implement PoE Wiki ingestion:

```text
HTTP client
→ PoEWiki DTO
→ normalized candidate
→ ExternalIdentity reconciliation
→ apply persistent catalog-correction overlay
→ conflict reporting
→ unresolved conflict invalidates whole candidate
→ completeness validation (all required pages/batches observed; suspicious truncation fails)
→ absence/disappearance policy (no implicit UniqueDefinition deletion/retirement)
→ validation
→ candidate CatalogRevision
→ activation
→ Catalog UI
```

Add WireMock here.

Tests cover:

```text
pagination/batching
normal response
provider schema variation
malformed record
ambiguous identity
failed import keeps prior active revision
```

Do not attempt perfect automated AcquisitionSource graph construction yet.

## 24. Catalog Identity Rule During Slice 2

Never make permanent identity from a guessed `hash(name + baseType)` style key.

Internal ID is opaque and stable.

Known provider keys become `ExternalIdentity` mappings.

Unclear record → conflict, not creative merge.

## 25. Vertical Slice 3 — Real Curated PoE Knowledge

Add a small useful corpus, approximately:

```text
5–10 BuildVariants initially
important boss AcquisitionSources
real DropRelationships
supported fixed DropEstimates
```

Use this slice to discover the first real modelling exceptions.

If a build requires unsupported combinatorial requirement allocation, do not distort it to fit V0.1. Record it as the
trigger for the future requirement-model extension.

If a boss drop is modifier-dependent, do not insert a misleading fixed rate. Mark probability unsupported/conditional
until modeled.

## 26. Minimal Curator UI

Build only the curation flows needed after real data exists:

```text
create/edit Archetype/Variant
create new VariantRevision
edit groups/requirements
attach BuildReference
create/edit AcquisitionSource
attach DropRelationship
set supported DropEstimate
resolve catalog identity/correction cases
```

Ugly is fine.

## 27. Goals Slice

After the main Account → Build → Item → Acquisition chain works:

```text
Goal
- title
- completed
- AccountContext
- optional one GoalTarget
```

Target is one of:

```text
UniqueDefinition
BuildVariant
AcquisitionSource
```

No generic subtask/dependency engine.

## 28. Early Deployment

Deploy after Slice 1 when the following exist:

```text
basic auth
Flyway migrations
persistent PostgreSQL
backup approach
Actuator health
container image
```

Real catalog data is not required to validate deployment.

## 29. Container Packaging

Produce one OCI image.

Try `bootBuildImage`/Paketo first unless the host/workflow makes a custom multi-stage Dockerfile clearer.

Do not spend product time debating equivalent image builders.

## 30. CI Bootstrap

Initial GitHub Actions pipeline:

```text
compile
Spotless check
Detekt
unit tests
PostgreSQL integration tests
Modulith verification
build
```

Target useful local command:

```text
./gradlew check
```

Heavy integration tests may become a separate task later only if runtime becomes annoying.

## 31. Commit / Milestone Sequence

```text
01 Repository + build + CI + Postgres/Flyway/Testcontainers
02 Modulith/module skeleton + architecture test
03 Catalog identity basics + AccountContext/EvaluationContext + ManualOwnership
04 Build domain + readiness evaluator
05 Acquisition fixed probability domain
06 Minimal server-rendered web flow
07 Complete Vertical Slice 1
08 Required DropRateOverride + canonical/personal selection + STALE applicability
09 Security hardening + first hosted deployment
10 Real PoE Wiki catalog import + WireMock + reconciliation/corrections
11 Small real curated Build/Acquisition corpus
12 Minimal curator UI
13 Goals
```

Milestones are sequencing guidance, not mandatory one-commit units.

## 32. Resist During Bootstrap

Do not prematurely add:

```text
generic repository abstractions
BaseEntity hierarchy
generic event bus
Redis
Kafka
jOOQ before a query needs it
DTO copies for every layer
generic CRUD framework
generic requirement-expression engine
universal drop simulator
Vue
GGG OAuth
microservices
multiple Gradle projects
perfect CSS
```

Known future requirements are not permission to implement frameworks before their first concrete use case.

## 33. Bootstrap Definition of Done

Bootstrap is complete when:

```text
./gradlew build/check works
local PostgreSQL starts
Flyway owns schema
Testcontainers integration test passes
Modulith verification passes
Spring application starts
authenticated trivial Thymeleaf page renders
```

Then infrastructure work stops and Slice 1 starts.

## 34. Vertical Slice 1 Definition of Done

Through the actual browser:

```text
A/B owned, C missing
→ Build X PARTIAL
→ C → Boss Y
→ 12 attempts @ 10%
→ 71.8%, 95%=29
→ C quantity becomes 1
→ Build X UNIQUE_READY
```

using real PostgreSQL, Flyway, application services, domain tests and server-rendered HTML.

## 35. Guiding Implementation Principle

> **Specify the semantics we already know, prove them through vertical slices, and let real PoE cases earn the next
layer of complexity.**
