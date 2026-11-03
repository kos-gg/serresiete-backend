package com.kos.common.error

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class Problem(
    val status: Int,
    val code: String,
    val title: String,
    val detail: String? = null
) {
    constructor(status: HttpStatusCode, code: String, title: String, detail: String? = null) :
            this(status.value, code, title, detail)
}

val internalError = Problem(HttpStatusCode.InternalServerError, "INTERNAL_ERROR", "Internal error")

private val problemJson = ContentType("application", "problem+json")

suspend fun ApplicationCall.respondProblem(problem: Problem) =
    respondText(Json.encodeToString(problem), problemJson, HttpStatusCode.fromValue(problem.status))
