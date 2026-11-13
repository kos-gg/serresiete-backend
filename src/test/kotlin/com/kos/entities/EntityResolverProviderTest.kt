package com.kos.entities

import com.kos.views.Game
import com.kos.views.WowExtraArguments
import org.mockito.Mockito.mock
import kotlin.test.Test
import kotlin.test.assertSame

class EntityResolverProviderTest {
    private val wowResolver = mock(EntityResolver::class.java)
    private val wowGuildResolver = mock(EntityResolver::class.java)
    private val wowHardcoreResolver = mock(EntityResolver::class.java)
    private val wowHardcoreGuildResolver = mock(EntityResolver::class.java)
    private val lolResolver = mock(EntityResolver::class.java)
    private val wowForeverResolver = mock(EntityResolver::class.java)
    private val wowForeverGuildResolver = mock(EntityResolver::class.java)
    private val provider = EntityResolverProvider(
        wowResolver = wowResolver,
        wowGuildResolver = wowGuildResolver,
        wowHardcoreResolver = wowHardcoreResolver,
        wowHardcoreGuildResolver = wowHardcoreGuildResolver,
        lolResolver = lolResolver,
        wowForeverResolver = wowForeverResolver,
        wowForeverGuildResolver = wowForeverGuildResolver
    )

    @Test
    fun `guild views are resolved by the guild resolver of their game`() {
        assertSame(wowGuildResolver, provider.resolverFor(Game.WOW, WowExtraArguments(isGuild = true)))
        assertSame(wowHardcoreGuildResolver, provider.resolverFor(Game.WOW_HC, WowExtraArguments(isGuild = true)))
        assertSame(wowForeverGuildResolver, provider.resolverFor(Game.WOW_FOREVER, WowExtraArguments(isGuild = true)))
    }

    @Test
    fun `character views are resolved by the entity resolver of their game`() {
        assertSame(wowResolver, provider.resolverFor(Game.WOW, WowExtraArguments(isGuild = false)))
        assertSame(wowResolver, provider.resolverFor(Game.WOW, null))
        assertSame(wowHardcoreResolver, provider.resolverFor(Game.WOW_HC, WowExtraArguments(isGuild = false)))
        assertSame(wowHardcoreResolver, provider.resolverFor(Game.WOW_HC, null))
        assertSame(lolResolver, provider.resolverFor(Game.LOL, null))
        assertSame(wowForeverResolver, provider.resolverFor(Game.WOW_FOREVER, WowExtraArguments(isGuild = false)))
        assertSame(wowForeverResolver, provider.resolverFor(Game.WOW_FOREVER, null))
    }
}
