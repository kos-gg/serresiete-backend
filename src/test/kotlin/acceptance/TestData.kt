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

fun existingEntityRow(game: Game): Map<String, String> = when (game) {
    Game.WOW, Game.WOW_HC -> mapOf("name" to "Sanxei", "region" to "eu", "realm" to "Silvermoon")
    Game.LOL -> mapOf("name" to "GTP ZeroMVPs", "tag" to "EUW")
}

fun newEntityRow(game: Game): Map<String, String> = when (game) {
    Game.WOW, Game.WOW_HC -> mapOf("name" to "Threndil", "region" to "eu", "realm" to "Silvermoon")
    Game.LOL -> mapOf("name" to "Faker", "tag" to "T1")
}

fun Game.entityRequest(row: Map<String, String>): EntityRequest = when (this) {
    Game.WOW, Game.WOW_HC -> WowEntityRequest(row.getValue("name"), row.getValue("region"), row.getValue("realm"))
    Game.LOL -> LolEntityRequest(row.getValue("name"), row.getValue("tag"))
}

fun Game.entityRequestJson(row: Map<String, String>): JsonObject = when (this) {
    Game.WOW, Game.WOW_HC -> buildJsonObject {
        put("type", "wow")
        put("name", row["name"])
        put("region", row["region"])
        put("realm", row["realm"])
    }

    Game.LOL -> buildJsonObject {
        put("type", "lol")
        put("name", row["name"])
        put("tag", row["tag"])
    }
}
