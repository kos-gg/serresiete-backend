package acceptance

import com.kos.entities.domain.EntityRequest
import com.kos.entities.domain.LolEntityRequest
import com.kos.entities.domain.WowEntityRequest
import com.kos.views.Game
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun String.toGame(): Game = Game.valueOf(uppercase())

fun wowEntityRequest(name: String, realm: String, region: String): WowEntityRequest =
    WowEntityRequest(name, region, realm)

// Already present in the database before the request is made, so the resolver
// should find it via the repository and skip the third-party existence check.
fun existingEntityRow(game: Game): Map<String, String> = when (game) {
    Game.WOW, Game.WOW_HC -> mapOf("name" to "Sanxei", "region" to "eu", "realm" to "Silvermoon")
    Game.LOL -> mapOf("name" to "GTP ZeroMVPs", "tag" to "EUW")
}

// Not present in the database, so the resolver has to confirm it against the
// third party (RaiderIO/Riot/Blizzard) before it can be persisted.
fun newEntityRow(game: Game): Map<String, String> = when (game) {
    Game.WOW, Game.WOW_HC -> mapOf("name" to "Threndil", "region" to "eu", "realm" to "Silvermoon")
    Game.LOL -> mapOf("name" to "Faker", "tag" to "T1")
}

fun Game.entityRequest(row: Map<String, String>): EntityRequest = when (this) {
    Game.WOW, Game.WOW_HC -> WowEntityRequest(row.getValue("name"), row.getValue("region"), row.getValue("realm"))
    Game.LOL -> LolEntityRequest(row.getValue("name"), row.getValue("tag"))
}

// The acceptance HTTP client's Json() has no SerializersModule for EntityRequest's
// polymorphic subtypes, so a typed WowEntityRequest/LolEntityRequest would fail to
// serialize via setBody() - build the wire JSON by hand instead.
fun Game.entityRequestJson(row: Map<String, String>): JsonObject = when (this) {
    Game.WOW, Game.WOW_HC -> buildJsonObject {
        put("type", "com.kos.entities.domain.WowEntityRequest")
        put("name", row["name"])
        put("region", row["region"])
        put("realm", row["realm"])
    }

    Game.LOL -> buildJsonObject {
        put("type", "com.kos.entities.domain.LolEntityRequest")
        put("name", row["name"])
        put("tag", row["tag"])
    }
}
