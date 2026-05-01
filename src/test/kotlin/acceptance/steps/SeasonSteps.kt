package acceptance.steps

import acceptance.SharedInfrastructure
import com.kos.sources.wow.staticdata.wowexpansion.WowExpansion
import com.kos.sources.wow.staticdata.wowexpansion.repository.WowExpansionDatabaseRepository
import com.kos.sources.wow.staticdata.wowexpansion.repository.WowExpansionState
import com.kos.sources.wow.staticdata.wowseason.WowSeason
import com.kos.sources.wow.staticdata.wowseason.repository.WowSeasonDatabaseRepository
import io.cucumber.java.en.Given
import kotlinx.coroutines.runBlocking

class SeasonSteps {

    private val db = SharedInfrastructure.db

    @Given("a current WOW season exists in the database")
    fun currentWowSeasonExists() {
        val expansionRepo = WowExpansionDatabaseRepository(db)
        val seasonRepo = WowSeasonDatabaseRepository(db)
        runBlocking {
            expansionRepo.withState(WowExpansionState(listOf(WowExpansion(10, "The War Within", true))))
            seasonRepo.insert(WowSeason(15, "TWW Season 3", "season-tww-3", 10, "{}", true))
        }
    }
}
