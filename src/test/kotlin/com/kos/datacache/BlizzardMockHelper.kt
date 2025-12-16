package com.kos.datacache

import arrow.core.Either
import com.kos.clients.domain.*
import com.kos.common.error.HttpError
import com.kos.entities.WowEntity
import com.kos.entities.WowEntityRequest
import kotlin.random.Random

object BlizzardMockHelper {

    fun getToken(): Either<HttpError, TokenResponse> {
        return Either.Right(TokenResponse("token", "token", 10, "sub"))
    }

    fun getCharacterProfile(wowCharacter: WowEntity): Either<HttpError, GetWowCharacterResponse> {
        return Either.Right(
            GetWowCharacterResponse(
                wowCharacter.id,
                wowCharacter.name,
                60,
                false,
                false,
                50,
                50,
                "Hunter",
                "Alliance",
                "Night Elf",
                "Female",
                Realm("Stitches", 5220),
                "PERO LA QUERIA TANTO",
                0,
                1703019625000
            )
        )
    }

    fun getCharacterMedia(wowCharacter: WowEntity): Either<HttpError, GetWowMediaResponse> {
        return Either.Right(
            GetWowMediaResponse(listOf(AssetKeyValue("avatar", "${wowCharacter.id}")))
        )
    }

    fun getItemMedia(): Either<HttpError, GetWowMediaResponse> {
        return Either.Right(
            GetWowMediaResponse(listOf(AssetKeyValue("icon", "1.jpg")))
        )
    }

    fun getCharacterEquipment(): Either<HttpError, GetWowEquipmentResponse> {
        return Either.Right(getWowEquipmentResponse)
    }

    fun getCharacterStats(): Either<HttpError, GetWowCharacterStatsResponse> {
        return Either.Right(getWowStatsResponse)
    }

    fun getCharacterSpecializations(): Either<HttpError, GetWowSpecializationsResponse> {
        return Either.Right(getWowSpecializationsResponse)
    }

    fun getWowItemResponse(): Either<HttpError, GetWowItemResponse> {
        return Either.Right(getWowItemResponse)
    }

    fun getCharacterProfile(wowCharacter: WowEntityRequest): Either<HttpError, GetWowCharacterResponse> {
        return Either.Right(
            GetWowCharacterResponse(
                Random.nextLong(),
                wowCharacter.name,
                60,
                false,
                false,
                50,
                50,
                "Hunter",
                "Alliance",
                "Night Elf",
                "Female",
                Realm("Stitches", 5220),
                "PERO LA QUERIA TANTO",
                0,
                1703019625000
            )
        )
    }

    val hardcoreRealm = GetWowRealmResponse("Hardcore")
    val notHardcoreRealm = GetWowRealmResponse("Not Hardcore")

    val getWowCharacterResponse = GetWowCharacterResponse(
        id = 30758927,
        name = "Kumii",
        level = 60,
        isDead = false,
        averageItemLevel = 59,
        equippedItemLevel = 57,
        characterClass = "Hunter",
        faction = "Alliance",
        race = "Night Elf",
        gender = "Female",
        realm = Realm("Stitches", 5220),
        guild = "I CANT RELEASE",
        experience = 0,
        lastLogin = 1703019625000
    )

