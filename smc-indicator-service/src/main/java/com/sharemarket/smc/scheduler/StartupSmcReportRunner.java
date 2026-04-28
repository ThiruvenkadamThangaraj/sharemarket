package com.sharemarket.smc.scheduler;

import com.sharemarket.smc.config.SmcMarketConfig;
import com.sharemarket.smc.service.SmcReportGeneratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartupSmcReportRunner implements CommandLineRunner {

    private final SmcMarketConfig config;
    private final SmcReportGeneratorService reportGeneratorService;

    @Override
    public void run(String... args) throws Exception {
        if (!config.isRunOnStartup()) {
            log.info("SMC startup report generation is disabled.");
            return;
        }

        log.info("Generating SMC Excel report on startup...");
        String reportPath = reportGeneratorService.generateExcelReport();
        log.info("SMC Excel report ready: {}", reportPath);
    }
}
