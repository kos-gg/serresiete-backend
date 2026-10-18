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
        "com.kos.scripts.BlizzardStaticItemsConverterKt",
        "com.kos.scripts.StaticWowItem",
        "com.kos.sources.wowhc.staticdata.wowitems.WowItemsDatabaseRepository",
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
            .because(
                "provider models are an implementation detail of com.kos.clients: " +
                    "clients must return our own types, translated in clients/<provider>/<Provider>Mappers.kt"
            )
            .check(productionClasses)
    }
}