    val getWowCharacterResponseString = """
            {
                "_links": {
                    "self": {
                        "href": "https://eu.api.blizzard.com/profile/wow/character/stitches/kumii?namespace=profile-classic1x-eu"
                    }
                },
                "id": 30758927,
                "name": "Kumii",
                "gender": {
                    "type": "FEMALE",
                    "name": "Female"
                },
                "faction": {
                    "type": "ALLIANCE",
                    "name": "Alliance"
                },
                "race": {
                    "key": {
                        "href": "https://eu.api.blizzard.com/data/wow/playable-race/4?namespace=static-1.15.0_51892-classic1x-eu"
                    },
                    "name": "Night Elf",
                    "id": 4
                },
                "character_class": {
                    "key": {
                        "href": "https://eu.api.blizzard.com/data/wow/playable-class/3?namespace=static-1.15.0_51892-classic1x-eu"
                    },
                    "name": "Hunter",
                    "id": 3
                },
                "active_spec": {
                    "key": {
                        "href": "https://eu.api.blizzard.com/data/wow/playable-specialization/0?namespace=static-1.15.0_51892-classic1x-eu"
                    },
                    "id": 0
                },
                "realm": {
                    "key": {
                        "href": "https://eu.api.blizzard.com/data/wow/realm/5220?namespace=dynamic-classic1x-eu"
                    },
                    "name": "Stitches",
                    "id": 5220,
                    "slug": "stitches"
                },
                "guild": {
                    "key": {
                        "href": "https://eu.api.blizzard.com/data/wow/guild/stitches/i-cant-release?namespace=profile-classic1x-eu"
                    },
                    "name": "I CANT RELEASE",
                    "id": 1193006,
                    "realm": {
                        "key": {
                            "href": "https://eu.api.blizzard.com/data/wow/realm/5220?namespace=dynamic-classic1x-eu"
                        },
                        "name": "Stitches",
                        "id": 5220,
                        "slug": "stitches"
                    },
                    "faction": {
                        "type": "ALLIANCE",
                        "name": "Alliance"
                    }
                },
                "level": 60,
                "experience": 0,
                "titles": {
                    "href": "https://eu.api.blizzard.com/profile/wow/character/stitches/kumii/titles?namespace=profile-classic1x-eu"
                },
                "pvp_summary": {
                    "href": "https://eu.api.blizzard.com/profile/wow/character/stitches/kumii/pvp-summary?namespace=profile-classic1x-eu"
                },
                "media": {
                    "href": "https://eu.api.blizzard.com/profile/wow/character/stitches/kumii/character-media?namespace=profile-classic1x-eu"
                },
                "hunter_pets": {
                    "href": "https://eu.api.blizzard.com/profile/wow/character/stitches/kumii/hunter-pets?namespace=profile-classic1x-eu"
                },
                "last_login_timestamp": 1703019625000,
                "average_item_level": 59,
                "equipped_item_level": 57,
                "specializations": {
                    "href": "https://eu.api.blizzard.com/profile/wow/character/stitches/kumii/specializations?namespace=profile-classic1x-eu"
                },
                "statistics": {
                    "href": "https://eu.api.blizzard.com/profile/wow/character/stitches/kumii/statistics?namespace=profile-classic1x-eu"
                },
                "equipment": {
                    "href": "https://eu.api.blizzard.com/profile/wow/character/stitches/kumii/equipment?namespace=profile-classic1x-eu"
                },
                "appearance": {
                    "href": "https://eu.api.blizzard.com/profile/wow/character/stitches/kumii/appearance?namespace=profile-classic1x-eu"
                },
                "is_ghost": false
            }
        """.trimIndent()

