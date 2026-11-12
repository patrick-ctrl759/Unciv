package com.unciv.logic.automation.civilization

import com.unciv.Constants
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.trade.Trade
import com.unciv.logic.trade.TradeEvaluation
import com.unciv.logic.trade.TradeLogic
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeOfferType
import com.unciv.logic.trade.TradeRequest
import com.unciv.ui.screens.victoryscreen.RankingType
import com.unciv.view.CivView

object DeclareWarTargetAutomation {

    /**
     * Chooses a target civilization along with a plan of attack.
     * Note that this doesn't guarantee that we will declare war on them immediately, or that we will end up declaring war at all.
     */
    fun chooseDeclareWarTarget(civView: CivView, civAttackMotivations: List<Pair<Civilization, Float>>) {
        val highestValueTargets = civAttackMotivations.sortedByDescending { it.first.getStatForRanking(RankingType.Score) }

        for (target in highestValueTargets) {
            if (tryDeclareWarWithPlan(civView, target.first, target.second))
                return // We have successfully found a plan and started executing it!
        }
    }

    /**
     * Determines a war plan against this [target] and executes it if able.
     */
    private fun tryDeclareWarWithPlan(civView: CivView, target: Civilization, motivation: Float): Boolean {

        if (!target.isCityState) {
            if (motivation > 5 && tryTeamWar(civView, target, motivation)) return true

            if (motivation >= 15 && tryJoinWar(civView, target, motivation)) return true
        }

        if (motivation >= 20 && declareWar(civView, target, motivation)) return true

        if (motivation >= 15 && prepareWar(civView, target, motivation)) return true

        return false
    }

    /**
     * The safest option for war is to invite a new ally to join the war with us.
     * Together we are stronger and are more likely to take down bigger threats.
     */
    private fun tryTeamWar(civView: CivView, target: Civilization, motivation: Float): Boolean {
        val civInfo = civView.getCiv()
        val potentialAllies = civInfo.getDiplomacyManager(target)!!.getCommonKnownCivs()
            .filter {
                it.isMajorCiv
                    && !civInfo.getDiplomacyManager(it)!!.hasFlag(DiplomacyFlags.DeclinedJoinWarOffer)
                    && civInfo.getDiplomacyManager(it)!!.isRelationshipLevelGE(RelationshipLevel.Neutral)
                    && !it.isAtWarWith(target)
            }.sortedByDescending { it.getStatForRanking(RankingType.Force) }

        for (thirdCiv in potentialAllies) {
            if (DeclareWarPlanEvaluator.evaluateTeamWarPlan(civInfo, target, thirdCiv, motivation) <= 0) continue

            // Send them an offer
            val tradeLogic = TradeLogic(civInfo, thirdCiv)
            tradeLogic.currentTrade.ourOffers.add(TradeOffer(target.civID, TradeOfferType.WarDeclaration, speed = civInfo.gameInfo.speed))
            tradeLogic.currentTrade.theirOffers.add(TradeOffer(target.civID, TradeOfferType.WarDeclaration, speed = civInfo.gameInfo.speed))

            thirdCiv.tradeRequests.add(TradeRequest(civInfo.civID, tradeLogic.currentTrade.reverse()))

            return true
        }

        return false
    }

