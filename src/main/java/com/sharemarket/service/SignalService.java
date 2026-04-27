package com.sharemarket.service;

import com.sharemarket.config.MarketConfig;
import com.sharemarket.model.MarketSignal;
import com.sharemarket.model.OHLCData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Applies the BUY / WAIT / SELL decision logic for a single symbol.
 *
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │  Signal Decision Tree                                                    │
 * │                                                                          │
 * │  1. RSI < 30                  → OVERSOLD  (strong buy zone)             │
 * │  2. RSI > 70                  → OVERBOUGHT (strong sell zone)           │
 * │  3. RSI blue > RSI yellow AND RSI in [30,40]                            │
 * │       + price near support    → BUY CONFIRM  (highest conviction)       │
 * │       otherwise               → BULLISH START                           │
 * │  4. RSI blue > RSI yellow but RSI outside [30,40]                       │
 * │       otherwise               → WAIT                                    │
 * │  5. RSI blue < RSI yellow                                                │
 * │       + price near support    → BUY CONFIRM  (highest conviction)       │
 * │       + price near resistance → SELL                                    │
 * │       otherwise               → WAIT                                    │
 * └─────────────────────────────────────────────────────────────────────────┘
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignalService {

    private final MarketConfig      config;
    private final PriceDataService  priceDataService;
    private final IndicatorService  indicatorService;

    /**
     * Fetches data, computes indicators, and produces a {@link MarketSignal}.
     *
     * @param symbol ticker, e.g. "ETH-USD" or "NVDA"
     * @param type   "CRYPTO" or "STOCK"
     * @param sector display name, e.g. "Big Tech", "Crypto"
     */
    public MarketSignal analyzeSymbol(String symbol, String type, String sector) {

        // ── 1. Fetch price history ─────────────────────────────────────────
        List<OHLCData> bars = priceDataService.fetchOHLC(
            symbol,
            config.getDataInterval(),
            config.getDataRange());

        if (bars.isEmpty()) {
            return MarketSignal.builder()
                .symbol(symbol).type(type).sector(sector)
                .signal("ERROR")
                .reason("Failed to fetch price data from Yahoo Finance")
                .build();
        }

        // ── 2. Current price = latest close ───────────────────────────────
        double currentPrice = bars.get(bars.size() - 1).getClose();

        // ── 3. RSI + RSI-MA ───────────────────────────────────────────────
        IndicatorService.RSIResult rsiResult = indicatorService.calculateRSI(
            bars,
            config.getRsi().getPeriod(),
            config.getRsi().getMaPeriod());

        // ── 4. Support / Resistance ───────────────────────────────────────
        double[] sr         = indicatorService.calculateSupportResistance(
            bars, config.getSupportResistanceLookback());
        double support      = sr[0];
        double resistance   = sr[1];

        double threshold    = config.getRangeThresholdPercent() / 100.0;
        boolean nearSupport     = currentPrice <= support    * (1.0 + threshold);
        boolean nearResistance  = currentPrice >= resistance * (1.0 - threshold);

        // ── 5. Signal logic ───────────────────────────────────────────────
        boolean blueAboveYellow   = rsiResult.rsi() > rsiResult.rsiMA();  // bullish
        boolean criticalOversold   = rsiResult.rsi() < config.getRsi().getCriticalOversoldLevel();
        boolean extremeOversold    = rsiResult.rsi() < config.getRsi().getExtremeOversoldLevel();
        boolean oversold           = rsiResult.rsi() < 30;
        boolean criticalOverbought = rsiResult.rsi() > config.getRsi().getCriticalOverboughtLevel();
        boolean extremeOverbought  = rsiResult.rsi() > config.getRsi().getExtremeOverboughtLevel();
        boolean overbought         = rsiResult.rsi() > config.getRsi().getOverboughtLevel();
        boolean bullishRange    = rsiResult.rsi() >= config.getRsi().getBullishRangeMin()
            && rsiResult.rsi() <= config.getRsi().getBullishRangeMax();

        String signal;
        String reason;

        if (!rsiResult.enoughData()) {
            signal = "WAIT";
            reason = "Insufficient historical data for reliable indicator values";

        } else if (criticalOversold) {
            signal = "CRITICAL OS";
            reason = String.format(
                "RSI %.1f < %.0f — critically oversold; very high bounce/reversal potential, watch for entry",
                rsiResult.rsi(), config.getRsi().getCriticalOversoldLevel());

        } else if (extremeOversold) {
            signal = "EXTREME OS";
            reason = String.format(
                "RSI %.1f < %.0f — extremely oversold; strong potential for reversal, watch closely",
                rsiResult.rsi(), config.getRsi().getExtremeOversoldLevel());

        } else if (oversold) {
            signal = "OVERSOLD";
            reason = String.format(
                "RSI %.1f < 30 — price may be deeply oversold; watch for reversal",
                rsiResult.rsi());

        } else if (criticalOverbought) {
            signal = "CRITICAL OB";
            reason = String.format(
                "RSI %.1f > %.0f — critically overbought; very high reversal risk, consider exit or short",
                rsiResult.rsi(), config.getRsi().getCriticalOverboughtLevel());

        } else if (extremeOverbought) {
            signal = "EXTREME OB";
            reason = String.format(
                "RSI %.1f > %.0f — strongly overbought; high probability of short-term reversal or pullback",
                rsiResult.rsi(), config.getRsi().getExtremeOverboughtLevel());

        } else if (overbought) {
            signal = "OVERBOUGHT";
            reason = String.format(
                "RSI %.1f > %.0f — price may be overextended; watch for pullback",
                rsiResult.rsi(), config.getRsi().getOverboughtLevel());

        } else if (blueAboveYellow && bullishRange && nearSupport) {
            signal = "BUY CONFIRM";
            reason = String.format(
                "Bullish phase confirmed: RSI blue (%.1f) > yellow (%.1f), RSI in [%.1f, %.1f], "
                    + "and price %.2f is near support %.2f",
                rsiResult.rsi(), rsiResult.rsiMA(),
                config.getRsi().getBullishRangeMin(),
                config.getRsi().getBullishRangeMax(),
                currentPrice, support);

        } else if (blueAboveYellow && bullishRange) {
            signal = "BULLISH START";
            reason = String.format(
                "RSI blue (%.1f) > yellow (%.1f) and RSI is in bullish range [%.1f, %.1f] — "
                    + "early bullish phase",
                rsiResult.rsi(), rsiResult.rsiMA(),
                config.getRsi().getBullishRangeMin(),
                config.getRsi().getBullishRangeMax());

        } else if (blueAboveYellow && nearSupport) {
            signal = "WAIT";
            reason = String.format(
                "RSI blue (%.1f) is above yellow (%.1f) and near support %.2f, but RSI is outside "
                    + "bullish range [%.1f, %.1f]",
                rsiResult.rsi(), rsiResult.rsiMA(), support,
                config.getRsi().getBullishRangeMin(),
                config.getRsi().getBullishRangeMax());

        } else if (blueAboveYellow) {
            signal = "WAIT";
            reason = String.format(
                "RSI blue (%.1f) above yellow (%.1f), but RSI is outside bullish range [%.1f, %.1f]",
                rsiResult.rsi(), rsiResult.rsiMA(),
                config.getRsi().getBullishRangeMin(),
                config.getRsi().getBullishRangeMax());

        } else if (!blueAboveYellow && nearResistance) {
            signal = "SELL";
            reason = String.format(
                "RSI blue (%.1f) below yellow (%.1f) AND price %.2f is near resistance %.2f — "
                    + "bearish RSI crossover confirmed at resistance",
                rsiResult.rsi(), rsiResult.rsiMA(), currentPrice, resistance);

        } else {
            signal = "WAIT";
            reason = String.format(
                "RSI blue (%.1f) below yellow (%.1f) — no bullish confirmation yet",
                rsiResult.rsi(), rsiResult.rsiMA());
        }

        log.info("[{}] {} | Price={} | RSI={} | RSI-MA={} | → {}",
            type, symbol,
            String.format("%.2f", currentPrice),
            String.format("%.1f", rsiResult.rsi()),
            String.format("%.1f", rsiResult.rsiMA()),
            signal);

        return MarketSignal.builder()
            .symbol(symbol)
            .type(type)
            .sector(sector)
            .currentPrice(currentPrice)
            .rsi(rsiResult.rsi())
            .rsiMA(rsiResult.rsiMA())
            .support(support)
            .resistance(resistance)
            .signal(signal)
            .reason(reason)
            .build();
    }
}
