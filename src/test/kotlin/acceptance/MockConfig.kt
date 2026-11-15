package acceptance

import io.ktor.http.*

data class WowRosterMember(val name: String, val level: Int, val blizzardId: Long)

object MockConfig {
    var blizzardProfileStatusOverride: HttpStatusCode? = null
    var raiderIoCutoffStatusOverride: HttpStatusCode? = null
    var raiderIoProfileStatusOverride: HttpStatusCode? = null
    var wowGuildRosterMembers: List<WowRosterMember>? = null

    fun reset() {
        blizzardProfileStatusOverride = null
        raiderIoCutoffStatusOverride = null
        raiderIoProfileStatusOverride = null
        wowGuildRosterMembers = null
    }
}
