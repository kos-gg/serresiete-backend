package com.kos.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import kotlin.test.Test

class ThirdPartyDecouplingTest {

    private val productionClasses = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .importPackages("com.kos")

    private val compositionRoot = "com.kos.ApplicationKt"

    private val pendingToDecouple: Set<String> = setOf(
        "com.kos.datacache.domain.EnrichedMythicPlusRun",
        "com.kos.datacache.domain.HardcoreData",
        "com.kos.datacache.domain.LeagueMatchData",
        "com.kos.datacache.domain.MythicPlusRanksWithSpecs",
        "com.kos.datacache.domain.RaiderIoData",
        "com.kos.datacache.domain.RiotData",
        "com.kos.datacache.domain.WowPrice",
        "com.kos.datacache.domain.WowSpecialization",
        "com.kos.datacache.domain.WowStats",
        "com.kos.datacache.domain.WowWeaponDisplayableStats",
        "com.kos.scripts.BlizzardStaticItemsConverterKt",
        "com.kos.scripts.StaticWowItem",
        "com.kos.sources.SourcesController",
        "com.kos.sources.SourcesRoutesKt",
        "com.kos.sources.SourcesService",
        "com.kos.sources.lol.LolEntityResolver",
        "com.kos.sources.lol.LolEntitySynchronizer",
        "com.kos.sources.lol.LolEntityUpdater",
        "com.kos.sources.wow.WowEntityResolver",
        "com.kos.sources.wow.WowEntitySynchronizer",
        "com.kos.sources.wow.staticdata.wowseason.WowSeasonService",
        "com.kos.sources.wowhc.WowHardcoreEntityResolver",
        "com.kos.sources.wowhc.WowHardcoreEntitySynchronizer",
        "com.kos.sources.wowhc.staticdata.wowitems.WowItemsDatabaseRepository",
        "com.kos.sources.wowhc.staticdata.wowitems.WowItemsRepository",
        "com.kos.sources.wowhc.staticdata.wowitems.scripts.BlizzardStaticItemsConverterKt",
        "com.kos.sources.wowhc.staticdata.wowitems.scripts.StaticWowItem"
    )

    private val allowedToKnowThirdPartyModels = DescribedPredicate.describe<JavaClass>(
        "client, composition root or pending to decouple"
    ) { javaClass ->
        javaClass.packageName.startsWith("com.kos.clients") ||
            javaClass.name == compositionRoot ||
            pendingToDecouple.any { javaClass.name == it || javaClass.name.startsWith("$it$") }
    }

    @Test
    fun `only clients depend on third party models`() {
        noClasses()
            .that(DescribedPredicate.not(allowedToKnowThirdPartyModels))
            .should().dependOnClassesThat().resideInAPackage("com.kos.clients.domain..")
            .check(productionClasses)
    }
}
