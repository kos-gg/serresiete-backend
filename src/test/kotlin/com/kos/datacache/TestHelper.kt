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
                "teamPosition": "UTILITY",
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
                        "teamPosition": "UTILITY",
                        "lane": "BOTTOM",
                        "kills": 2,
                        "deaths": 7,
                        "assists": 15,
                        "assistMePings": 0,
                        "visionWardsBoughtInGame": 8,
                        "enemyMissingPings": 0,
                        "wardsPlaced": 47,
                        "gameFinishedCorrectly": true,
                        "gameDuration": 1883,
                        "totalTimeSpentDead": 174,
                        "win": true,
                        "matchUp" : {
                            "championId" : 2,
                            "championName" : "Aatrox",
                            "teamPosition" : "TOP",
                            "kills" : 10,
                            "deaths" : 1,
                            "assists" : 0
                        }
                    },
                    {
                        "id": "EUW1_232424252",
                        "championId": 497,
                        "championName": "Rakan",
                        "role": "SUPPORT",
                        "individualPosition": "UTILITY",
                        "teamPosition": "UTILITY",
                        "lane": "NONE",
                        "kills": 0,
                        "deaths": 2,
                        "assists": 20,
                        "assistMePings": 0,
                        "visionWardsBoughtInGame": 5,
                        "enemyMissingPings": 2,
                        "wardsPlaced": 20,
                        "gameFinishedCorrectly": true,
                        "gameDuration": 1146,
                        "totalTimeSpentDead": 24,
                        "win": true,
                        "matchUp" : {
                            "championId" : 2,
                            "championName" : "Aatrox",
                            "teamPosition" : "TOP",
                            "kills" : 10,
                            "deaths" : 1,
                            "assists" : 0
                        }
                    },
                    {
                        "id": "EUW1_232424252",
                        "championId": 12,
                        "championName": "Alistar",
                        "role": "SUPPORT",
                        "individualPosition": "UTILITY",
                        "teamPosition": "UTILITY",
                        "lane": "NONE",
                        "kills": 2,
                        "deaths": 2,
                        "assists": 4,
                        "assistMePings": 0,
                        "visionWardsBoughtInGame": 6,
                        "enemyMissingPings": 0,
                        "wardsPlaced": 11,
                        "gameFinishedCorrectly": true,
                        "gameDuration": 917,
                        "totalTimeSpentDead": 36,
                        "win": true,
                        "matchUp" : {
                            "championId" : 2,
                            "championName" : "Aatrox",
                            "teamPosition" : "TOP",
                            "kills" : 10,
                            "deaths" : 1,
                            "assists" : 0
                        }
                    },
                    {
                        "id": "EUW1_232424252",
                        "championId": 497,
                        "championName": "Rakan",
                        "role": "SUPPORT",
                        "individualPosition": "UTILITY",
                        "teamPosition": "UTILITY",
                        "lane": "BOTTOM",
                        "kills": 2,
                        "deaths": 3,
                        "assists": 21,
                        "assistMePings": 0,
                        "visionWardsBoughtInGame": 16,
                        "enemyMissingPings": 0,
                        "wardsPlaced": 51,
                        "gameFinishedCorrectly": true,
                        "gameDuration": 1712,
                        "totalTimeSpentDead": 67,
                        "win": true,
                        "matchUp" : {
                            "championId" : 2,
                            "championName" : "Aatrox",
                            "teamPosition" : "TOP",
                            "kills" : 10,
                            "deaths" : 1,
                            "assists" : 0
                        }
                    },
                    {
                        "id": "EUW1_232424252",
                        "championId": 235,
                        "championName": "Senna",
                        "role": "SUPPORT",
                        "individualPosition": "UTILITY",
                        "teamPosition": "UTILITY",
                        "lane": "BOTTOM",
                        "kills": 0,
                        "deaths": 2,
                        "assists": 13,
                        "assistMePings": 0,
                        "visionWardsBoughtInGame": 11,
                        "enemyMissingPings": 0,
                        "wardsPlaced": 32,
                        "gameFinishedCorrectly": true,
                        "gameDuration": 1856,
                        "totalTimeSpentDead": 73,
                        "win": true,
                        "matchUp" : {
                            "championId" : 2,
                            "championName" : "Aatrox",
                            "teamPosition" : "TOP",
                            "kills" : 10,
                            "deaths" : 1,
                            "assists" : 0
                        }
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
                    "teamPosition": "TOP",
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
                            "teamPosition": "Position",
                            "lane": "Lane",
                            "kills": 5,
                            "deaths": 1,
                            "assists": 10,
                            "assistMePings": 0,
                            "visionWardsBoughtInGame": 0,
                            "enemyMissingPings": 0,
                            "wardsPlaced": 0,
                            "gameFinishedCorrectly": true,
                            "gameDuration": 1800,
                            "totalTimeSpentDead": 300,
                            "win": true,
                            "matchUp" : {
                                "championId" : 2,
                                "championName" : "Aatrox",
                                "teamPosition" : "TOP",
                                "kills" : 10,
                                "deaths" : 1,
                                "assists" : 0
                            }
                        },
                        {
                            "id": "match2",
                            "championId": 1,
                            "championName": "ChampionName",
                            "role": "Role",
                            "individualPosition": "Position",
                            "teamPosition": "Position",
                            "lane": "Lane",
                            "kills": 5,
                            "deaths": 1,
                            "assists": 10,
                            "assistMePings": 0,
                            "visionWardsBoughtInGame": 0,
                            "enemyMissingPings": 0,
                            "wardsPlaced": 0,
                            "gameFinishedCorrectly": true,
                            "gameDuration": 1800,
                            "totalTimeSpentDead": 300,
                            "win": true,
                            "matchUp" : {
                                "championId" : 2,
                                "championName" : "Aatrox",
                                "teamPosition" : "TOP",
                                "kills" : 10,
                                "deaths" : 1,
                                "assists" : 0
                            }
                        },
                        {
                            "id": "match3",
                            "championId": 1,
                            "championName": "ChampionName",
                            "role": "Role",
                            "individualPosition": "Position",
                            "teamPosition": "Position",
                            "lane": "Lane",
                            "kills": 5,
                            "deaths": 1,
                            "assists": 10,
                            "assistMePings": 0,
                            "visionWardsBoughtInGame": 0,
                            "enemyMissingPings": 0,
                            "wardsPlaced": 0,
                            "gameFinishedCorrectly": true,
                            "gameDuration": 1800,
                            "totalTimeSpentDead": 300,
                            "win": true,
                            "matchUp" : {
                                "championId" : 2,
                                "championName" : "Aatrox",
                                "teamPosition" : "TOP",
                                "kills" : 10,
                                "deaths" : 1,
                                "assists" : 0
                            }
                        }
                    ]
                }
            }
        }"""

    val wowHardcoreData = """
        {
        		"type": "com.kos.clients.domain.HardcoreData",
        		"id": 50885014,
        		"name": "Kakoshi",
        		"level": 60,
        		"isDead": false,
        		"isSelfFound": false,
        		"averageItemLevel": 60,
        		"equippedItemLevel": 56,
        		"characterClass": "Rogue",
        		"race": "Human",
        		"gender": "Male",
        		"realm": "Soulseeker",
        		"region": "eu",
        		"guild": "ONLYFATS",
        		"experience": 0,
        		"items": [
        			{
        				"id": 12587,
        				"slot": "Head",
        				"quality": "Rare",
        				"name": "Eye of Rend",
        				"level": 63,
        				"binding": "Binds when picked up",
        				"requiredLevel": 58,
        				"itemSubclass": "Leather",
        				"armor": "143 Armor",
        				"stats": [
        					"+13 Strength",
        					"+7 Stamina"
        				],
        				"spells": [
        					"Equip: Improves your chance to get a critical strike by 2%."
        				],
        				"sellPrice": {
        					"header": "Sell Price:",
        					"gold": "2",
        					"silver": "10",
        					"copper": "31"
        				},
        				"durability": "Durability 60 / 60",
        				"weaponStats": null,
        				"icon": "https://render.worldofwarcraft.com/classic1x-eu/icons/56/inv_helmet_46.jpg",
        				"enchantments": []
        			},
        			{
        				"id": 15411,
        				"slot": "Neck",
        				"quality": "Rare",
        				"name": "Mark of Fordring",
        				"level": 63,
        				"binding": "Binds when picked up",
        				"requiredLevel": 0,
        				"itemSubclass": "Miscellaneous",
        				"armor": null,
        				"stats": [],
        				"spells": [
        					"Equip: Improves your chance to get a critical strike by 1%.",
        					"Equip: +26 Attack Power."
        				],
        				"sellPrice": {
        					"header": "Sell Price:",
        					"gold": "1",
        					"silver": "2",
        					"copper": "83"
        				},
        				"durability": null,
        				"weaponStats": null,
        				"icon": "https://render.worldofwarcraft.com/classic1x-eu/icons/56/inv_jewelry_talisman_07.jpg",
        				"enchantments": []
        			},
        			{
        				"id": 16708,
        				"slot": "Shoulders",
        				"quality": "Rare",
        				"name": "Shadowcraft Spaulders",
        				"level": 60,
        				"binding": "Binds when picked up",
        				"requiredLevel": 55,
        				"itemSubclass": "Leather",
        				"armor": "127 Armor",
        				"stats": [
        					"+22 Agility",
        					"+9 Stamina"
        				],
        				"spells": [],
        				"sellPrice": {
        					"header": "Sell Price:",
        					"gold": "1",
        					"silver": "81",
        					"copper": "75"
        				},
        				"durability": "Durability 60 / 60",
        				"weaponStats": null,
        				"icon": "https://render.worldofwarcraft.com/classic1x-eu/icons/56/inv_shoulder_07.jpg",
        				"enchantments": []
        			},
        			{
        				"id": 2575,
        				"slot": "Shirt",
        				"quality": "Common",
        				"name": "Red Linen Shirt",
        				"level": 10,
        				"binding": null,
        				"requiredLevel": 0,
        				"itemSubclass": "Miscellaneous",
        				"armor": null,
        				"stats": [],
        				"spells": [],
        				"sellPrice": {
        					"header": "Sell Price:",
        					"gold": "0",
        					"silver": "0",
        					"copper": "25"
        				},
        				"durability": null,
        				"weaponStats": null,
        				"icon": "https://render.worldofwarcraft.com/classic1x-eu/icons/56/inv_shirt_red_01.jpg",
        				"enchantments": []
        			},
        			{
        				"id": 13944,
        				"slot": "Chest",
        				"quality": "Rare",
        				"name": "Tombstone Breastplate",
        				"level": 62,
        				"binding": "Binds when picked up",
        				"requiredLevel": 57,
        				"itemSubclass": "Leather",
        				"armor": "174 Armor",
        				"stats": [
        					"+10 Strength",
        					"+10 Stamina"
        				],
        				"spells": [
        					"Equip: Improves your chance to get a critical strike by 2%."
        				],
        				"sellPrice": {
        					"header": "Sell Price:",
        					"gold": "2",
        					"silver": "68",
        					"copper": "8"
        				},
        				"durability": "Durability 100 / 100",
        				"weaponStats": null,
        				"icon": "https://render.worldofwarcraft.com/classic1x-eu/icons/56/inv_chest_chain_17.jpg",
        				"enchantments": []
        			},
        			{
        				"id": 16713,
        				"slot": "Waist",
        				"quality": "Rare",
        				"name": "Shadowcraft Belt",
        				"level": 58,
        				"binding": "Binds when equipped",
        				"requiredLevel": 53,
        				"itemSubclass": "Leather",
        				"armor": "93 Armor",
        				"stats": [
        					"+9 Strength",
        					"+14 Agility",
        					"+10 Stamina",
        					"+9 Spirit"
        				],
        				"spells": [],
        				"sellPrice": {
        					"header": "Sell Price:",
        					"gold": "1",
        					"silver": "11",
        					"copper": "96"
        				},
        				"durability": "Durability 35 / 35",
        				"weaponStats": null,
        				"icon": "https://render.worldofwarcraft.com/classic1x-eu/icons/56/inv_belt_03.jpg",
        				"enchantments": []
        			},
        			{
        				"id": 15062,
        				"slot": "Legs",
        				"quality": "Rare",
        				"name": "Devilsaur Leggings",
        				"level": 60,
        				"binding": "Binds when equipped",
        				"requiredLevel": 55,
        				"itemSubclass": "Leather",
        				"armor": "148 Armor",
        				"stats": [
        					"+12 Stamina"
        				],
        				"spells": [
        					"Equip: +46 Attack Power.",
        					"Equip: Improves your chance to get a critical strike by 1%."
        				],
        				"sellPrice": {
        					"header": "Sell Price:",
        					"gold": "2",
        					"silver": "57",
        					"copper": "9"
        				},
        				"durability": "Durability 75 / 75",
        				"weaponStats": null,
        				"icon": "https://render.worldofwarcraft.com/classic1x-eu/icons/56/inv_pants_wolf.jpg",
        				"enchantments": []
        			},
        			{
        				"id": 14641,
        				"slot": "Feet",
        				"quality": "Rare",
        				"name": "Cadaverous Walkers",
        				"level": 61,
        				"binding": "Binds when picked up",
        				"requiredLevel": 56,
        				"itemSubclass": "Leather",
        				"armor": "118 Armor",
        				"stats": [
        					"+20 Stamina"
        				],
        				"spells": [
        					"Equip: +24 Attack Power."
        				],
        				"sellPrice": {
        					"header": "Sell Price:",
        					"gold": "1",
        					"silver": "90",
        					"copper": "74"
        				},
        				"durability": "Durability 50 / 50",
        				"weaponStats": null,
        				"icon": "https://render.worldofwarcraft.com/classic1x-eu/icons/56/inv_boots_05.jpg",
        				"enchantments": []
        			},
        			{
        				"id": 13120,
        				"slot": "Wrist",
        				"quality": "Rare",
        				"name": "Deepfury Bracers",
        				"level": 55,
        				"binding": "Binds when equipped",
        				"requiredLevel": 50,
        				"itemSubclass": "Leather",
        				"armor": "69 Armor",
        				"stats": [
        					"+4 Strength",
        					"+15 Agility",
        					"+4 Stamina"
        				],
        				"spells": [],
        				"sellPrice": {
        					"header": "Sell Price:",
        					"gold": "0",
        					"silver": "95",
        					"copper": "5"
        				},
        				"durability": "Durability 35 / 35",
        				"weaponStats": null,
        				"icon": "https://render.worldofwarcraft.com/classic1x-eu/icons/56/inv_bracer_07.jpg",
        				"enchantments": []
        			},
        			{
        				"id": 15063,
        				"slot": "Hands",
        				"quality": "Rare",
        				"name": "Devilsaur Gauntlets",
        				"level": 58,
        				"binding": "Binds when equipped",
        				"requiredLevel": 53,
        				"itemSubclass": "Leather",
        				"armor": "103 Armor",
        				"stats": [
        					"+9 Stamina"
        				],
        				"spells": [
        					"Equip: +28 Attack Power.",
        					"Equip: Improves your chance to get a critical strike by 1%."
        				],
        				"sellPrice": {
        					"header": "Sell Price:",
        					"gold": "1",
        					"silver": "17",
        					"copper": "1"
        				},
        				"durability": "Durability 35 / 35",
        				"weaponStats": null,
        				"icon": "https://render.worldofwarcraft.com/classic1x-eu/icons/56/inv_gauntlets_26.jpg",
        				"enchantments": [
        					"Enchanted: Agility +7"
        				]
        			},
        			{
        				"id": 12548,
        				"slot": "Ring 1",
        				"quality": "Rare",
        				"name": "Magni's Will",
        				"level": 60,
        				"binding": "Binds when picked up",
        				"requiredLevel": 0,
        				"itemSubclass": "Miscellaneous",
        				"armor": null,
        				"stats": [
        					"+6 Strength",
        					"+7 Stamina"
        				],
        				"spells": [
        					"Equip: Improves your chance to get a critical strike by 1%."
        				],
        				"sellPrice": {
        					"header": "Sell Price:",
        					"gold": "0",
        					"silver": "71",
        					"copper": "2"
        				},
        				"durability": null,
        				"weaponStats": null,
        				"icon": "https://render.worldofwarcraft.com/classic1x-eu/icons/56/inv_jewelry_ring_05.jpg",
        				"enchantments": []
        			},
        			{
        				"id": 17713,
        				"slot": "Ring 2",
        				"quality": "Rare",
        				"name": "Blackstone Ring",
        				"level": 54,
        				"binding": "Binds when picked up",
        				"requiredLevel": 49,
        				"itemSubclass": "Miscellaneous",
        				"armor": null,
        				"stats": [
        					"+6 Stamina"
        				],
        				"spells": [
        					"Equip: +20 Attack Power.",
        					"Equip: Improves your chance to hit by 1%."
        				],
        				"sellPrice": {
        					"header": "Sell Price:",
        					"gold": "1",
        					"silver": "46",
        					"copper": "41"
        				},
        				"durability": null,
        				"weaponStats": null,
        				"icon": "https://render.worldofwarcraft.com/classic1x-eu/icons/56/inv_jewelry_ring_17.jpg",
        				"enchantments": []
        			},
        			{
        				"id": 209612,
        				"slot": "Trinket 1",
        				"quality": "Rare",
        				"name": "Insignia of the Alliance",
        				"level": 1,
        				"binding": "Binds when picked up",
        				"requiredLevel": 0,
        				"itemSubclass": "Miscellaneous",
        				"armor": null,
        				"stats": [],
        				"spells": [
        					"Use: Dispels all Charm, Fear and Polymorph effects. (5 Mins Cooldown)"
        				],
        				"sellPrice": {
        					"header": "Sell Price:",
        					"gold": "5",
        					"silver": "0",
        					"copper": "0"
        				},
        				"durability": null,
        				"weaponStats": null,
        				"icon": "https://render.worldofwarcraft.com/classic1x-eu/icons/56/inv_jewelry_trinketpvp_01.jpg",
        				"enchantments": []
        			},
        			{
        				"id": 11815,
        				"slot": "Trinket 2",
        				"quality": "Rare",
        				"name": "Hand of Justice",
        				"level": 58,
        				"binding": "Binds when picked up",
        				"requiredLevel": 53,
        				"itemSubclass": "Miscellaneous",
        				"armor": null,
        				"stats": [],
        				"spells": [
        					"Equip: 2% chance on melee hit to gain 1 extra attack.",
        					"Equip: +20 Attack Power."
        				],
        				"sellPrice": {
        					"header": "Sell Price:",
        					"gold": "1",
        					"silver": "0",
        					"copper": "0"
        				},
        				"durability": null,
        				"weaponStats": null,
        				"icon": "https://render.worldofwarcraft.com/classic1x-eu/icons/56/inv_jewelry_talisman_01.jpg",
        				"enchantments": []
        			},
        			{
        				"id": 13340,
        				"slot": "Back",
        				"quality": "Rare",
        				"name": "Cape of the Black Baron",
        				"level": 63,
        				"binding": "Binds when picked up",
        				"requiredLevel": 58,
        				"itemSubclass": "Cloth",
        				"armor": "45 Armor",
        				"stats": [
        					"+15 Agility"
        				],
        				"spells": [
        					"Equip: +20 Attack Power."
        				],
        				"sellPrice": {
        					"header": "Sell Price:",
        					"gold": "1",
        					"silver": "64",
        					"copper": "98"
        				},
        				"durability": null,
        				"weaponStats": null,
        				"icon": "https://render.worldofwarcraft.com/classic1x-eu/icons/56/inv_misc_cape_20.jpg",
        				"enchantments": [
        					"Enchanted: Agility +3"
        				]
        			},
        			{
        				"id": 12940,
        				"slot": "Main Hand",
        				"quality": "Rare",
        				"name": "Dal'Rend's Sacred Charge",
        				"level": 63,
        				"binding": "Binds when picked up",
        				"requiredLevel": 58,
        				"itemSubclass": "Sword",
        				"armor": null,
        				"stats": [
        					"+4 Strength"
        				],
        				"spells": [
        					"Equip: Improves your chance to get a critical strike by 1%."
        				],
        				"sellPrice": {
        					"header": "Sell Price:",
        					"gold": "5",
        					"silver": "48",
        					"copper": "12"
        				},
        				"durability": "Durability 90 / 90",
        				"weaponStats": {
        					"damage": "81 - 151 Damage",
        					"dps": "(41.4 damage per second)",
        					"attackSpeed": "Speed 2.80"
        				},
        				"icon": "https://render.worldofwarcraft.com/classic1x-eu/icons/56/inv_sword_43.jpg",
        				"enchantments": []
        			},
        			{
        				"id": 12939,
        				"slot": "Off Hand",
        				"quality": "Rare",
        				"name": "Dal'Rend's Tribal Guardian",
        				"level": 63,
        				"binding": "Binds when picked up",
        				"requiredLevel": 58,
        				"itemSubclass": "Sword",
        				"armor": "100 Armor",
        				"stats": [],
        				"spells": [
        					"Equip: Increased Defense +7."
        				],
        				"sellPrice": {
        					"header": "Sell Price:",
        					"gold": "6",
        					"silver": "3",
        					"copper": "63"
        				},
        				"durability": "Durability 90 / 90",
        				"weaponStats": {
        					"damage": "52 - 97 Damage",
        					"dps": "(41.4 damage per second)",
        					"attackSpeed": "Speed 1.80"
        				},
        				"icon": "https://render.worldofwarcraft.com/classic1x-eu/icons/56/inv_sword_40.jpg",
        				"enchantments": []
        			},
        			{
        				"id": 15294,
        				"slot": "Ranged",
        				"quality": "Uncommon",
        				"name": "Siege Bow",
        				"level": 53,
        				"binding": "Binds when equipped",
        				"requiredLevel": 48,
        				"itemSubclass": "Bow",
        				"armor": null,
        				"stats": [],
        				"spells": [],
        				"sellPrice": {
        					"header": "Sell Price:",
        					"gold": "2",
        					"silver": "1",
        					"copper": "35"
        				},
        				"durability": "Durability 65 / 65",
        				"weaponStats": {
        					"damage": "48 - 90 Damage",
        					"dps": "(24.6 damage per second)",
        					"attackSpeed": "Speed 2.80"
        				},
        				"icon": "https://render.worldofwarcraft.com/classic1x-eu/icons/56/inv_weapon_bow_07.jpg",
        				"enchantments": [
        					"+7 Agility"
        				]
        			}
        		],
        		"faction": "Alliance",
        		"avatar": "https://render.worldofwarcraft.com/classic1x-eu/character/soulseeker/150/50885014-avatar.jpg",
        		"stats": {
        			"health": 3033,
        			"resource": {
        				"type": "Energy",
        				"value": 100
        			},
        			"strength": 126,
        			"agility": 213,
        			"intellect": 35,
        			"stamina": 169,
        			"meleeCrit": 26.748499,
        			"attackPower": 813,
        			"mainHandStats": {
        				"minDamage": 243.60002,
        				"maxDamage": 313.60004,
        				"speed": 2.8,
        				"dps": 99.500015
        			},
        			"offHandStats": {
        				"minDamage": 117.3,
        				"maxDamage": 151.05,
        				"speed": 1.8,
        				"dps": 74.54167
        			},
        			"spellPower": 0,
        			"spellPenetration": 0,
        			"spellCrit": 9.999999,
        			"manaRegen": 0,
        			"manaRegenCombat": 0,
        			"armor": 1746,
        			"dodge": 14.857,
        			"parry": 10.16,
        			"block": 0,
        			"rangedCrit": 24.2685,
        			"spirit": 66,
        			"defense": 304,
        			"resistances": {
        				"fire": 81,
        				"holy": 0,
        				"shadow": 0,
        				"nature": 0,
        				"arcane": 0
        			}
        		},
        		"specializations": {
        			"wowHeadEmbeddedTalents": "025303105000000-3200552120050100231-",
        			"specializations": [
        				{
        					"name": "Assassination",
        					"points": 19,
        					"talents": [
        						{
        							"id": 272,
        							"rank": 2
        						},
        						{
        							"id": 270,
        							"rank": 5
        						},
        						{
        							"id": 277,
        							"rank": 3
        						},
        						{
        							"id": 281,
        							"rank": 1
        						},
        						{
        							"id": 269,
        							"rank": 5
        						},
        						{
        							"id": 273,
        							"rank": 3
        						}
        					]
        				},
        				{
        					"name": "Combat",
        					"points": 32,
        					"talents": [
        						{
        							"id": 201,
        							"rank": 2
        						},
        						{
        							"id": 203,
        							"rank": 3
        						},
        						{
        							"id": 187,
        							"rank": 5
        						},
        						{
        							"id": 301,
        							"rank": 1
        						},
        						{
        							"id": 181,
        							"rank": 5
        						},
        						{
        							"id": 221,
        							"rank": 5
        						},
        						{
        							"id": 222,
        							"rank": 2
        						},
        						{
        							"id": 223,
        							"rank": 1
        						},
        						{
        							"id": 204,
        							"rank": 2
        						},
        						{
        							"id": 1122,
        							"rank": 3
        						},
        						{
        							"id": 1703,
        							"rank": 2
        						},
        						{
        							"id": 205,
        							"rank": 1
        						}
        					]
        				}
        			]
        		},
        		"lastLogin": "2025-01-09T08:52:12Z"
        }
    """.trimIndent()

    private val now = OffsetDateTime.now()
    val outdatedDataCache = DataCache(1, wowData, now.minusHours(25), Game.WOW)
    val wowDataCache = DataCache(1, wowData, now, Game.WOW)
    val wowHardcoreDataCache = DataCache(1, wowHardcoreData, now, Game.WOW_HC)
    val lolDataCache = DataCache(2, lolData, now, Game.LOL)
    val anotherLolDataCache = DataCache(3, anotherLolData, now, Game.LOL)
}