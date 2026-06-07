package com.sharemarket.crypto.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.Map;

public record AnalyzeRequest(
    @NotEmpty(message = "symbols must not be empty")
    List<@NotBlank(message = "symbol must not be blank") String> symbols,

    /**
     * Optional profit targets keyed by symbol (e.g. {"BTC-USD": 45000.0}).
     * When the current price reaches or exceeds the target and all MA + RSI
     * sell conditions are met, the decision is upgraded from SELL_WATCH to SELL.
     */
    Map<String, @Positive(message = "sell target must be a positive value") Double> sellTargets
) {
    public AnalyzeRequest {
        sellTargets = (sellTargets != null) ? sellTargets : Map.of();
    }
}
