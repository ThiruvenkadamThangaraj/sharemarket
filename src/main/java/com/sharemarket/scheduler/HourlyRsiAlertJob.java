package com.sharemarket.scheduler;

import com.sharemarket.config.MarketConfig;
import com.sharemarket.model.OHLCData;
import com.sharemarket.service.IndicatorService;
import com.sharemarket.service.PriceDataService;
import com.sharemarket.service.RsiAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Runs at minute :01 of every hour, fetches 1-hour candles from Yahoo Finance
 * (free, no API key), computes RSI-14, and fires email alerts when:
 *
 *   RSI >= alert.rsi.overbought  (default 80) — potential reversal / sell zone
 *   RSI <= alert.rsi.oversold    (default 30) — potential bounce  / buy zone
 *
 * A per-symbol cooldown (default 4 h) prevents inbox flooding when RSI stays
 * pinned at an extreme for several consecutive hours.
 *
 * Symbols to watch are configured in application.properties:
 *   alert.symbols=BTC-USD,ETH-USD,AAPL,NVDA,TSLA,SPY
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HourlyRsiAlertJob {

    private final PriceDataService  priceDataService;
    private final IndicatorService  indicatorService;
    private final RsiAlertService   rsiAlertService;
    private final MarketConfig      marketConfig;

    /** Symbols dedicated to hourly alerts — separate from the daily report list. */
    @Value("${alert.symbols:BTC-USD,ETH-USD,AAPL,NVDA,TSLA,SPY}")
    private String alertSymbols;

    @Value("${alert.rsi.overbought:80}")
    private double overboughtThreshold;

    @Value("${alert.rsi.oversold:30}")
    private double oversoldThreshold;

    // ── 1-hour candle fetch settings (RSI) ───────────────────────────────────
    // "5d" gives ~120 hourly bars — plenty for RSI-14 (needs 14 + 9 = 23 minimum)
    private static final String INTERVAL_1H = "1h";
    private static final String RANGE_5D    = "5d";

    // ── 4-hour candle fetch settings (Support / Resistance) ───────────────────
    // "3mo" gives ~540 4h bars — enough for a reliable swing high/low lookback
    private static final String INTERVAL_4H = "4h";
    private static final String RANGE_3MO   = "3mo";

    // ── Daily candle fetch settings (Traditional Pivot Points) ───────────────
    // "10d" gives ~10 daily bars — only need the previous completed session
    private static final String INTERVAL_1D  = "1d";
    private static final String RANGE_10D    = "10d";

    // ── Scheduled entry point ─────────────────────────────────────────────────

    @Scheduled(cron = "${alert.scheduler.cron:0 1 * * * *}")
    public void runHourlyRsiCheck() {
        log.info("────────────────────────────────────────");
        log.info("  Hourly RSI Alert Check — STARTED");
        log.info("────────────────────────────────────────");

        List<String> symbols = Arrays.stream(alertSymbols.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .toList();

        int rsiPeriod = marketConfig.getRsi().getPeriod();
        int maPeriod  = marketConfig.getRsi().getMaPeriod();

        for (String symbol : symbols) {
            try {
                List<OHLCData> bars = priceDataService.fetchOHLC(symbol, INTERVAL_1H, RANGE_5D);

                if (bars.isEmpty()) {
                    log.warn("No data returned for {} — skipping alert check.", symbol);
                    continue;
                }

                IndicatorService.RSIResult result =
                    indicatorService.calculateRSI(bars, rsiPeriod, maPeriod);

                if (!result.enoughData()) {
                    log.warn("Not enough bars for {} to compute RSI reliably — skipping.", symbol);
                    continue;
                }

                double currentPrice = bars.get(bars.size() - 1).getClose();

                // Fetch 4h candles separately for support/resistance
                List<OHLCData> bars4h = priceDataService.fetchOHLC(symbol, INTERVAL_4H, RANGE_3MO);
                List<OHLCData> srBars = bars4h.isEmpty() ? bars : bars4h;

                double[] sr          = indicatorService.calculateSupportResistance(
                    srBars, marketConfig.getSupportResistanceLookback());
                double support       = sr[0];
                double resistance    = sr[1];

                // Fetch daily bars for Traditional Pivot Points (P, R1-R5, S1-S5)
                List<OHLCData> dailyBars = priceDataService.fetchOHLC(symbol, INTERVAL_1D, RANGE_10D);
                IndicatorService.PivotPoints pivots =
                    indicatorService.calculatePivotPoints(dailyBars);

                log.info("{} | 1h RSI={} | Price={} | 4h Support={} | 4h Resistance={} | Pivot R1={} S4={}",
                    symbol,
                    String.format("%.2f", result.rsi()),
                    String.format("%.4f", currentPrice),
                    String.format("%.4f", support),
                    String.format("%.4f", resistance),
                    pivots != null ? String.format("%.4f", pivots.r1()) : "N/A",
                    pivots != null ? String.format("%.4f", pivots.s4()) : "N/A");

                rsiAlertService.evaluateAndAlert(
                    symbol, result.rsi(), overboughtThreshold, oversoldThreshold,
                    currentPrice, support, resistance, pivots);

                // Small pause to respect Yahoo Finance rate limits between the extra fetch
                Thread.sleep(300);

                // Small pause to respect Yahoo Finance rate limits
                Thread.sleep(700);

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Hourly RSI check interrupted.");
                break;
            } catch (Exception e) {
                log.error("Error checking RSI for {}: {}", symbol, e.getMessage(), e);
            }
        }

        log.info("────────────────────────────────────────");
        log.info("  Hourly RSI Alert Check — DONE");
        log.info("────────────────────────────────────────");
    }
}
