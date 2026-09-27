# PoE Companion — Technology & Engineering Baseline V2.1.1

**Status:** Canonical implementation technology baseline
**Verified:** 2026-09-22
**Purpose:** Record the concrete stack selected after the technology and JVM-language evaluation.

## 1. Executive Summary

```text
Kotlin
JDK 25 LTS

Spring Boot 4.x
Spring MVC
Spring Security
Spring Data JPA / Hibernate
Spring Modulith
Thymeleaf
HTMX

PostgreSQL
Flyway SQL migrations
jOOQ only when justified

Gradle Kotlin DSL
libs.versions.toml
Spring Boot BOM/native Gradle platform

JUnit
AssertJ
MockK selectively
Testcontainers
WireMock when external HTTP integration begins

Detekt
Spotless using ktlint
Renovate

Docker Compose locally
OCI/Docker production image
GitHub Actions
```

Architecture:

```text
server-rendered modular monolith
one application deployable
one relational database
backend owns presentation/domain rules
external providers behind adapters
```

## 2. Reference Versions

Reference baseline as of 2026-09-22:

```text
JDK                25 LTS
Kotlin             2.4.20
Spring Boot        4.1.1
Spring Modulith    2.1.1
Gradle             9.7.1
PostgreSQL         18.6
```

Exact dependency versions should be rechecked at repository bootstrap/update time. Prefer stable compatible releases, not preview versions merely because they are newer.

Spring Boot 4.1.1 supports Java 17 through 26, so JDK 25 is a supported LTS baseline.

## 3. Technology Selection

Final backend candidates were Django and Spring Boot.

Django's strongest advantages:

```text
initial product velocity
built-in Admin
data scripting
low CRUD ceremony
```

Spring's strongest advantages for this product:

```text
static typing/refactoring confidence
transactional/domain-heavy growth
mature relational persistence
integration testing
security/OAuth ecosystem
long-lived codebase predictability
```

Decision:

> **Spring Boot**

The decision is based on expected domain/version/integration complexity, not familiarity alone.

## 4. JVM Language Decision

Final candidates: Java and Kotlin.

Decision:

> **Kotlin as the only application source language.**

Application/tests live under:

```text
src/main/kotlin
src/test/kotlin
```

No Java source is planned.

Kotlin is preferred primarily for:

```text
compile-time nullability
sealed state modelling
concise immutable values/DTOs
exhaustive branching
strong refactoring semantics
```

not merely fewer lines of code.

## 5. Kotlin + JPA Rules

Use:

```text
kotlin-jvm
kotlin-spring
kotlin-jpa
```

Modern Kotlin JPA support automatically handles the relevant JPA no-arg/all-open behaviour through the JPA compiler plugin.

Coding rules:

```text
ordinary classes for JPA entities
no data classes for JPA entities
data classes for DTOs/domain results
constructor injection
non-null by default
DB nullability aligned with Kotlin nullability
avoid unnecessary lateinit
avoid clever entity inheritance
avoid unnecessary bidirectional relationships
```

Preferred style:

> **Boring Kotlin.**

Avoid custom DSLs, operator magic, deep functional abstractions or coroutine architecture without a demonstrated problem.

## 6. Spring Application Model

Use:

```text
Spring Boot
Spring MVC
imperative Kotlin
blocking JDBC/JPA
```

Do not start with:

```text
WebFlux
Reactor application model
R2DBC
```

Coroutines and virtual threads remain optional future tools for actual concurrency/I/O needs, not defaults.

## 7. Spring Modulith

Use Spring Modulith early to reinforce the modular-monolith structure.

Primary uses:

```text
module-boundary verification
cycle detection
module-scoped testing where useful
architecture documentation
```

Do not introduce event-driven internal architecture merely because Modulith supports it.

Domain-oriented packaging remains the primary design.

## 8. Frontend

Initial frontend:

```text
Spring MVC
→ Thymeleaf
→ HTML
+ HTMX selectively
```

Backend owns the application. There is no separate SPA/application state layer.

HTMX is used where it removes obvious friction, for example:

```text
ownership quantity update
attempt count update
simple filters
goal completion
small fragment refreshes
```

Plain form POST + redirect is acceptable when simpler.

## 9. Why Not Vue Initially?

Current UI is primarily:

```text
tables
forms
filters
detail pages
navigation
small state changes
```

A Vue/TypeScript application would add a second application/toolchain/API boundary without current product value.

Vue remains possible later if genuinely complex client-side interaction appears. Application/domain services must therefore stay presentation-independent.

## 10. Frontend Tooling Principle

No Node-based frontend build is required initially.

Serve Thymeleaf, HTMX and minimal CSS/JS from the Spring application.

Do not start a design-system/component-framework project.

## 11. Database

Use PostgreSQL.

Reference current stable major/minor at bootstrap; PostgreSQL 18.6 is the current 18.x maintenance release as of this document.

The domain is relational and benefits from:

```text
transactions
constraints
indexes
rich SQL
JSONB only where genuinely useful
excellent Testcontainers support
```

## 12. Persistence

Start with:

```text
Spring Data JPA
Hibernate
```

for ordinary domain persistence.

Set:

```text
spring.jpa.open-in-view=false
```

from the beginning.

Application services own transaction boundaries. Lazy DB access must not leak into Thymeleaf rendering.

## 13. jOOQ

Do not add jOOQ at repository bootstrap.

Add it when a real read/query problem becomes substantially clearer in SQL, e.g. complex readiness/reporting/dashboard queries.

Intended future hybrid:

```text
ordinary writes/persistence → JPA
complex reads/reporting     → jOOQ
```

