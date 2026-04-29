package com.sharemarket.crypto.service;

import com.sharemarket.crypto.model.Decision;
import com.sharemarket.crypto.model.Strength;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DecisionEngineTest {

    @Test
    void shouldReturnVeryStrongBuyWhenAllSignalsMetAndThreeMaHits() {
        DecisionEngine.DecisionResult result = DecisionEngine.evaluate(true, true, 3, 35.0, 70.0);

        assertEquals(Decision.BUY, result.decision());
        assertEquals(Strength.VERY_STRONG, result.strength());
    }

    @Test
    void shouldReturnBasicBuyWhenAllSignalsMetAndOneMaHit() {
        DecisionEngine.DecisionResult result = DecisionEngine.evaluate(true, true, 1, 39.0, 70.0);

        assertEquals(Decision.BUY, result.decision());
        assertEquals(Strength.BASIC, result.strength());
    }

    @Test
    void shouldReturnSellWatchWhenRsiOverboughtAndNoMaHit() {
        DecisionEngine.DecisionResult result = DecisionEngine.evaluate(false, false, 0, 75.0, 70.0);

        assertEquals(Decision.SELL_WATCH, result.decision());
        assertEquals(Strength.NONE, result.strength());
    }

    @Test
    void shouldReturnWaitWhenBuyConditionsNotMet() {
        DecisionEngine.DecisionResult result = DecisionEngine.evaluate(true, false, 2, 45.0, 70.0);

        assertEquals(Decision.WAIT, result.decision());
        assertEquals(Strength.NONE, result.strength());
    }
}
