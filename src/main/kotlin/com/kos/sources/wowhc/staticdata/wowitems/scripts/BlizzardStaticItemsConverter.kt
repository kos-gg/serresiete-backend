package com.kos.sources.wowhc.staticdata.wowitems.scripts

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.File

fun main() = runBlocking {
    val githubUrl =
        "https://raw.githubusercontent.com/nexus-devs/wow-classic-items/refs/heads/master/data/json/data.json"

    val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 120_000
        }
    }

    println("Downloading full JSON from GitHub…")
    val jsonString: String = client.get(githubUrl).body()
    println("Download complete, filtering equippable items…")

    val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }
    val fullJson = json.parseToJsonElement(jsonString).jsonArray

    val equippableItems: List<String> = fullJson.filter { item ->
        val obj = item.jsonObject

        val isMisc = obj["class"]?.jsonPrimitive?.content == "Miscellaneous"
        val isJunk = obj["subclass"]?.jsonPrimitive?.content == "Junk"

        val hasMaxStack200 = obj["tooltip"]
            ?.jsonArray
            ?.any { tip ->
                tip.jsonObject["label"]?.jsonPrimitive?.content == "Max Stack: 200"
            } == true

        isMisc && isJunk && hasMaxStack200
    }.map { item ->
        val staticItem = json.decodeFromJsonElement<StaticWowItem>(item)
        val obj = Json.encodeToString(staticItem.toBlizzard())
        val media = Json.encodeToString(staticItem.toBlizzardMedia())

        "(${staticItem.itemId}, $$$obj$$, $$$media$$)"
    }

    val outputFile = File("wow-items-equippable.json")
    outputFile.writeText(equippableItems.joinToString(",\n"))

    println("Filtered JSON saved: ${outputFile.absolutePath}")
    println("Total equippable items: ${equippableItems.size}")

    client.close()
}