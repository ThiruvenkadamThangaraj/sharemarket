package com.sharemarket.crypto.model;

import java.util.Map;

public record CoinSignalResponse(
    String symbol,
    double currentPrice,
    double athPrice,
    double athDiscountThreshold,
    double rsi,
    Map<Integer, Double> movingAverages,
    int movingAverageHits,
    boolean signalAthDiscountMet,
    boolean signalRsiMet,
    boolean signalMovingAverageMet,
    String scenario,
    Decision decision,
    Strength strength,
    String reason
) {
}