    val getWowEquipmentResponseString = """
        {
          "_links": {
            "self": {
              "href": "https://eu.api.blizzard.com/profile/wow/character/stitches/kumii/equipment?namespace=profile-classic1x-eu"
            }
          },
          "character": {
            "key": {
              "href": "https://eu.api.blizzard.com/profile/wow/character/stitches/kumii?namespace=profile-classic1x-eu"
            },
            "name": "Kumii",
            "id": 30758927,
            "realm": {
              "key": {
                "href": "https://eu.api.blizzard.com/data/wow/realm/5220?namespace=dynamic-classic1x-eu"
              },
              "name": "Stitches",
              "id": 5220,
              "slug": "stitches"
            }
          },
          "equipped_items": [
            {
              "item": {
                "key": {
                  "href": "https://eu.api.blizzard.com/data/wow/item/18421?namespace=static-1.15.0_51892-classic1x-eu"
                },
                "id": 18421
              },
              "slot": {
                "type": "HEAD",
                "name": "Head"
              },
              "quantity": 1,
              "quality": {
                "type": "RARE",
                "name": "Rare"
              },
              "name": "Backwood Helm",
              "media": {
                "key": {
                  "href": "https://eu.api.blizzard.com/data/wow/media/item/18421?namespace=static-1.15.0_51892-classic1x-eu"
                },
                "id": 18421
              },
              "item_class": {
                "key": {
                  "href": "https://eu.api.blizzard.com/data/wow/item-class/4?namespace=static-1.15.0_51892-classic1x-eu"
                },
                "name": "Armor",
                "id": 4
              },
              "item_subclass": {
                "key": {
                  "href": "https://eu.api.blizzard.com/data/wow/item-class/4/item-subclass/3?namespace=static-1.15.0_51892-classic1x-eu"
                },
                "name": "Mail",
                "id": 3
              },
              "inventory_type": {
                "type": "HEAD",
                "name": "Head"
              },
              "binding": {
                "type": "ON_ACQUIRE",
                "name": "Binds when picked up"
              },
              "armor": {
                "value": 301,
                "display": {
                  "display_string": "301 Armor",
                  "color": {
                    "r": 255,
                    "g": 255,
                    "b": 255,
                    "a": 1.0
                  }
                }
              },
              "stats": [
                {
                  "type": {
                    "type": "AGILITY",
                    "name": "Agility"
                  },
                  "value": 21,
                  "display": {
                    "display_string": "+21 Agility",
                    "color": {
                      "r": 255,
                      "g": 255,
                      "b": 255,
                      "a": 1.0
                    }
                  }
                },
                {
                  "type": {
                    "type": "STAMINA",
                    "name": "Stamina"
                  },
                  "value": 13,
                  "display": {
                    "display_string": "+13 Stamina",
                    "color": {
                      "r": 255,
                      "g": 255,
                      "b": 255,
                      "a": 1.0
                    }
                  }
                },
                {
                  "type": {
                    "type": "INTELLECT",
                    "name": "Intellect"
                  },
                  "value": 9,
                  "display": {
                    "display_string": "+9 Intellect",
                    "color": {
                      "r": 255,
                      "g": 255,
                      "b": 255,
                      "a": 1.0
                    }
                  }
                },
                {
                  "type": {
                    "type": "SPIRIT",
                    "name": "Spirit"
                  },
                  "value": 9,
                  "display": {
                    "display_string": "+9 Spirit",
                    "color": {
                      "r": 255,
                      "g": 255,
                      "b": 255,
                      "a": 1.0
                    }
                  }
                }
              ],
              "spells": [
                {
                  "spell": {
                    "key": {
                      "href": "https://eu.api.blizzard.com/data/wow/spell/7597?namespace=static-1.15.0_51892-classic1x-eu"
                    },
                    "name": "Increased Critical 1",
                    "id": 7597
                  },
                  "description": "Equip: Improves your chance to get a critical strike by 1%."
                }
              ],
              "sell_price": {
                "value": 25741,
                "display_strings": {
                  "header": "Sell Price:",
                  "gold": "2",
                  "silver": "57",
                  "copper": "41"
                }
              },
              "durability": {
                "value": 53,
                "display_string": "Durability 53 / 70"
              }
            }
          ]
        }
    """.trimIndent()

    val getWowEquipmentResponse = GetWowEquipmentResponse(
        equippedItems = listOf(
            WowEquippedItemResponse(
                item = WowItemId(id = 18421),
                slot = WowItemSlot(name = "Head"),
                quality = WowItemQuality(type = "RARE"),
                name = "Backwood Helm",
                enchantments = listOf()
            )
        )
    )

