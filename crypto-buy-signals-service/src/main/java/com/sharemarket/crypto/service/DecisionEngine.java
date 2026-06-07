package com.sharemarket.crypto.service;

import com.sharemarket.crypto.model.Decision;
import com.sharemarket.crypto.model.Strength;

public final class DecisionEngine {

    private DecisionEngine() {
    }

    public record DecisionResult(Decision decision, Strength strength, String reason) {
    }

    /**
     * Evaluates the current market data against both buy and sell rule-sets.
     *
     * <p><b>Sell rules (from the 3-step strategy):</b>
     * <ol>
     *   <li>Rule 1 – Profit target: {@code currentPrice >= sellTarget} (SELL only; omit for SELL_WATCH)</li>
     *   <li>Rule 2 – All MAs below price: {@code movingAverageHits == 0}</li>
     *   <li>Rule 3 – RSI overbought: {@code rsi >= sellThreshold} (default 70)</li>
     * </ol>
     *
     * @param sellTarget null means no profit target is configured; a non-null value upgrades
     *                   a SELL_WATCH to a full SELL once the price reaches or exceeds it.
     */
    public static DecisionResult evaluate(
        boolean athDiscountMet,
        boolean rsiMet,
        int movingAverageHits,
        double rsi,
        double sellThreshold,
        double currentPrice,
        Double sellTarget
    ) {
        // ── BUY signal ───────────────────────────────────────────────────────
        if (athDiscountMet && rsiMet && movingAverageHits > 0) {
            Strength strength = switch (movingAverageHits) {
                case 1 -> Strength.BASIC;
                case 2 -> Strength.STRONG;
                default -> Strength.VERY_STRONG;
            };
            return new DecisionResult(Decision.BUY, strength,
                "All buy conditions met (ATH discount + RSI + MA alignment)");
        }

        // ── Sell signal (Rules 2 + 3 must both be satisfied) ─────────────────
        boolean aboveAllMAs  = movingAverageHits == 0;
        boolean rsiOverbought = rsi >= sellThreshold;

        if (aboveAllMAs && rsiOverbought) {
            // Rule 1 – profit target reached → confirmed SELL
            if (sellTarget != null && currentPrice >= sellTarget) {
                return new DecisionResult(Decision.SELL, Strength.NONE,
                    String.format(
                        "All 3 sell rules met: price %.4f reached target %.4f, above all MAs, RSI %.2f overbought",
                        currentPrice, sellTarget, rsi));
            }
            // Rule 1 – target set but not yet reached → watch
            if (sellTarget != null) {
                return new DecisionResult(Decision.SELL_WATCH, Strength.NONE,
                    String.format(
                        "MA + RSI sell rules met — watching for price %.4f to reach target %.4f",
                        currentPrice, sellTarget));
            }
            // No target configured → watch with guidance
            return new DecisionResult(Decision.SELL_WATCH, Strength.NONE,
                "RSI is overbought and price is above MA 50/100/200 — set a sell target (Rule 1) to confirm a SELL");
        }

        return new DecisionResult(Decision.WAIT, Strength.NONE,
            "One or more buy conditions are not met yet");
    }
}
