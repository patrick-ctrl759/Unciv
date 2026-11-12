package com.unciv.logic.map

import com.unciv.logic.files.MapSaver
import com.unciv.models.ruleset.RulesetCache
import com.unciv.testing.BaseTestRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(BaseTestRunner::class)
class BundledRealWorldMapTests {
    @Test
    fun mapLoadsWithTheExpectedTerrainAndDimensions() {
        val mapFile = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "android/assets/maps/Mundo_Real_5k") }
            .first { it.isFile }
        val savedMap = mapFile.readText()
        val tileMap = MapSaver.mapFromSavedString(savedMap)

        assertEquals(90, tileMap.mapParameters.mapSize.width)
        assertEquals(56, tileMap.mapParameters.mapSize.height)
        assertEquals("Grand Strategy (5k)", tileMap.mapParameters.mapSize.name)
        assertEquals(5_040, tileMap.tileList.size)

        RulesetCache.loadRulesets(noMods = true)
        tileMap.setTransients(RulesetCache.getVanillaRuleset())
        tileMap.assignContinents(TileMap.AssignContinentsMode.Ensure)

        assertTrue(tileMap.values.any { it.baseTerrain == "Ocean" })
        assertTrue(tileMap.values.any { it.baseTerrain == "Mountain" })
        assertTrue(tileMap.values.any { "Forest" in it.terrainFeatures })
        assertTrue(tileMap.values.any { "Jungle" in it.terrainFeatures })
        assertTrue(tileMap.continentSizes.isNotEmpty())
    }
}
