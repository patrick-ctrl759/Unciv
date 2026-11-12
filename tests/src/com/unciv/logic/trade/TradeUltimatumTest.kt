package com.unciv.logic.trade

import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class TradeUltimatumTest {

    private val testGame = TestGame()
    private val attacker = testGame.addCiv()
    private val target = testGame.addCiv()

    init {
        testGame.addUnit("Warrior", attacker, null)
        testGame.addUnit("Warrior", target, null)
        attacker.diplomacyFunctions.makeCivilizationsMeet(target)
    }

    @Test
    fun `declining an ultimatum declares war immediately`() {
        val request = TradeRequest(attacker.civID, Trade().apply { isUltimatum = true })

        request.decline(target)

        assertTrue(attacker.isAtWarWith(target))
    }

    @Test
    fun `accepting an ultimatum marks the active deal`() {
        val tradeLogic = TradeLogic(attacker, target)
        tradeLogic.currentTrade.isUltimatum = true

        tradeLogic.acceptTrade()

        assertFalse(attacker.isAtWarWith(target))
        assertTrue(attacker.getDiplomacyManager(target)!!.trades.any { it.isUltimatum })
        assertTrue(target.getDiplomacyManager(attacker)!!.trades.any { it.isUltimatum })
    }
}
