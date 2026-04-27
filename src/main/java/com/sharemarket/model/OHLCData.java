package com.sharemarket.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One OHLCV daily bar for a symbol, as returned by the price-data API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OHLCData {

    /** Yahoo Finance ticker, e.g. "BTC-USD" or "AAPL" */
    private String symbol;

    /** Unix epoch seconds for the bar's close time */
    private long timestamp;

    private double open;
    private double high;
    private double low;
    private double close;
    private double volume;
}
