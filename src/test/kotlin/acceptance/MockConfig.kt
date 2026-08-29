package acceptance

import io.ktor.http.*

object MockConfig {
    var blizzardProfileStatusOverride: HttpStatusCode? = null
    var raiderIoCutoffStatusOverride: HttpStatusCode? = null
    var raiderIoProfileStatusOverride: HttpStatusCode? = null
    var wowGuildRosterMembers: List<Pair<String, Int>>? = null

    fun reset() {
        blizzardProfileStatusOverride = null
        raiderIoCutoffStatusOverride = null
        raiderIoProfileStatusOverride = null
        wowGuildRosterMembers = null
    }
}
