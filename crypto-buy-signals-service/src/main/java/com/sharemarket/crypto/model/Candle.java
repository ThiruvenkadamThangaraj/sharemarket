package com.sharemarket.crypto.model;

public record Candle(
    String symbol,
    long timestamp,
    double open,
    double high,
    double low,
    double close,
    double volume
) {
}
