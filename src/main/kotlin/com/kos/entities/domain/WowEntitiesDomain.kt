package com.kos.entities.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

data class Spec(val name: String, val externalSpec: Int, val internalSpec: Int, val role: String)
data class Class(val `class`: String, val specs: List<Spec>)

@Serializable
@SerialName("wow")
data class WowEntityRequest(
    override val name: String,
    val region: String,
    val realm: String,
    val blizzardId: Long? = null,
    override val alias: String? = null
) :
    EntityRequest, InsertEntityRequest {
    override fun toEntity(id: Long) = WowEntity(id, name, region, realm, blizzardId)
    override fun same(other: Entity): Boolean {
        return when (other) {
            is WowEntity -> if (this.blizzardId != null && other.blizzardId != null) {
                this.blizzardId == other.blizzardId
            } else {
                this.name == other.name && this.region == other.region && this.realm == other.realm
            }

            else -> false
        }
    }

    override fun toRequest(): EntityRequest = this.copy(blizzardId = null)
    override fun toResponse(): EntityResponse = WowEntityResponse(name, region, realm)
}

@Serializable
data class WowEntityResponse(
    override val name: String,
    val region: String,
    val realm: String
) : EntityResponse

@Serializable
@SerialName("wow")
data class WowEntity(
    override val id: Long,
    override val name: String,
    val region: String,
    val realm: String,
    val blizzardId: Long?
) : Entity {
    fun specsWithName(`class`: String): List<Spec> = classes.find { it.`class` == `class` }?.specs.orEmpty()
    override fun toRequest(): WowEntityRequest = WowEntityRequest(this.name, this.region, this.realm, null)
}

fun findClassAndSpec(blizzardSpecId: Int): Pair<String, Spec>? =
    classes.firstNotNullOfOrNull { wowClass ->
        wowClass.specs.find { it.externalSpec == blizzardSpecId }?.let { wowClass.`class` to it }
    }

//TODO: this should be static data in db
val classes: List<Class> = listOf(
    Class(
        "Priest", listOf(
            Spec(
                "Discipline",
                256,
                0,
                "healer"
            ),
            Spec(
                "Holy Priest",
                257,
                1,
                "healer"
            ),
            Spec(
                "Shadow",
                258,
                2,
                "dps"
            )
        )
    ),
    Class(
        "Paladin", listOf(
            Spec(
                "Holy Paladin",
                65,
                0,
                "healer"
            ),
            Spec(
                "Protection Paladin",
                66,
                1,
                "tank"
            ),
            Spec(
                "Retribution",
                70,
                2,
                "dps"
            )
        )
    ),
    Class(
        "Paladin", listOf(
            Spec(
                "Holy Paladin",
                65,
                0,
                "healer"
            ),
            Spec(
                "Protection Paladin",
                66,
                1,
                "tank"
            ),
            Spec(
                "Retribution",
                70,
                2,
                "dps"
            )
        )
    ),
    Class(
        "Rogue", listOf(
            Spec(
                "Subtlety",
                261,
                2,
                "dps"
            ),
            Spec(
                "Outlaw",
                260,
                1,
                "dps"
            ),
            Spec(
                "Assassination",
                259,
                0,
                "dps"
            )
        )
    ),
    Class(
        "Druid", listOf(
            Spec(
                "Balance",
                102,
                0,
                "dps"
            ),
            Spec(
                "Feral",
                103,
                1,
                "dps"
            ),
            Spec(
                "Guardian",
                104,
                2,
                "tank"
            ),
            Spec(
                "Restoration Druid",
                105,
                3,
                "healer"
            )
        )
    ),
    Class(
        "Monk", listOf(
            Spec(
                "Brew Master",
                268,
                0,
                "tank"
            ),
            Spec(
                "Wind Walker",
                269,
                2,
                "dps"
            ),
            Spec(
                "Mist Weaver",
                270,
                1,
                "healer"
            )
        )
    ),
    Class(
        "Evoker", listOf(
            Spec(
                "Devastation",
                1467,
                0,
                "dps"
            ),
            Spec(
                "Preservation",
                1468,
                1,
                "healer"
            ),
            Spec(
                "Augmentation",
                1473,
                2,
                "dps"
            )
        )
    ),
    Class(
        "Shaman", listOf(
            Spec(
                "Elemental",
                262,
                0,
                "dps"
            ),
            Spec(
                "Enhancement",
                263,
                1,
                "dps"
            ),
            Spec(
                "Restoration Shaman",
                264,
                2,
                "healer"
            )
        )
    ),
    Class(
        "Warrior", listOf(
            Spec(
                "Arms",
                71,
                0,
                "dps"
            ),
            Spec(
                "Fury",
                72,
                1,
                "dps"
            ),
            Spec(
                "Protection Warrior",
                73,
                2,
                "tank"
            )
        )
    ),
    Class(
        "Hunter", listOf(
            Spec(
                "Beast Mastery",
                253,
                0,
                "dps"
            ),
            Spec(
                "Marksmanship",
                254,
                1,
                "dps"
            ),
            Spec(
                "Survival",
                255,
                2,
                "dps"
            )
        )
    ),
    Class(
        "Demon Hunter", listOf(
            Spec(
                "Havoc",
                577,
                0,
                "dps"
            ),
            Spec(
                "Vengeance",
                581,
                1,
                "tank"
            ),
            Spec(
                "Devourer",
                1480,
                2,
                "dps"
            )
        )
    ),
    Class(
        "Mage", listOf(
            Spec(
                "Arcane",
                62,
                0,
                "dps"
            ),
            Spec(
                "Fire",
                63,
                1,
                "dps"
            ),
            Spec(
                "Frost Mage",
                64,
                2,
                "dps"
            )
        )
    ),
    Class(
        "Death Knight",
        listOf(
            Spec(
                "Blood",
                250,
                0,
                "tank"
            ),
            Spec(
                "Frost Death Knight",
                251,
                1,
                "dps"
            ),
            Spec(
                "Unholy",
                252,
                2,
                "dps"
            )
        ),
    ),
    Class(
        "Warlock", listOf(
            Spec(
                "Affliction",
                265,
                0,
                "dps"
            ),
            Spec(
                "Demonology",
                266,
                1,
                "dps"
            ),
            Spec(
                "Destruction",
                267,
                2,
                "dps"
            )
        )
    )
)

data class WowCharacter(
    val id: Long,
    val name: String,
    val level: Int,
    val isDead: Boolean,
    val isSelfFound: Boolean,
    val averageItemLevel: Int,
    val equippedItemLevel: Int,
    val characterClass: String,
    val faction: String,
    val race: String,
    val gender: String,
    val realmId: Long,
    val realm: String,
    val guild: String?,
    val experience: Int,
    val lastLogin: OffsetDateTime
)

data class WowRealm(val isHardcore: Boolean)
