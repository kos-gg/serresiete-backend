package com.kos.plugins

import com.kos.common.error.Problem
import com.kos.common.error.internalError
import com.kos.common.error.respondProblem
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.engine.defaultExceptionStatusCode
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            when (val status = defaultExceptionStatusCode(cause)) {
                null, HttpStatusCode.InternalServerError -> {
                    call.application.log.error("Unhandled error on ${call.request.httpMethod.value} ${call.request.uri}", cause)
                    call.respondProblem(internalError)
                }

                else -> call.respondProblem(
                    Problem(status, status.description.uppercase().replace(' ', '_'), status.description)
                )
            }
        }
    }
}
