package com.kos.datacache

import com.kos.views.Game
import java.time.OffsetDateTime

object TestHelper {

    private val wowData = """{                                
     "id": 1,                    
     "name": "Proassassin",       
     "spec": "Havoc",             
     "class": "Demon Hunter",     
     "score": 0.0,                
     "quantile": 0.0,             
     "mythicPlusRanks": {         
         "class": {               
             "realm": 0,          
             "world": 0,          
             "region": 0          
         },                       
         "specs": [],                       
         "overall": {             
             "realm": 0,          
             "world": 0,          
             "region": 0          
         }                        
     },                           
     "mythicPlusBestRuns": [],
     "type": "com.kos.clients.domain.RaiderIoData"
   }"""

    val lolData = """{
        "type": "com.kos.clients.domain.RiotData",
        "summonerIcon": 1389,
        "summonerLevel": 499,
        "summonerName": "GTP ZeroMVPs",
        "summonerTag": "KAKO",
        "leagues": {
            "RANKED_SOLO_5x5": {
                "mainRole": "SUPPORT",
                "tier": "GOLD",
                "rank": "I",
                "leaguePoints": 1,
                "gamesPlayed": 27,
                "winrate": 0.5925925925925926,
                "matches": [
                    {
                        "id": "EUW1_232424252",
                        "championId": 497,
                        "championName": "Rakan",
                        "role": "SUPPORT",
                        "individualPosition": "UTILITY",
                        "lane": "BOTTOM",
                        "kills": 2,
                        "deaths": 7,
                        "assists": 15,
                        "assistMePings": 0,
                        "visionWardsBoughtInGame": 8,
                        "enemyMissingPings": 0,
                        "wardsPlaced": 47,
                        "gameDuration": 1883,
                        "totalTimeSpentDead": 174,
                        "win": true
                    },
                    {
                        "id": "EUW1_232424252",
                        "championId": 497,
                        "championName": "Rakan",
                        "role": "SUPPORT",
                        "individualPosition": "UTILITY",
                        "lane": "NONE",
                        "kills": 0,
                        "deaths": 2,
                        "assists": 20,
                        "assistMePings": 0,
                        "visionWardsBoughtInGame": 5,
                        "enemyMissingPings": 2,
                        "wardsPlaced": 20,
                        "gameDuration": 1146,
                        "totalTimeSpentDead": 24,
                        "win": true
                    },
                    {
                        "id": "EUW1_232424252",
                        "championId": 12,
                        "championName": "Alistar",
                        "role": "SUPPORT",
                        "individualPosition": "UTILITY",
                        "lane": "NONE",
                        "kills": 2,
                        "deaths": 2,
                        "assists": 4,
                        "assistMePings": 0,
                        "visionWardsBoughtInGame": 6,
                        "enemyMissingPings": 0,
                        "wardsPlaced": 11,
                        "gameDuration": 917,
                        "totalTimeSpentDead": 36,
                        "win": true
                    },
                    {
                        "id": "EUW1_232424252",
                        "championId": 497,
                        "championName": "Rakan",
                        "role": "SUPPORT",
                        "individualPosition": "UTILITY",
                        "lane": "BOTTOM",
                        "kills": 2,
                        "deaths": 3,
                        "assists": 21,
                        "assistMePings": 0,
                        "visionWardsBoughtInGame": 16,
                        "enemyMissingPings": 0,
                        "wardsPlaced": 51,
                        "gameDuration": 1712,
                        "totalTimeSpentDead": 67,
                        "win": true
                    },
                    {
                        "id": "EUW1_232424252",
                        "championId": 235,
                        "championName": "Senna",
                        "role": "SUPPORT",
                        "individualPosition": "UTILITY",
                        "lane": "BOTTOM",
                        "kills": 0,
                        "deaths": 2,
                        "assists": 13,
                        "assistMePings": 0,
                        "visionWardsBoughtInGame": 11,
                        "enemyMissingPings": 0,
                        "wardsPlaced": 32,
                        "gameDuration": 1856,
                        "totalTimeSpentDead": 73,
                        "win": true
                    }
                ]
            }
        }
    }"""

    val anotherLolData = """{
          "type": "com.kos.clients.domain.RiotData",
          "leagues": {},
          "summonerIcon": 3582,
          "summonerName": "sanxei",
          "summonerTag": "EUW",
          "summonerLevel": 367
        }
    """

    val smartSyncDataCache = """{
            "type": "com.kos.clients.domain.RiotData",
            "summonerIcon": 1,
            "summonerLevel": 30,
            "summonerName": "TestSummoner",
            "summonerTag": "TAG",
            "leagues": {
                "RANKED_FLEX_SR": {
                    "mainRole": "MainRole",
                    "tier": "Gold",
                    "rank": "I",
                    "leaguePoints": 100,
                    "gamesPlayed": 20,
                    "winrate": 50.0,
                    "matches": [
                        {
                            "id": "match1",
                            "championId": 1,
                            "championName": "ChampionName",
                            "role": "Role",
                            "individualPosition": "Position",
                            "lane": "Lane",
                            "kills": 5,
                            "deaths": 1,
                            "assists": 10,
                            "assistMePings": 0,
                            "visionWardsBoughtInGame": 0,
                            "enemyMissingPings": 0,
                            "wardsPlaced": 0,
                            "gameDuration": 1800,
                            "totalTimeSpentDead": 300,
                            "win": true
                        },
                        {
                            "id": "match2",
                            "championId": 1,
                            "championName": "ChampionName",
                            "role": "Role",
                            "individualPosition": "Position",
                            "lane": "Lane",
                            "kills": 5,
                            "deaths": 1,
                            "assists": 10,
                            "assistMePings": 0,
                            "visionWardsBoughtInGame": 0,
                            "enemyMissingPings": 0,
                            "wardsPlaced": 0,
                            "gameDuration": 1800,
                            "totalTimeSpentDead": 300,
                            "win": true
                        },
                        {
                            "id": "match3",
                            "championId": 1,
                            "championName": "ChampionName",
                            "role": "Role",
                            "individualPosition": "Position",
                            "lane": "Lane",
                            "kills": 5,
                            "deaths": 1,
                            "assists": 10,
                            "assistMePings": 0,
                            "visionWardsBoughtInGame": 0,
                            "enemyMissingPings": 0,
                            "wardsPlaced": 0,
                            "gameDuration": 1800,
                            "totalTimeSpentDead": 300,
                            "win": true
                        }
                    ]
                }
            }
        }"""

    private val now = OffsetDateTime.now()
    val outdatedDataCache = DataCache(1, wowData, now.minusHours(25), Game.WOW)
    val wowDataCache = DataCache(1, wowData, now, Game.WOW)
    val lolDataCache = DataCache(2, lolData, now, Game.LOL)
    val anotherLolDataCache = DataCache(3, anotherLolData, now, Game.LOL)
}