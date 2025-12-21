package com.kos.clients.blizzard

import arrow.core.Either
import arrow.core.flatMap
import com.kos.clients.ClientError
import com.kos.clients.NetworkError
import com.kos.clients.domain.BlizzardCredentials
import com.kos.clients.domain.TokenResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.serialization.json.Json
import java.net.URI

class BlizzardHttpAuthClient(private val client: HttpClient, private val credentials: BlizzardCredentials) :
    BlizzardAuthClient {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    override suspend fun getAccessToken(): Either<ClientError, TokenResponse> {
        val uri = URI("https://oauth.battle.net/token")
        val auth = "${credentials.client}:${credentials.secret}".encodeBase64()

        return Either
            .catch {
                client.post(uri.toString()) {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                append("grant_type", "client_credentials")
                            }
                        )
                    )
                    headers {
                        append(HttpHeaders.Accept, "*/*")
                        header(HttpHeaders.Authorization, "Basic $auth")
                    }
                }
            }
            .mapLeft { throwable ->
                NetworkError(throwable.message ?: "Unknown network error")
            }
            .flatMap { response ->
                if (response.status.isSuccess()) {

                    val jsonString = response.body<String>()

                    Either
                        .catch { json.decodeFromString<TokenResponse>(jsonString) }
                        .mapLeft { e ->
                            com.kos.clients.JsonParseError(
                                raw = jsonString,
                                error = e.stackTraceToString()
                            )
                        }

                } else {
                    Either.Left(
                        com.kos.clients.HttpError(
                            status = response.status.value,
                            body = response.bodyAsText()
                        )
                    )
                }
            }
    }
}