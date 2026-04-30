# SERRESIETE BACKEND
[![codecov](https://codecov.io/gh/locl95/serresiete-backend/graph/badge.svg?token=DYT934CTMG)](https://codecov.io/gh/locl95/serresiete-backend)

Backend of:
* https://alcaland-ranks.netlify.app/5f51f45c-a032-4a6e-bc49-dab4eeb707f7
* https://osborno-gestiones.netlify.app/
* https://correcalles.netlify.app/
* https://o7gg.netlify.app/

Stack:
* Main language: https://kotlinlang.org/
* HTTP framework: https://ktor.io/docs/welcome.html
* SQL Library: https://github.com/JetBrains/Exposed
* Migrations Library: https://flywaydb.org/
* Enhanced functional programming: https://arrow-kt.io/learn/overview/

Apis used:
* https://raider.io/api
* https://developer.riotgames.com/apis
* https://develop.battle.net/

## Testing

### Unit & integration tests

```bash
./gradlew test
```

### Acceptance tests (e2e)

Acceptance tests run against a real embedded PostgreSQL database using production Flyway migrations and a full in-process Ktor server. 
No external services are required — game API clients are replaced with a mock engine.

Scenarios are written in Gherkin and live under `src/test/resources/features/`. Step definitions use the Cucumber `En` lambda DSL (`cucumber-java8`).

Run only acceptance tests:

```bash
./gradlew test --tests "com.kos.acceptance.CucumberRunner"
```

#### Dependencies

| Artifact | Purpose |
|---|---|
| `io.cucumber:cucumber-java8` | Lambda-style step definitions via the `En` interface |
| `io.cucumber:cucumber-junit-platform-engine` | Bridges Cucumber with JUnit Platform |
| `io.cucumber:cucumber-picocontainer` | Injects shared `World` state into step classes per scenario |
| `org.junit.platform:junit-platform-suite` | `@Suite` runner that wires the Cucumber engine |
| `io.zonky.test:embedded-postgres` | In-process PostgreSQL — no external DB needed |
| `io.ktor:ktor-server-tests-jvm` | `TestApplication` — spins up the full Ktor app in-process |
| `io.ktor:ktor-client-mock` | Replaces external HTTP clients (Blizzard, Riot, RaiderIO) |

#### Structure

```
src/test/
  kotlin/com/kos/acceptance/
    CucumberRunner.kt        # JUnit Platform Suite entry point
    SharedInfrastructure.kt  # Singleton: embedded PG + TestApplication started once per suite
    World.kt                 # Per-scenario state shared across step classes via PicoContainer
    JwtHelper.kt             # JWT factory for building test tokens
    Hooks.kt                 # Before hook: resets DB with Flyway before each scenario
    steps/                   # Step definition classes
    fixtures/                # DB seeding helpers (givenView, givenUser, ...)
  resources/
    features/                # Gherkin feature files
```
