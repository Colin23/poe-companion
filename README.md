# Path of Exile Companion

Path of Exile Companion is a server-rendered companion application for Path of Exile. Its goal is to help players
compare current item ownership with build requirements and understand acquisition options without making the final
decision for them.

> [!NOTE]
> The repository currently contains the application foundation and the canonical V2.1.1 product specification. Product
functionality is implemented incrementally through vertical slices; the current application is not yet the complete V0.1
product.

## Stack

The application uses:

- Kotlin as its application source language on JDK 25
- Spring Boot
- Spring MVC
- Thymeleaf
- Spring Security
- Spring Data JPA/Hibernate
- Spring Modulith
- PostgreSQL
- Flyway
- Gradle
- Testcontainers
- Detekt
- Spotless/ktlint
- Docker Compose
- CycloneDX SBOM generation

Exact dependency versions live in `gradle/libs.versions.toml` and the Gradle wrapper configuration.

## Prerequisites

Install:

- Git
- Docker with Docker Compose
- JDK 25 for local/IDE use

The Gradle wrapper is included, so a separate Gradle installation is not required. The configured Gradle toolchain can
also provision a matching JDK when necessary.

## Local setup

Clone the repository and create the local environment file:

```bash
git clone git@github.com:Colin23/poe-companion.git
cd poe-companion
cp .env.example .env
```

Set non-empty local values for at least:

```dotenv
POE_COMPANION_DB_PASSWORD=choose-a-local-database-password
POE_COMPANION_APP_PASSWORD=choose-a-local-application-password
```

`.env` is for local development only. Do not commit it or reuse its credentials in a hosted environment.

Export the variables and start the application:

```bash
set -a
source .env
set +a
./gradlew bootRun
```

Spring Boot starts the PostgreSQL Compose service when required. Compose lifecycle management is `start-only`, so the
database remains running after the application stops.

Useful local endpoints:

- Application: <http://localhost:8080>
- Login: <http://localhost:8080/login>
- Health: <http://localhost:8080/actuator/health>

The health endpoint is public. Other application endpoints require authentication with `POE_COMPANION_APP_USERNAME` and
`POE_COMPANION_APP_PASSWORD`.

To stop local PostgreSQL:

```bash
docker compose down
```

To also delete the local database volume:

```bash
docker compose down --volumes
```

## Build and verification

Run the complete local verification with dependency verification made explicit:

```bash
./gradlew clean build --dependency-verification=strict
```

Docker must be available because integration tests use PostgreSQL through Testcontainers.

Useful focused commands:

```bash
./gradlew test
./gradlew detekt
./gradlew spotlessCheck
./gradlew spotlessApply
```

Build and test reports are written under `build/reports/` and `build/test-results/`.

## Database migrations

Flyway owns the PostgreSQL schema. Migration files live in `src/main/resources/db/migration`.

The application uses the `poe_companion` schema, and Hibernate is configured to validate it rather than create or mutate
it.

For schema changes:

1. Add a new versioned Flyway migration.
2. Do not rewrite a migration that has been shared or applied outside a disposable local database.
3. Test against PostgreSQL rather than an in-memory substitute.

## Architecture

The application is a single-deployable modular monolith verified with Spring Modulith. The initial functional modules
are:

| Module        | Responsibility                                |
|---------------|-----------------------------------------------|
| `account`     | Account contexts and current item ownership   |
| `acquisition` | Acquisition sources and probability knowledge |
| `build`       | Build variants, revisions, and requirements   |
| `catalog`     | Canonical item and catalog knowledge          |
| `goal`        | User goals and targets                        |
| `league`      | League, patch, and compatibility context      |
| `security`    | Authentication and application access         |

Implementation details should remain inside module-internal packages. Cross-module interaction should use explicit
module APIs rather than references to another module's internals.

`ApplicationModulesTests` verifies the module structure.

## Product specification

The canonical V2.1.1 specification is in [`docs/product`](docs/product). Start with
the [Specification Index](docs/product/poe-companion-spec-index-v2.1.1.md); it defines which document is authoritative
for product scope, domain semantics, architecture, technology choices, implementation sequencing, and time-sensitive
feasibility evidence.

When documents appear to conflict, follow the authority rules in the Specification Index rather than duplicating or
informally overriding a decision.

## Dependency management

Renovate owns dependency-update PR creation. Gradle dependency locking and dependency verification are intentionally
enabled and should remain strict.

The full maintainer workflow, including Renovate policy, verification-metadata repair, IDE-only source/Javadoc
exceptions, Gradle wrapper updates, signed commits, and supply-chain guardrails, is documented
in [Dependency management and supply-chain runbook](docs/development/dependency-management.md).

## Software bill of materials

The build generates a CycloneDX JSON SBOM at:

```text
build/reports/cyclonedx/bom.json
```

Generate it directly with:

```bash
./gradlew cyclonedxDirectBom
```

## Packaging

Build the executable Spring Boot JAR:

```bash
./gradlew bootJar
```

Build an OCI image with Spring Boot buildpacks:

```bash
./gradlew bootBuildImage
```

## Continuous integration

GitHub Actions runs `Build and verify` on pushes to `main`, pull requests, and manual workflow dispatches. Successful
builds upload the executable JAR and CycloneDX SBOM; failed builds upload diagnostic reports when available.

The default branch also requires signed commits and is protected by required CI and CodeQL checks.

## Repository hygiene

Local/generated files such as `.env`, `.gradle/`, `.idea/`, `.kotlin/`, and `build/` are ignored. Keep credentials,
local database state, and generated build output out of Git.

## License and third-party notice

PoE Companion is licensed under the GNU Affero General Public License version 3 only (`AGPL-3.0-only`).
See [LICENSE](LICENSE) for details.

This product isn't affiliated with or endorsed by Grinding Gear Games in any way.

Path of Exile is developed by Grinding Gear Games.
See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for additional third-party information.
