package acceptance

import com.kos.entities.domain.WowEntityRequest
import com.kos.views.Game

fun String.toGame(): Game = Game.valueOf(uppercase())

fun wowEntityRequest(name: String, realm: String, region: String): WowEntityRequest =
    WowEntityRequest(name, region, realm)