    val getWowStatsResponseString = """
        {
            "_links": {
                "self": {
                    "href": "https://eu.api.blizzard.com/profile/wow/character/stitches/kumii/statistics?namespace=profile-classic1x-eu"
                }
            },
            "health": 3323,
            "power": 2775,
            "power_type": {
                "key": {
                    "href": "https://eu.api.blizzard.com/data/wow/power-type/0?namespace=static-1.15.0_51892-classic1x-eu"
                },
                "name": "Mana",
                "id": 0
            },
            "strength": {
                "base": 52,
                "effective": 52
            },
            "agility": {
                "base": 130,
                "effective": 269
            },
            "intellect": {
                "base": 65,
                "effective": 89
            },
            "stamina": {
                "base": 89,
                "effective": 179
            },
            "melee_crit": {
                "rating": 0,
                "rating_bonus": 0.0,
                "value": 11.9641
            },
            "attack_power": 627,
            "main_hand_damage_min": 289.27142,
            "main_hand_damage_max": 358.27142,
            "main_hand_speed": 3.4,
            "main_hand_dps": 95.22688,
            "off_hand_damage_min": 45.357147,
            "off_hand_damage_max": 45.357147,
            "off_hand_speed": 2.0,
            "off_hand_dps": 22.678574,
            "spell_power": 0,
            "spell_penetration": 0,
            "spell_crit": {
                "rating": 0,
                "rating_bonus": 0.0,
                "value": 5.0685
            },
            "mana_regen": 77.0,
            "mana_regen_combat": 77.0,
            "armor": {
                "base": 2082,
                "effective": 2122
            },
            "dodge": {
                "rating": 0,
                "rating_bonus": 0.0,
                "value": 10.5413
            },
            "parry": {
                "rating": 0,
                "rating_bonus": 0.0,
                "value": 6.3999996
            },
            "block": {
                "rating": 0,
                "rating_bonus": 0.0,
                "value": 0.0
            },
            "ranged_crit": {
                "rating": 0,
                "rating_bonus": 0.0,
                "value": 17.084099
            },
            "character": {
                "key": {
                    "href": "https://eu.api.blizzard.com/profile/wow/character/stitches/kumii?namespace=profile-classic1x-eu"
                },
                "name": "Kumii",
                "id": 30758927,
                "realm": {
                    "key": {
                        "href": "https://eu.api.blizzard.com/data/wow/realm/5220?namespace=dynamic-classic1x-eu"
                    },
                    "name": "Stitches",
                    "id": 5220,
                    "slug": "stitches"
                }
            },
            "spirit": {
                "base": 70,
                "effective": 79
            },
            "defense": {
                "base": 285,
                "effective": 285
            },
            "fire_resistance": {
                "base": 0,
                "effective": 0
            },
            "holy_resistance": {
                "base": 0,
                "effective": 0
            },
            "shadow_resistance": {
                "base": 0,
                "effective": 0
            },
            "nature_resistance": {
                "base": 10,
                "effective": 10
            },
            "arcane_resistance": {
                "base": 0,
                "effective": 0
            }
        }
    """.trimIndent()

    val getWowStatsResponse = GetWowCharacterStatsResponse(
        health = 3323,
        power = 2775,
        powerType = "Mana",
        strength = 52,
        agility = 269,
        intellect = 89,
        stamina = 179,
        meleeCrit = 11.9641,
        attackPower = 627,
        mainHandDamageMin = 289.27142,
        mainHandDamageMax = 358.27142,
        mainHandSpeed = 3.4,
        mainHandDps = 95.22688,
        offHandDamageMin = 45.357147,
        offHandDamageMax = 45.357147,
        offHandSpeed = 2.0,
        offHandDps = 22.678574,
        spellPower = 0.0,
        spellPenetration = 0.0,
        spellCrit = 5.0685,
        manaRegen = 77.0,
        manaRegenCombat = 77.0,
        armor = 2122,
        dodge = 10.5413,
        parry = 6.3999996,
        block = 0.0,
        rangedCrit = 17.084099,
        spirit = 79,
        defense = 285,
        fireResistance = 0,
        holyResistance = 0,
        shadowResistance = 0,
        natureResistance = 10,
        arcaneResistance = 0
    )

