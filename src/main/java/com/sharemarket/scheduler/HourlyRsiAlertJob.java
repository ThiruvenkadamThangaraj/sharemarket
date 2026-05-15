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

    // ── 1-hour candle fetch settings ──────────────────────────────────────────
    // "5d" gives ~120 hourly bars — plenty for RSI-14 (needs 14 + 9 = 23 minimum)
    private static final String INTERVAL_1H = "1h";
    private static final String RANGE_5D    = "5d";

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

                log.info("{} | 1h RSI={} | Price={}", symbol,
                    String.format("%.2f", result.rsi()),
                    String.format("%.4f", currentPrice));

                rsiAlertService.evaluateAndAlert(
                    symbol, result.rsi(), overboughtThreshold, oversoldThreshold, currentPrice);

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
