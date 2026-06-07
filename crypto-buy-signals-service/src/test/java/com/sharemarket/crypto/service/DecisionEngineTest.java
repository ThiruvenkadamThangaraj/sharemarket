package com.sharemarket.crypto.service;

import com.sharemarket.crypto.model.Decision;
import com.sharemarket.crypto.model.Strength;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionEngineTest {

    @Test
    void shouldReturnVeryStrongBuyWhenAllSignalsMetAndThreeMaHits() {
        DecisionEngine.DecisionResult result = DecisionEngine.evaluate(true, true, 3, 35.0, 70.0, 28000.0, null);

        assertEquals(Decision.BUY, result.decision());
        assertEquals(Strength.VERY_STRONG, result.strength());
    }

    @Test
    void shouldReturnBasicBuyWhenAllSignalsMetAndOneMaHit() {
        DecisionEngine.DecisionResult result = DecisionEngine.evaluate(true, true, 1, 39.0, 70.0, 28000.0, null);

        assertEquals(Decision.BUY, result.decision());
        assertEquals(Strength.BASIC, result.strength());
    }

    @Test
    void shouldReturnSellWhenAllThreeRulesMetWithTarget() {
        // BTC: bought at $30k, target $45k, current $46k, RSI 72, price above all MAs
        DecisionEngine.DecisionResult result = DecisionEngine.evaluate(false, false, 0, 72.0, 70.0, 46000.0, 45000.0);

        assertEquals(Decision.SELL, result.decision());
        assertEquals(Strength.NONE, result.strength());
        assertTrue(result.reason().contains("target"));
    }

    @Test
    void shouldReturnSellWatchWhenRsiAndMasMetButTargetNotYetReached() {
        // RSI overbought, above all MAs, but price hasn't reached $45k yet
        DecisionEngine.DecisionResult result = DecisionEngine.evaluate(false, false, 0, 71.0, 70.0, 42000.0, 45000.0);

        assertEquals(Decision.SELL_WATCH, result.decision());
        assertTrue(result.reason().contains("45000"));
    }

    @Test
    void shouldReturnSellWatchWhenRsiOverboughtAndNoTargetConfigured() {
        DecisionEngine.DecisionResult result = DecisionEngine.evaluate(false, false, 0, 75.0, 70.0, 46000.0, null);

        assertEquals(Decision.SELL_WATCH, result.decision());
        assertEquals(Strength.NONE, result.strength());
    }

    @Test
    void shouldReturnWaitWhenPriceIsNotAboveAllMAsEvenWithHighRsi() {
        // RSI overbought but price still below one MA (maHits = 1) → not a sell signal
        DecisionEngine.DecisionResult result = DecisionEngine.evaluate(false, false, 1, 75.0, 70.0, 30000.0, 45000.0);

        assertEquals(Decision.WAIT, result.decision());
    }

    @Test
    void shouldReturnWaitWhenBuyConditionsNotMet() {
        DecisionEngine.DecisionResult result = DecisionEngine.evaluate(true, false, 2, 45.0, 70.0, 28000.0, null);

        assertEquals(Decision.WAIT, result.decision());
        assertEquals(Strength.NONE, result.strength());
    }
}
