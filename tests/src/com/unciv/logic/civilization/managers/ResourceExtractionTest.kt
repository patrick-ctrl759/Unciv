package com.unciv.logic.civilization.managers

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.tile.Tile
import com.unciv.logic.trade.Trade
import com.unciv.logic.trade.TradeEvaluation
import com.unciv.logic.trade.TradeLogic
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeOfferType
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class ResourceExtractionTest {
    private val testGame = TestGame()
    private lateinit var civ: Civilization

    @Before
    fun setUp() {
        testGame.makeHexagonalMap(4)
        civ = testGame.addCiv()
        testGame.addCity(civ, testGame.getTile(HexCoord.Zero))
    }

    @Test
    fun everyResourceTypeIsStockpiled() {
        for (resourceType in ResourceType.entries) {
            val resource = testGame.createResource().apply { this.resourceType = resourceType }
            assertTrue(resource.isStockpiled)
            civ.gainStockpiledResource(resource, 2)
            assertEquals(2, civ.getStockpiledResourcesForTrade()
                .first { it.resource == resource }.amount)
        }
    }

    @Test
    fun improvedResourceStartsWithFiniteReserveAndExtractsTwoUnits() {
        val farm = testGame.ruleset.tileImprovements["Farm"]!!
        farm.techRequired?.let { civ.tech.techsResearched.add(it) }
        val resource = testGame.createResource().apply {
            improvement = farm.name
            setTransients(testGame.ruleset)
        }
        val tile = testGame.getTile(HexCoord.Zero)
        tile.setTileResource(resource)

        assertEquals(Tile.InitialResourceReserve, tile.resourceReserve)
        TurnManager(civ).startTurn()

        assertEquals(2, civ.getResourceAmount(resource))
        assertEquals(Tile.InitialResourceReserve - 2, tile.resourceReserve)
    }

    @Test
    fun finiteReserveRemovesDepletedResourceAndImprovement() {
        val farm = testGame.ruleset.tileImprovements["Farm"]!!
        farm.techRequired?.let { civ.tech.techsResearched.add(it) }
        val resource = testGame.createResource().apply {
            improvement = farm.name
            setTransients(testGame.ruleset)
        }
        val tile = testGame.getTile(HexCoord.Zero)
        tile.setTileResource(resource)
        tile.resourceReserve = 1

        TurnManager(civ).startTurn()

        assertEquals(1, civ.getResourceAmount(resource))
        assertEquals(0, tile.resourceReserve)
        assertNull(tile.tileResource)
        assertNull(tile.improvement)
    }

    @Test
    fun recurringResourceTradeTransfersCollectedStockAndRequiresGoldTribute() {
        val importer = testGame.addCiv()
        testGame.addCity(importer, testGame.getTile(HexCoord(1, 0)))
        civ.diplomacyFunctions.makeCivilizationsMeet(importer)

        val farm = testGame.ruleset.tileImprovements["Farm"]!!
        farm.techRequired?.let { civ.tech.techsResearched.add(it) }
        val resource = testGame.createResource().apply {
            resourceType = ResourceType.Bonus
            improvement = farm.name
            setTransients(testGame.ruleset)
        }
        val tile = testGame.getTile(HexCoord.Zero)
        tile.setTileResource(resource)
        civ.cache.updateCivResources()
        importer.stats.statsForNextTurn.gold = 10f

        val tribute = TradeEvaluation().getResourceTributePerTurn(resource, 1, civ)
        val trade = Trade()
        trade.ourOffers.add(TradeOffer(
            resource.name,
            TradeOfferType.Stockpiled_Resource_Per_Turn,
            1,
            testGame.gameInfo.speed
        ))
        trade.theirOffers.add(TradeOffer(
            com.unciv.Constants.goldPerTurn,
            TradeOfferType.Gold_Per_Turn,
            tribute,
            testGame.gameInfo.speed
        ))
        assertTrue(TradeEvaluation().isTradeValid(trade, civ, importer))

        val tradeLogic = TradeLogic(civ, importer)
        tradeLogic.currentTrade.set(trade)
        tradeLogic.acceptTrade(applyGifts = false)

        TurnManager(civ).startTurn()

        assertEquals(1, civ.getResourceAmount(resource))
        assertEquals(1, importer.getResourceAmount(resource))
        assertEquals(2, tile.resourceReserve.let { Tile.InitialResourceReserve - it })
        assertEquals(-tribute, importer.getDiplomacyManager(civ)!!.goldPerTurn())

        trade.theirOffers.clear()
        assertTrue(!TradeEvaluation().isTradeValid(trade, civ, importer))
    }

    @Test
    fun recurringResourceTributeScalesByResourceType() {
        val evaluation = TradeEvaluation()
        val resource = testGame.createResource()

        resource.resourceType = ResourceType.Bonus
        assertEquals(1, evaluation.getResourceTributePerTurn(resource, 1, civ))
        resource.resourceType = ResourceType.Strategic
        assertEquals(3, evaluation.getResourceTributePerTurn(resource, 1, civ))
        resource.resourceType = ResourceType.Luxury
        assertEquals(4, evaluation.getResourceTributePerTurn(resource, 1, civ))
    }
}
