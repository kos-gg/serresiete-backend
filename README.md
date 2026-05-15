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

### Acceptance tests

Acceptance tests are written in Gherkin (Cucumber) and live under `src/test/resources/acceptance/features/`.

**Via Gradle:**
```bash
./gradlew acceptanceTest
```

**Via IDE (IntelliJ):**
- Run `CucumberRunner` directly — right-click `src/test/kotlin/acceptance/CucumberRunner.kt` and select _Run_
- Or open any `.feature` file and click the run icon next to an individual scenario to run it in isolation

### Full build (unit, integration & acceptance)

```bash
./gradlew check
```