    val getWowSpecializationsResponseString = """
        {
            "_links": {
                "self": {
                    "href": "https://eu.api.blizzard.com/profile/wow/character/stitches/kumii/specializations?namespace=profile-classic1x-eu"
                }
            },
            "character": {
                "key": {
                    "href": "https://eu.api.blizzard.com/profile/wow/character/stitches/kumii?namespace=profile-classic1x-eu"
                },
                "name": "Kumii",
                "id": 30758927,
                "realm": {
                    "key": {
                        "href": "https://eu.api.blizzard.com/data/wow/realm/5220?namespace=dynamic-classic1x-eu"
                    },
                    "name": "Stitches",
                    "id": 5220,
                    "slug": "stitches"
                }
            },
            "specialization_groups": [
                {
                    "is_active": true,
                    "specializations": [
                        {
                            "talents": [
                                {
                                    "talent": {
                                        "id": 1382
                                    },
                                    "spell_tooltip": {
                                        "spell": {
                                            "name": "Improved Aspect of the Hawk",
                                            "id": 19553
                                        },
                                        "description": "While Aspect of the Hawk is active, all normal ranged attacks have a 2% chance of increasing ranged attack speed by 30% for 12 sec.",
                                        "cast_time": "Passive",
                                        "power_cost": null
                                    },
                                    "talent_rank": 2
                                }
                            ],
                            "specialization_name": "Beast Mastery",
                            "spent_points": 2
                        },
                        {
                            "talents": [
                                {
                                    "talent": {
                                        "id": 1342
                                    },
                                    "spell_tooltip": {
                                        "spell": {
                                            "name": "Efficiency",
                                            "id": 19420
                                        },
                                        "description": "Reduces the Mana cost of your Shots and Stings by 10%.",
                                        "cast_time": "Passive"
                                    },
                                    "talent_rank": 5
                                },
                                {
                                    "talent": {
                                        "id": 1344
                                    },
                                    "spell_tooltip": {
                                        "spell": {
                                            "name": "Lethal Shots",
                                            "id": 19431
                                        },
                                        "description": "Increases your critical strike chance with ranged weapons by 5%.",
                                        "cast_time": "Passive",
                                        "power_cost": null
                                    },
                                    "talent_rank": 5
                                },
                                {
                                    "talent": {
                                        "id": 1343
                                    },
                                    "spell_tooltip": {
                                        "spell": {
                                            "name": "Improved Hunter's Mark",
                                            "id": 19422
                                        },
                                        "description": "Increases the Ranged Attack Power bonus of your Hunter's Mark spell by 6%.",
                                        "cast_time": "Passive",
                                        "power_cost": null
                                    },
                                    "talent_rank": 2
                                },
                                {
                                    "talent": {
                                        "id": 1352
                                    },
                                    "spell_tooltip": {
                                        "spell": {
                                            "name": "Hawk Eye",
                                            "id": 19500
                                        },
                                        "description": "Increases the range of your ranged weapons by 6 yards.",
                                        "cast_time": "Passive"
                                    },
                                    "talent_rank": 3
                                },
                                {
                                    "talent": {
                                        "id": 1349
                                    },
                                    "spell_tooltip": {
                                        "spell": {
                                            "name": "Mortal Shots",
                                            "id": 19490
                                        },
                                        "description": "Increases your ranged weapon critical strike damage bonus by 30%.",
                                        "cast_time": "Passive"
                                    },
                                    "talent_rank": 5
                                },
                                {
                                    "talent": {
                                        "id": 1345
                                    },
                                    "spell_tooltip": {
                                        "spell": {
                                            "name": "Aimed Shot",
                                            "id": 19434
                                        },
                                        "description": "An aimed shot that increases ranged damage by 70.",
                                        "cast_time": "3 sec cast",
                                        "power_cost": "68 Mana",
                                        "range": "8-35 yd range",
                                        "cooldown": "6 sec cooldown"
                                    },
                                    "talent_rank": 1
                                },
                                {
                                    "talent": {
                                        "id": 1347
                                    },
                                    "spell_tooltip": {
                                        "spell": {
                                            "name": "Barrage",
                                            "id": 24691
                                        },
                                        "description": "Increases the damage done by your Multi-Shot and Volley spells by 15%.",
                                        "cast_time": "Passive",
                                        "power_cost": null
                                    },
                                    "talent_rank": 3
                                },
                                {
                                    "talent": {
                                        "id": 1353
                                    },
                                    "spell_tooltip": {
                                        "spell": {
                                            "name": "Scatter Shot",
                                            "id": 19503
                                        },
                                        "description": "A short-range shot that deals 50% weapon damage and disorients the target for 4 sec.  Any damage caused will remove the effect.  Turns off your attack when used.",
                                        "cast_time": "Instant cast",
                                        "power_cost": "123 Mana",
                                        "range": "15 yd range",
                                        "cooldown": "30 sec cooldown"
                                    },
                                    "talent_rank": 1
                                },
                                {
                                    "talent": {
                                        "id": 1362
                                    },
                                    "spell_tooltip": {
                                        "spell": {
                                            "name": "Ranged Weapon Specialization",
                                            "id": 19511
                                        },
                                        "description": "Increases the damage you deal with ranged weapons by 5%.",
                                        "cast_time": "Passive",
                                        "power_cost": null
                                    },
                                    "talent_rank": 5
                                },
                                {
                                    "talent": {
                                        "id": 1361
                                    },
                                    "spell_tooltip": {
                                        "spell": {
                                            "name": "Trueshot Aura",
                                            "id": 19506
                                        },
                                        "description": "Increases the attack power of party members within 45 yards by 50.  Lasts 30 min.",
                                        "cast_time": "Instant cast",
                                        "power_cost": "325 Mana"
                                    },
                                    "talent_rank": 1
                                }
                            ],
                            "specialization_name": "Marksmanship",
                            "spent_points": 31
                        },
                        {
                            "talents": [
                                {
                                    "talent": {
                                        "id": 1623
                                    },
                                    "spell_tooltip": {
                                        "spell": {
                                            "name": "Monster Slaying",
                                            "id": 24295
                                        },
                                        "description": "Increases all damage caused against Beasts, Giants and Dragonkin targets by 3% and increases critical damage caused against Beasts, Giants and Dragonkin targets by an additional 3%.",
                                        "cast_time": "Passive",
                                        "power_cost": null
                                    },
                                    "talent_rank": 3
                                },
                                {
                                    "talent": {
                                        "id": 1301
                                    },
                                    "spell_tooltip": {
                                        "spell": {
                                            "name": "Humanoid Slaying",
                                            "id": 19153
                                        },
                                        "description": "Increases all damage caused against Humanoid targets by 3% and increases critical damage caused against Humanoid targets by an additional 3%.",
                                        "cast_time": "Passive",
                                        "power_cost": null
                                    },
                                    "talent_rank": 3
                                },
                                {
                                    "talent": {
                                        "id": 1311
                                    },
                                    "spell_tooltip": {
                                        "spell": {
                                            "name": "Deflection",
                                            "id": 19297
                                        },
                                        "description": "Increases your Parry chance by 2%.",
                                        "cast_time": "Passive",
                                        "power_cost": null
                                    },
                                    "talent_rank": 2
                                },
                                {
                                    "talent": {
                                        "id": 1621
                                    },
                                    "spell_tooltip": {
                                        "spell": {
                                            "name": "Savage Strikes",
                                            "id": 19160
                                        },
                                        "description": "Increases the critical strike chance of Raptor Strike and Mongoose Bite by 20%.",
                                        "cast_time": "Passive",
                                        "power_cost": null
                                    },
                                    "talent_rank": 2
                                },
                                {
                                    "talent": {
                                        "id": 1308
                                    },
                                    "spell_tooltip": {
                                        "spell": {
                                            "name": "Deterrence",
                                            "id": 19263
                                        },
                                        "description": "When activated, increases your Dodge and Parry chance by 25% for 10 sec.",
                                        "cast_time": "Instant",
                                        "power_cost": null,
                                        "cooldown": "5 min cooldown"
                                    },
                                    "talent_rank": 1
                                },
                                {
                                    "talent": {
                                        "id": 1622
                                    },
                                    "spell_tooltip": {
                                        "spell": {
                                            "name": "Survivalist",
                                            "id": 19258
                                        },
                                        "description": "Increases total health by 8%.",
                                        "cast_time": "Passive",
                                        "power_cost": null
                                    },
                                    "talent_rank": 4
                                },
                                {
                                    "talent": {
                                        "id": 1310
                                    },
                                    "spell_tooltip": {
                                        "spell": {
                                            "name": "Surefooted",
                                            "id": 24283
                                        },
                                        "description": "Increases hit chance by 3% and increases the chance movement impairing effects will be resisted by an additional 15%.",
                                        "cast_time": "Passive",
                                        "power_cost": null
                                    },
                                    "talent_rank": 3
                                }
                            ],
                            "specialization_name": "Survival",
                            "spent_points": 18
                        }
                    ]
                }
            ]
        }
    """.trimIndent()

