package com.kos.entities

import com.kos.views.Game
import org.mockito.Mockito.mock
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class EntityResolverProviderTest {
    private val wowResolver = mock(EntityResolver::class.java)
    private val wowGuildResolver = mock(GuildResolver::class.java)
    private val wowHardcoreResolver = mock(EntityResolver::class.java)
    private val wowHardcoreGuildResolver = mock(GuildResolver::class.java)
    private val lolResolver = mock(EntityResolver::class.java)
    private val wowForeverResolver = mock(EntityResolver::class.java)
    private val wowForeverGuildResolver = mock(GuildResolver::class.java)
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
    fun `guilds are resolved by the guild resolver of their game`() {
        assertSame(wowGuildResolver, provider.guildResolverFor(Game.WOW))
        assertSame(wowHardcoreGuildResolver, provider.guildResolverFor(Game.WOW_HC))
        assertSame(wowForeverGuildResolver, provider.guildResolverFor(Game.WOW_FOREVER))
    }

    @Test
    fun `lol has no guild resolver`() {
        assertFailsWith<IllegalStateException> { provider.guildResolverFor(Game.LOL) }
    }

    @Test
    fun `characters are resolved by the entity resolver of their game`() {
        assertSame(wowResolver, provider.resolverFor(Game.WOW))
        assertSame(wowHardcoreResolver, provider.resolverFor(Game.WOW_HC))
        assertSame(lolResolver, provider.resolverFor(Game.LOL))
        assertSame(wowForeverResolver, provider.resolverFor(Game.WOW_FOREVER))
    }
}
