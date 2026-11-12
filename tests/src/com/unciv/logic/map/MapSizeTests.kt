package com.unciv.logic.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapSizeTests {
    @Test
    fun grandStrategySizeIsAvailableByDisplayName() {
        val size = MapSize("Grand Strategy (5k)")

        assertEquals(41, size.radius)
        assertEquals(90, size.width)
        assertEquals(56, size.height)
        assertEquals("Grand Strategy (5k)", size.name)
        assertTrue(MapSize.names().contains("Grand Strategy (5k)"))
    }
}
