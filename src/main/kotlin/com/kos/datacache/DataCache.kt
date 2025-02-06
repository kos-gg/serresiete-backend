package com.kos.datacache

import com.kos.views.Game
import java.time.OffsetDateTime

data class DataCache(val entityId: Long, val data: String, val inserted: OffsetDateTime, val game: Game) {

    //TODO: Change this depending of source (game)
    fun isTooOld(): Boolean = inserted.plusMinutes(15).isBefore(OffsetDateTime.now())
}