    val getWowSpecializationsResponse = GetWowSpecializationsResponse(
        specializationGroups = listOf(
            SpecializationGroup(
                specializations = listOf(
                    Specialization(
                        specializationName = "Beast Mastery",
                        spentPoints = 2,
                        talents = listOf(
                            Talent(
                                talent = TalentInfo(id = 1382),
                                talentRank = 2
                            )
                        )
                    ),
                    Specialization(
                        specializationName = "Marksmanship",
                        spentPoints = 31,
                        talents = listOf(
                            Talent(TalentInfo(id = 1342), talentRank = 5),
                            Talent(TalentInfo(id = 1344), talentRank = 5),
                            Talent(TalentInfo(id = 1343), talentRank = 2),
                            Talent(TalentInfo(id = 1352), talentRank = 3),
                            Talent(TalentInfo(id = 1349), talentRank = 5),
                            Talent(TalentInfo(id = 1345), talentRank = 1),
                            Talent(TalentInfo(id = 1347), talentRank = 3),
                            Talent(TalentInfo(id = 1353), talentRank = 1),
                            Talent(TalentInfo(id = 1362), talentRank = 5),
                            Talent(TalentInfo(id = 1361), talentRank = 1)
                        )
                    ),
                    Specialization(
                        specializationName = "Survival",
                        spentPoints = 18,
                        talents = listOf(
                            Talent(TalentInfo(id = 1623), talentRank = 3),
                            Talent(TalentInfo(id = 1301), talentRank = 3),
                            Talent(TalentInfo(id = 1311), talentRank = 2),
                            Talent(TalentInfo(id = 1621), talentRank = 2),
                            Talent(TalentInfo(id = 1308), talentRank = 1),
                            Talent(TalentInfo(id = 1622), talentRank = 4),
                            Talent(TalentInfo(id = 1310), talentRank = 3)
                        )
                    )
                ),
                isActive = true
            )
        )
    )

