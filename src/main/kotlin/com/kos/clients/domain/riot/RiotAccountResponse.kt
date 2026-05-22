package com.kos.clients.domain.riot

import kotlinx.serialization.Serializable

@Serializable
data class GetAccountResponse(val gameName: String, val tagLine: String)

@Serializable
data class GetPUUIDResponse(val puuid: String, val gameName: String, val tagLine: String)

@Serializable
data class GetSummonerResponse(
    val puuid: String,
    val profileIconId: Int,
    val revisionDate: Long,
    val summonerLevel: Int
)
