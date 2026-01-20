package com.kos.clients.blizzard

import arrow.core.Either
import com.kos.clients.ClientError
import com.kos.clients.domain.BlizzardCredentials
import com.kos.clients.domain.TokenResponse
import com.kos.clients.fetchFromApi
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.util.*
import java.net.URI

class BlizzardHttpAuthClient(
    private val client: HttpClient,
    private val credentials: BlizzardCredentials
) : BlizzardAuthClient {

    override suspend fun getAccessToken(): Either<ClientError, TokenResponse> {
        val uri = URI("https://oauth.battle.net/token")
        val auth = "${credentials.client}:${credentials.secret}".encodeBase64()

        return fetchFromApi {
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
    }

}