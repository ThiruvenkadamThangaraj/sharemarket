package com.sharemarket.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The final computed signal for one symbol — written as a row in the Excel report.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketSignal {

    /** Ticker symbol, e.g. "ETH-USD" or "NVDA" */
    private String symbol;

    /** "CRYPTO" or "STOCK" */
    private String type;

    /** Sector display name, e.g. "Big Tech", "Semiconductors", "Crypto" */
    private String sector;

    /** Latest closing price */
    private double currentPrice;

    /** RSI-14 value  (the "blue line") */
    private double rsi;

    /** SMA-9 of RSI  (the "yellow line") */
    private double rsiMA;

    /** Lowest low over the support/resistance lookback window */
    private double support;

    /** Highest high over the support/resistance lookback window */
    private double resistance;

    /** One of: BUY CONFIRM / BUY / WAIT / SELL / OVERBOUGHT / OVERSOLD / ERROR */
    private String signal;

    /** Human-readable explanation of the signal */
    private String reason;
}
