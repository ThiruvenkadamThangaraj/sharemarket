package com.sharemarket.crypto.service;

import com.sharemarket.crypto.model.Decision;
import com.sharemarket.crypto.model.Strength;

public final class DecisionEngine {

    private DecisionEngine() {
    }

    public record DecisionResult(Decision decision, Strength strength, String reason) {
    }

    public static DecisionResult evaluate(
        boolean athDiscountMet,
        boolean rsiMet,
        int movingAverageHits,
        double rsi,
        double sellThreshold
    ) {
        if (athDiscountMet && rsiMet && movingAverageHits > 0) {
            Strength strength = switch (movingAverageHits) {
                case 1 -> Strength.BASIC;
                case 2 -> Strength.STRONG;
                default -> Strength.VERY_STRONG;
            };
            return new DecisionResult(Decision.BUY, strength,
                "All buy conditions met (ATH discount + RSI + MA alignment)");
        }

        if (rsi >= sellThreshold && movingAverageHits == 0) {
            return new DecisionResult(Decision.SELL_WATCH, Strength.NONE,
                "RSI is overbought and price is above MA 50/100/200");
        }

        return new DecisionResult(Decision.WAIT, Strength.NONE,
            "One or more buy conditions are not met yet");
    }
}
