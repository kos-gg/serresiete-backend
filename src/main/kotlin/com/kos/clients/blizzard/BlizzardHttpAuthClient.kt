package com.kos.clients.blizzard

import arrow.core.Either
import com.kos.clients.domain.BlizzardCredentials
import com.kos.clients.domain.RiotError
import com.kos.clients.domain.TokenResponse
import com.kos.common.HttpError
import com.kos.common.JsonParseError
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.net.URI

class BlizzardHttpAuthClient(private val client: HttpClient, private val credentials: BlizzardCredentials) : BlizzardAuthClient {
    private val json = Json {
        ignoreUnknownKeys = true
    }
    override suspend fun getAccessToken(): Either<HttpError, TokenResponse> {
        val uri = URI("https://oauth.battle.net/token")
        val auth = "${credentials.client}:${credentials.secret}".encodeBase64()
        val response = client.post(uri.toString()) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(FormDataContent(Parameters.build {
                append("grant_type", "client_credentials")
            }))
            headers {
                append(HttpHeaders.Accept, "*/*")
                header(HttpHeaders.Authorization, "Basic $auth")
            }
        }
        val jsonString = response.body<String>()
        return try {
            Either.Right(json.decodeFromString<TokenResponse>(jsonString))
        } catch (e: SerializationException) {
            Either.Left(JsonParseError(jsonString, e.stackTraceToString()))
        } catch (e: IllegalArgumentException) {
            val error = json.decodeFromString<RiotError>(jsonString)
            Either.Left(error)
        }
    }
}