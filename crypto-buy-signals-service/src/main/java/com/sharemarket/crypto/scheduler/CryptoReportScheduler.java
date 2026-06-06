package com.sharemarket.crypto.scheduler;

import com.sharemarket.crypto.config.CryptoFrameworkProperties;
import com.sharemarket.crypto.model.CoinSignalResponse;
import com.sharemarket.crypto.service.CryptoEmailReportService;
import com.sharemarket.crypto.service.CryptoExcelReportService;
import com.sharemarket.crypto.service.CryptoSignalAnalyzerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Runs at minute :01 of every hour, analyzes all configured symbols,
 * generates an Excel report, and emails it as an attachment.
 *
 * Schedule is configurable via {@code crypto.email.scheduler.cron}.
 * Default: every hour at minute 1  (same cadence as the RSI alert job).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CryptoReportScheduler {

    private final CryptoSignalAnalyzerService analyzerService;
    private final CryptoExcelReportService    excelReportService;
    private final CryptoEmailReportService    emailReportService;
    private final CryptoFrameworkProperties   frameworkProperties;

    @Scheduled(cron = "${crypto.email.scheduler.cron:0 1 * * * *}")
    public void runHourlyReport() {
        log.info("────────────────────────────────────────");
        log.info("  Crypto Report Scheduler — STARTED");
        log.info("────────────────────────────────────────");

        try {
            List<String> symbols = frameworkProperties.getAllowedSymbols();
            log.info("Analyzing {} symbols…", symbols.size());

            List<CoinSignalResponse> rows = analyzerService.analyze(symbols);
            String reportPath = excelReportService.writeReport(rows);

            log.info("Report written to: {}", reportPath);
            emailReportService.sendReport(reportPath, rows);

        } catch (Exception e) {
            log.error("Crypto report scheduler failed: {}", e.getMessage(), e);
        }

        log.info("────────────────────────────────────────");
        log.info("  Crypto Report Scheduler — DONE");
        log.info("────────────────────────────────────────");
    }
}
