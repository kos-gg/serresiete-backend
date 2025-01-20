package com.kos.views

object ViewsTestHelper {

    val id = "1"
    val name = "name"
    val owner = "owner"
    val published = true
    val featured = false
    val basicSimpleWowView = SimpleView(id, name, owner, published, listOf(), Game.WOW, featured)
    val basicSimpleWowHardcoreView = SimpleView(id, name, owner, published, listOf(), Game.WOW_HC, featured)
    val basicSimpleLolView = SimpleView(id, name, owner, published, listOf(), Game.LOL, featured)
    val basicSimpleLolViews = listOf(
        basicSimpleLolView,
        basicSimpleLolView.copy(id = "2")
    )
    val basicSimpleGameViews = listOf(
        basicSimpleLolView,
        basicSimpleLolView.copy(id = "2"),
        basicSimpleWowView.copy(id = "3", featured = true)
    )
    val gigaSimpleGameViews =
        basicSimpleGameViews +
                listOf(
                    basicSimpleLolView.copy(id = "4", featured = false),
                    basicSimpleLolView.copy(id = "5", featured = true),
                    basicSimpleWowView.copy(id = "6", featured = true),
                    basicSimpleWowView.copy(id = "7", featured = false),
                    basicSimpleWowView.copy(id = "8", game = Game.WOW_HC, featured = false),
                    basicSimpleWowView.copy(id = "9", game = Game.WOW_HC, featured = true)
                )
}

fun View.toSimple() =
    SimpleView(this.id, this.name, this.owner, this.published, this.entities.map { it.id }, this.game, this.featured)