    /**
     * The next safest aproach is to join an existing war on the side of an ally that is already at war with [target].
     */
    private fun tryJoinWar(civView: CivView, target: Civilization, motivation: Float): Boolean {
        val civInfo = civView.getCiv()
        val potentialAllies = civInfo.getDiplomacyManager(target)!!.getCommonKnownCivs()
            .filter {
                it.isMajorCiv
                    && !civInfo.getDiplomacyManager(it)!!.hasFlag(DiplomacyFlags.DeclinedJoinWarOffer)
                    && civInfo.getDiplomacyManager(it)!!.isRelationshipLevelGE(RelationshipLevel.Favorable)
                    && it.isAtWarWith(target)
            } // Must be a civ not already at war with them
            .sortedByDescending { it.getStatForRanking(RankingType.Force) }

        for (thirdCiv in potentialAllies) {
            if (DeclareWarPlanEvaluator.evaluateJoinWarPlan(civInfo, target, thirdCiv, motivation) <= 0) continue

            // Send them an offer
            val tradeLogic = TradeLogic(civInfo, thirdCiv)
            tradeLogic.currentTrade.ourOffers.add(TradeOffer(target.civID, TradeOfferType.WarDeclaration, speed = civInfo.gameInfo.speed))
            // TODO: Maybe add in payment requests in some situations
            thirdCiv.tradeRequests.add(TradeRequest(civInfo.civID, tradeLogic.currentTrade.reverse()))

            return true
        }

        return false
    }

    /**
     * Lastly, if our motivation is high enough and we don't have any better plans then lets just declare war.
     */
    private fun declareWar(civView: CivView, target: Civilization, motivation: Float): Boolean {
        val civInfo = civView.getCiv()
        if (DeclareWarPlanEvaluator.evaluateDeclareWarPlan(civInfo, target, motivation) > 0) {
            if (issueUltimatum(civInfo, target)) return true
            civInfo.getDiplomacyManager(target)!!.declareWar()
            return true
        }
        return false
    }

    private fun issueUltimatum(civInfo: Civilization, target: Civilization): Boolean {
        if (!target.isMajorCiv || civInfo.isAtWarWith(target)) return false
        val diplomacyManager = civInfo.getDiplomacyManager(target) ?: return false
        if (diplomacyManager.trades.any { it.isUltimatum }
            || target.tradeRequests.any { it.requestingCiv == civInfo.civID }) return true

        val tradeLogic = TradeLogic(civInfo, target)
        val ultimatum = Trade().apply { isUltimatum = true }
        val targetGoldPerTurn = target.stats.statsForNextTurn.gold.toInt()
        if (targetGoldPerTurn > 0) {
            val amount = (targetGoldPerTurn / 5).coerceIn(1, 5)
            ultimatum.theirOffers.add(
                TradeOffer(Constants.goldPerTurn, TradeOfferType.Gold_Per_Turn, amount, civInfo.gameInfo.speed)
            )
        } else {
            val resourceOffer = tradeLogic.theirAvailableOffers.firstOrNull {
                it.type == TradeOfferType.Stockpiled_Resource_Per_Turn && it.amount > 0
            }
            if (resourceOffer != null) {
                ultimatum.theirOffers.add(resourceOffer.copy(amount = 1))
                val resource = target.gameInfo.ruleset.tileResources[resourceOffer.name] ?: return false
                val tribute = TradeEvaluation().getResourceTributePerTurn(resource, 1, target)
                ultimatum.ourOffers.add(
                    TradeOffer(Constants.goldPerTurn, TradeOfferType.Gold_Per_Turn, tribute, civInfo.gameInfo.speed)
                )
            } else {
                val city = target.cities.firstOrNull { !it.isCapital() && !it.isInResistance() } ?: return false
                ultimatum.theirOffers.add(TradeOffer(city.id, TradeOfferType.City, speed = civInfo.gameInfo.speed))
            }
        }

        target.tradeRequests.add(TradeRequest(civInfo.civID, ultimatum.reverse()))
        return true
    }

    /**
     * Slightly safter is to silently plan an invasion and declare war later.
     */
    private fun prepareWar(civView: CivView, target: Civilization, motivation: Float): Boolean {
        // TODO: We use negative values in WaryOf for now so that we aren't adding any extra fields to the save file
        // This will very likely change in the future and we will want to build upon it
        val diploManager = civView.getCiv().getDiplomacyManager(target)!!
        if (DeclareWarPlanEvaluator.evaluateStartPreparingWarPlan(civView.getCiv(), target, motivation) > 0) {
            diploManager.setFlag(DiplomacyFlags.WaryOf, -1)
            return true
        }
        return false
    }

}

