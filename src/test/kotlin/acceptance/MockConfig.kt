package acceptance

import io.ktor.http.*

object MockConfig {
    var blizzardProfileStatusOverride: HttpStatusCode? = null

    fun reset() {
        blizzardProfileStatusOverride = null
    }
}
