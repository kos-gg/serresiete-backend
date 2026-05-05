package acceptance

import com.kos.views.Game
import io.ktor.client.statement.*

class ScenarioVariables {
    var token: String? = null
    var viewId: String? = null
    var game: Game? = null
    lateinit var response: HttpResponse
}