No ideological ORM-only rule.

## 14. Migrations

Use:

```text
Flyway
+ PostgreSQL SQL migrations
```

Example:

```text
V001__initial_schema.sql
V002__add_build_revision.sql
```

Hibernate does not own production schema evolution.

Prefer real PostgreSQL SQL rather than database-agnostic migration abstraction because only PostgreSQL is targeted.

## 15. Mapping

No MapStruct initially.

Use explicit Kotlin mapping functions/extensions where a real boundary exists.

Avoid generating unnecessary layers such as Entity/Domain/DTO/View for every object when no boundary requires them.

No kapt dependency merely for mapping.

## 16. Serialization

Use Spring's Jackson integration with the Kotlin module where JSON is needed for provider/API boundaries.

Do not introduce a second serialization ecosystem without a concrete requirement.

## 17. External HTTP

Preferred Spring client approach:

```text
HTTP Service Interfaces where useful
RestClient
```

Provider-specific clients/adapters remain inside the owning functional module.

WireMock is added when the first real HTTP provider integration is implemented.

## 18. Build Tool

Use:

```text
Gradle
Kotlin DSL
libs.versions.toml
Gradle wrapper
```

Reference Gradle baseline: 9.7.1.

Use Spring Boot's BOM through Gradle native platform support for Boot-managed dependencies.

Do not duplicate Spring/Hibernate/Jackson/Micrometer ecosystem versions into the version catalog.

`libs.versions.toml` primarily manages plugins/non-Boot-managed dependencies.

Enable dependency locking/verification once the bootstrap build is stable.

## 19. Testing

Core stack:

```text
JUnit
AssertJ
MockK selectively
Spring Boot Test
Testcontainers
Spring Modulith test support
WireMock for provider HTTP
```

Testing philosophy:

```text
many pure domain tests
real PostgreSQL integration tests
module/integration tests
few meaningful full HTTP tests
```

Do not use H2 as a PostgreSQL substitute.

Do not mock repository/database semantics that can cheaply be tested against real PostgreSQL.

Mocks should be uncommon and purposeful.

## 20. Local Infrastructure

Use Docker Compose for developer infrastructure.

Initial Compose services:

```text
postgres
```

Run the application from IntelliJ/`./gradlew bootRun` during development unless containerizing it locally adds value.

Tests use Testcontainers independently from developer Compose state.

## 21. Production Packaging

Production artifact is an OCI/Docker image.

Preferred first attempt:

```text
Spring Boot bootBuildImage / Paketo
```

A small explicit multi-stage Dockerfile is equally acceptable if the selected host/workflow makes it clearer.

Container technology is fixed; the image-construction mechanism is not architecturally important.

## 22. Security

Initial security:

```text
Spring Security
session-based application login
single configured user
HTTPS
secure + HttpOnly cookies
CSRF protection
secrets outside Git
DB not publicly exposed where avoidable
```

No public IAM/user-management platform is required.

GGG OAuth, when added, is an OAuth client integration distinct from application authentication.

## 23. Observability

Initial baseline:

```text
Spring Boot Actuator
health endpoint
structured/consistent application logging
import/provider failure logs
security-relevant logging without secrets
```

Do not initially deploy Prometheus/Grafana/Tempo/OTel Collector solely because mature SaaS stacks often do.

Add richer observability when operations create a concrete need.

## 24. Quality

Use:

```text
Detekt
Spotless
ktlint as the Spotless Kotlin formatting engine
```

Do not create two competing formatting workflows with Spotless plus an independently authoritative ktlint plugin setup.

Target one ordinary quality command:

```text
./gradlew check
```

## 25. Dependency Updates and CI

Use Renovate for update PRs.

Use GitHub Actions initially.

CI should cover:

```text
compile
format/lint
unit tests
integration tests
module verification
build
```

Container build/deployment joins the pipeline when the first host exists.

## 26. Repository Shape

Single repository, single Gradle project initially.

```text
src/main/kotlin
src/main/resources
src/test/kotlin
gradle/libs.versions.toml
build.gradle.kts
settings.gradle.kts
compose.yaml
docs/
```

No separate frontend repository/project.

## 27. Module Packaging

Functional top-level packages approximately:

```text
catalog
league
account
build
acquisition
goal
security
```

Provider adapters live with their owning capability, e.g.:

```text
catalog.integration.poewiki
account.integration.ggg
build.integration.pob
```

Avoid a top-level Kotlin `import` package and avoid global technical folders such as one application-wide `controller/`, `service/`, `repository/` hierarchy.

## 28. Curator UI

Spring does not provide Django Admin, so build small developer-only server-rendered curation pages as necessary.

Correctness and fast curation matter more than visual polish.

Do not build a generic admin framework before the actual curation needs are known.

## 29. Explicitly Not Initial Stack

```text
Vue/React/Next.js
separate frontend deployment
WebFlux/R2DBC
coroutine architecture
Redis
Kafka
RabbitMQ
Elasticsearch
Spring Batch
JobRunr
MapStruct
Lombok
H2
Prometheus/Grafana/Tempo stack
Kubernetes
microservices
```

These are not banned forever; they currently solve no demonstrated problem.

## 30. Evolution Paths

```text
Thymeleaf/HTMX → richer client UI later if justified
JPA → JPA + jOOQ when query complexity earns it
modular monolith → selected service extraction when independent deployment earns it
manual ownership → synchronized ownership behind same domain boundary
fixed probability → additional probability models without changing acquisition identity
```

## 31. Engineering Principle

> **Use the smallest boring stack that strongly protects the domain, and make every additional dependency or distributed boundary earn its place.**
