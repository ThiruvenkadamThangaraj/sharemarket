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
 * Runs every four hours, fetches candles from Yahoo Finance
 * (free, no API key), computes RSI-14, and fires email alerts when:
 *
 *   RSI >= alert.rsi.overbought  (default 80) — potential reversal / sell zone
 *   RSI <= alert.rsi.oversold    (default 30) — potential bounce  / buy zone
 *
 * Crypto and stock schedules are separate: crypto runs around the clock, while
 * stocks run only during the US market session.
 *
 * Symbols to watch are configured in application.properties:
 *   crypto.alert.symbols=ETH-USD,BTCUSDT
 *   stock.alert.symbols=TSLA,NVDA,AAPL
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HourlyRsiAlertJob {

    private final PriceDataService  priceDataService;
    private final IndicatorService  indicatorService;
    private final RsiAlertService   rsiAlertService;
    private final MarketConfig      marketConfig;

    @Value("${crypto.alert.symbols:ETH-USD,BTCUSDT}")
    private String cryptoAlertSymbols;

    @Value("${stock.alert.symbols:TSLA,NVDA,IAU,AAPL,MSFT,AMZN,GOOGL,META,AVGO,JPM,BRK.B}")
    private String stockAlertSymbols;

    @Value("${alert.rsi.overbought:80}")
    private double overboughtThreshold;

    @Value("${alert.rsi.oversold:30}")
    private double oversoldThreshold;

    /** Symbols checked once a day on both the 4H and Daily chart. */
    @Value("${watchlist.symbols:BTCUSDT,ETH-USD,TSLA,NVDA,IAU,AAPL,MSFT,AMZN,GOOGL,META,AVGO,JPM,BRK.B}")
    private String watchlistSymbols;

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

    @Scheduled(cron = "${crypto.alert.scheduler.cron:0 1 0/4 * * *}", zone = "UTC")
    public void runCryptoRsiCheck() {
        runRsiCheck(cryptoAlertSymbols, "Crypto", INTERVAL_4H, RANGE_3MO);
    }

    @Scheduled(cron = "${stock.alert.scheduler.cron:0 1 10,14 * * MON-FRI}", zone = "America/New_York")
    public void runStockRsiCheck() {
        runRsiCheck(stockAlertSymbols, "Stock", INTERVAL_4H, RANGE_3MO);
    }

    private void runRsiCheck(String configuredSymbols, String marketType,
                             String chartInterval, String chartRange) {
        log.info("────────────────────────────────────────");
        log.info("  {} Four-hour RSI Alert Check — STARTED", marketType);
        log.info("────────────────────────────────────────");

        List<String> symbols = Arrays.stream(configuredSymbols.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .toList();

        int rsiPeriod = marketConfig.getRsi().getPeriod();
        int maPeriod  = marketConfig.getRsi().getMaPeriod();

        for (String symbol : symbols) {
            try {
                List<OHLCData> bars = priceDataService.fetchOHLC(symbol, chartInterval, chartRange);

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

                log.info("{} | {} RSI={} | Price={} | 4h Support={} | 4h Resistance={} | Pivot R1={} S4={}",
                    symbol,
                    chartInterval,
                    String.format("%.2f", result.rsi()),
                    String.format("%.4f", currentPrice),
                    String.format("%.4f", support),
                    String.format("%.4f", resistance),
                    pivots != null ? String.format("%.4f", pivots.r1()) : "N/A",
                    pivots != null ? String.format("%.4f", pivots.s4()) : "N/A");

                // ── RSI conditional alert (only fires at RSI ≤30 or ≥80) ───────────
                rsiAlertService.evaluateAndAlert(
                    symbol, result.rsi(), overboughtThreshold, oversoldThreshold,
                    currentPrice, support, resistance, pivots);

                // ── Unconditional zone update (pivot + S/R, always fires) ──────────
                rsiAlertService.sendZoneUpdate(
                    symbol, result.rsi(), currentPrice, support, resistance, pivots);

                // Respect Yahoo Finance rate limits
                Thread.sleep(700);

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("{} four-hour RSI check interrupted.", marketType);
                break;
            } catch (Exception e) {
                log.error("Error checking RSI for {}: {}", symbol, e.getMessage(), e);
            }
        }

        log.info("────────────────────────────────────────");
        log.info("  {} Four-hour RSI Alert Check — DONE", marketType);
        log.info("────────────────────────────────────────");
    }

    // ── Daily watchlist check (4H + Daily chart) ─────────────────────────────

    /**
     * Runs once per day for the watchlist symbols (default: ETH-USD, TSLA, NVDA, IAU),
     * checking BOTH the 4-hour and Daily chart in a single combined report.
     *
    * The default cron fires at 8:00 AM America/New_York, with daylight saving
    * time handled by the scheduler zone.
     */
    @Scheduled(cron = "${watchlist.scheduler.cron:0 0 8 * * *}", zone = "America/New_York")
    public void runDailyWatchlistCheck() {
        log.info("────────────────────────────────────────");
        log.info("  Daily Watchlist Check (4H + Daily) — STARTED");
        log.info("────────────────────────────────────────");

        List<String> symbols = Arrays.stream(watchlistSymbols.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .toList();

        int rsiPeriod = marketConfig.getRsi().getPeriod();
        int maPeriod  = marketConfig.getRsi().getMaPeriod();
        int lookback  = marketConfig.getSupportResistanceLookback();
        List<RsiAlertService.WatchlistSnapshot> snapshots = new java.util.ArrayList<>();

        for (String symbol : symbols) {
            RsiAlertService.TimeframeSnapshot fourHour = null;
            RsiAlertService.TimeframeSnapshot daily = null;
            IndicatorService.PivotPoints pivots = null;
            IndicatorService.PivotPoints pivots4h = null;
            List<OHLCData> bars4h = List.of();
            List<OHLCData> barsDay = List.of();

            try {
                bars4h = priceDataService.fetchOHLC(symbol, INTERVAL_4H, RANGE_3MO);
                Thread.sleep(700);
                barsDay = priceDataService.fetchOHLC(symbol, INTERVAL_1D, RANGE_10D);
                Thread.sleep(700);

                fourHour = buildSnapshot("4-Hour", bars4h, rsiPeriod, maPeriod, lookback);
                daily = buildSnapshot("Daily", barsDay, rsiPeriod, maPeriod, lookback);

                if (!barsDay.isEmpty()) {
                    pivots = indicatorService.calculatePivotPoints(barsDay);
                }
                if (!bars4h.isEmpty()) {
                    pivots4h = indicatorService.calculatePivotPoints(bars4h);
                }

                if (fourHour == null && daily == null) {
                    log.warn("No usable data for {} on either timeframe — keeping symbol in report as unavailable.", symbol);
                }

                snapshots.add(new RsiAlertService.WatchlistSnapshot(symbol, fourHour, daily, pivots, pivots4h));

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Daily watchlist check interrupted.");
                break;
            } catch (Exception e) {
                log.error("Error checking watchlist symbol {}: {}", symbol, e.getMessage(), e);
                snapshots.add(new RsiAlertService.WatchlistSnapshot(symbol, null, null, null, null));
            }
        }

        rsiAlertService.sendWatchlistReport(snapshots);

        log.info("────────────────────────────────────────");
        log.info("  Daily Watchlist Check — DONE");
        log.info("────────────────────────────────────────");
    }

    private RsiAlertService.TimeframeSnapshot buildSnapshot(String label, List<OHLCData> bars,
                                                             int rsiPeriod, int maPeriod, int lookback) {
        if (bars.isEmpty()) {
            log.warn("No {} bars available — skipping that timeframe.", label);
            return null;
        }

        IndicatorService.RSIResult result = indicatorService.calculateRSI(bars, rsiPeriod, maPeriod);
        if (!result.enoughData()) {
            log.warn("Not enough {} bars to compute RSI reliably.", label);
            return null;
        }

        double price = bars.get(bars.size() - 1).getClose();
        double[] sr  = indicatorService.calculateSupportResistance(bars, lookback);
        return new RsiAlertService.TimeframeSnapshot(label, result.rsi(), result.rsiMA(), price, sr[0], sr[1]);
    }
}