    val getWowItemResponseString = """
        {
            "_links": {
                "self": {
                    "href": "https://eu.api.blizzard.com/data/wow/item/18421?namespace=static-1.15.4_56573-classic1x-eu"
                }
            },
            "id": 18421,
            "name": "Backwood Helm",
            "quality": {
                "type": "RARE",
                "name": "Rare"
            },
            "level": 63,
            "required_level": 0,
            "media": {
                "key": {
                    "href": "https://eu.api.blizzard.com/data/wow/media/item/18421?namespace=static-1.15.4_56573-classic1x-eu"
                },
                "id": 18421
            },
            "item_class": {
                "key": {
                    "href": "https://eu.api.blizzard.com/data/wow/item-class/4?namespace=static-1.15.4_56573-classic1x-eu"
                },
                "name": "Armor",
                "id": 4
            },
            "item_subclass": {
                "key": {
                    "href": "https://eu.api.blizzard.com/data/wow/item-class/4/item-subclass/3?namespace=static-1.15.4_56573-classic1x-eu"
                },
                "name": "Mail",
                "id": 3
            },
            "inventory_type": {
                "type": "HEAD",
                "name": "Head"
            },
            "purchase_price": 128705,
            "sell_price": 25741,
            "max_count": 0,
            "is_equippable": true,
            "is_stackable": false,
            "preview_item": {
                "item": {
                    "key": {
                        "href": "https://eu.api.blizzard.com/data/wow/item/18421?namespace=static-1.15.4_56573-classic1x-eu"
                    },
                    "id": 18421
                },
                "quality": {
                    "type": "RARE",
                    "name": "Rare"
                },
                "name": "Backwood Helm",
                "media": {
                    "key": {
                        "href": "https://eu.api.blizzard.com/data/wow/media/item/18421?namespace=static-1.15.4_56573-classic1x-eu"
                    },
                    "id": 18421
                },
                "item_class": {
                    "key": {
                        "href": "https://eu.api.blizzard.com/data/wow/item-class/4?namespace=static-1.15.4_56573-classic1x-eu"
                    },
                    "name": "Armor",
                    "id": 4
                },
                "item_subclass": {
                    "key": {
                        "href": "https://eu.api.blizzard.com/data/wow/item-class/4/item-subclass/3?namespace=static-1.15.4_56573-classic1x-eu"
                    },
                    "name": "Mail",
                    "id": 3
                },
                "inventory_type": {
                    "type": "HEAD",
                    "name": "Head"
                },
                "binding": {
                    "type": "ON_ACQUIRE",
                    "name": "Binds when picked up"
                },
                "armor": {
                    "value": 301,
                    "display": {
                        "display_string": "301 Armor",
                        "color": {
                            "r": 255,
                            "g": 255,
                            "b": 255,
                            "a": 1.0
                        }
                    }
                },
                "stats": [
                    {
                        "type": {
                            "type": "AGILITY",
                            "name": "Agility"
                        },
                        "value": 21,
                        "display": {
                            "display_string": "+21 Agility",
                            "color": {
                                "r": 255,
                                "g": 255,
                                "b": 255,
                                "a": 1.0
                            }
                        }
                    },
                    {
                        "type": {
                            "type": "STAMINA",
                            "name": "Stamina"
                        },
                        "value": 13,
                        "display": {
                            "display_string": "+13 Stamina",
                            "color": {
                                "r": 255,
                                "g": 255,
                                "b": 255,
                                "a": 1.0
                            }
                        }
                    },
                    {
                        "type": {
                            "type": "INTELLECT",
                            "name": "Intellect"
                        },
                        "value": 9,
                        "display": {
                            "display_string": "+9 Intellect",
                            "color": {
                                "r": 255,
                                "g": 255,
                                "b": 255,
                                "a": 1.0
                            }
                        }
                    },
                    {
                        "type": {
                            "type": "SPIRIT",
                            "name": "Spirit"
                        },
                        "value": 9,
                        "display": {
                            "display_string": "+9 Spirit",
                            "color": {
                                "r": 255,
                                "g": 255,
                                "b": 255,
                                "a": 1.0
                            }
                        }
                    }
                ],
                "spells": [
                    {
                        "spell": {
                            "key": {
                                "href": "https://eu.api.blizzard.com/data/wow/spell/7597?namespace=static-1.15.4_56573-classic1x-eu"
                            },
                            "name": "Increased Critical 1",
                            "id": 7597
                        },
                        "description": "Equip: Improves your chance to get a critical strike by 1%."
                    }
                ],
                "sell_price": {
                    "value": 25741,
                    "display_strings": {
                        "header": "Sell Price:",
                        "gold": "2",
                        "silver": "57",
                        "copper": "41"
                    }
                },
                "durability": {
                    "value": 70,
                    "display_string": "Durability 70 / 70"
                }
            },
            "purchase_quantity": 1
        }
    """.trimIndent()

    val getWowItemResponse = GetWowItemResponse(
        level = 63,
        requiredLevel = 0,
        id = 18421,
        name = "Backwood Helm",
        previewItem = WowPreviewItem(
            quality = "Rare",
            itemSubclass = "Mail",
            slot = "Head",
            armor = "301 Armor",
            stats = listOf("+21 Agility", "+13 Stamina", "+9 Intellect", "+9 Spirit"),
            spells = listOf("Equip: Improves your chance to get a critical strike by 1%."),
            sellPrice = WowPriceResponse(header = "Sell Price:", gold = "2", silver = "57", copper = "41"),
            durability = "Durability 70 / 70",
            binding = "Binds when picked up",
            weapon = null
        )
    )


}