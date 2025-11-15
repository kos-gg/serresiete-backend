package com.kos.clients.blizzard

import arrow.core.Either
import com.kos.clients.domain.StaticWowItem
import com.kos.common.HttpError
import com.kos.common.JsonParseError
import com.kos.common.NotFound
import com.kos.common.WithLogger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import kotlinx.serialization.json.Json

class StaticHttpItemClient(private val client: HttpClient): WithLogger("blizzardStaticClient") {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    suspend fun fetchStaticItemStreaming(itemId: Long): Either<HttpError, StaticWowItem> {
        logger.info("retrieving item $itemId")

        val url =
            "https://raw.githubusercontent.com/nexus-devs/wow-classic-items/refs/heads/master/data/json/data.json"

        val response = client.get(url)
        val channel = response.bodyAsChannel()

        val buffer = ByteArray(8192)
        val sb = StringBuilder()

        var insideObject = false
        var braceDepth = 0

        while (!channel.isClosedForRead) {
            val bytesRead = channel.readAvailable(buffer, 0, buffer.size)
            if (bytesRead <= 0) continue

            for (i in 0 until bytesRead) {
                val c = buffer[i].toInt().toChar()

                when (c) {
                    '{' -> {
                        if (!insideObject) {
                            insideObject = true
                            sb.clear()
                        }
                        braceDepth++
                        sb.append(c)
                    }

                    '}' -> {
                        sb.append(c)
                        braceDepth--

                        // Completed one full JSON object
                        if (braceDepth == 0 && insideObject) {
                            insideObject = false

                            try {
                                val objString = sb.toString()
                                val parsed = json.decodeFromString<StaticWowItem>(objString)

                                if (parsed.itemId == itemId) {
                                    logger.info("retireved item $parsed")
                                    return Either.Right(parsed)
                                }
                            } catch (e: Exception) {
                                // ignore parse errors (rare on malformed chunks)
                            }
                        }
                    }

                    else -> {
                        if (insideObject) {
                            sb.append(c)
                        }
                    }
                }
            }
        }

        return Either.Left(JsonParseError("", "")) // not found
    }
}