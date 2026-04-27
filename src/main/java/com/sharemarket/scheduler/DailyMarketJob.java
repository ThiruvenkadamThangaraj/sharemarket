package com.sharemarket.scheduler;

import com.sharemarket.config.MarketConfig;
import com.sharemarket.model.MarketSignal;
import com.sharemarket.service.ExcelReportService;
import com.sharemarket.service.SignalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Spring-managed scheduled job that runs daily after market close.
 *
 * Default schedule (application.properties → market.scheduler.cron):
 *   "0 5 21 * * *"  →  21:05 UTC every day
 *                       = 4:05 PM US-Eastern (after NYSE close)
 *                       = 5:05 PM after DST
 *
 * Crypto runs 24/7 but we piggyback on the same window for a unified report.
 *
 * To run immediately on startup (dev / testing), set:
 *   market.run-on-startup=true
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyMarketJob {

    private final MarketConfig       config;
    private final SignalService      signalService;
    private final ExcelReportService excelReportService;

    // ── Scheduled entry point ──────────────────────────────────────────────

    @Scheduled(cron = "${market.scheduler.cron}")
    public void runDailyAnalysis() {
        log.info("========================================");
        log.info("  Daily Market Analysis — STARTED");
        log.info("========================================");

        List<MarketSignal> signals = new ArrayList<>();

        // Crypto symbols
        for (String raw : config.getSymbols().getCryptoList()) {
            String symbol = raw.trim();
            try {
                signals.add(signalService.analyzeSymbol(symbol, "CRYPTO", "Crypto"));
                throttle();
            } catch (Exception e) {
                log.error("Error analysing crypto {}: {}", symbol, e.getMessage(), e);
            }
        }

        // Stock symbols — grouped by sector, preserving order from application.properties
        Map<String, List<String>> sectorMap = config.getSymbols().getSectorMap();
        for (Map.Entry<String, List<String>> entry : sectorMap.entrySet()) {
            String sector = entry.getKey();
            log.info("Analysing sector: {}", sector);
            for (String raw : entry.getValue()) {
                String symbol = raw.trim();
                try {
                    signals.add(signalService.analyzeSymbol(symbol, "STOCK", sector));
                    throttle();
                } catch (Exception e) {
                    log.error("Error analysing {} ({}): {}", symbol, sector, e.getMessage(), e);
                }
            }
        }

        // Write Excel report
        try {
            String path = excelReportService.writeReport(signals);
            log.info("========================================");
            log.info("  Report saved → {}", path);
            log.info("  Summary:");
            signals.forEach(s ->
                log.info("    {:12s} [{:6s}]  {:14s}  RSI={} / MA={}",
                    s.getSymbol(), s.getType(), s.getSignal(),
                    String.format("%.1f", s.getRsi()),
                    String.format("%.1f", s.getRsiMA())));
            log.info("========================================");
        } catch (Exception e) {
            log.error("Failed to write Excel report: {}", e.getMessage(), e);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Short pause between API calls to avoid hitting Yahoo Finance rate limits.
     */
    private void throttle() {
        try {
            Thread.sleep(700);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
