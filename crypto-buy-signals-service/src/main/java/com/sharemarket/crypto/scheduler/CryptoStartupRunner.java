package com.sharemarket.crypto.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Runs the crypto report once immediately on startup, then exits cleanly.
 * Used by GitHub Actions so the job completes without the scheduler keeping
 * the JVM alive.
 *
 * Only active when {@code crypto.run-on-startup=true}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "crypto.run-on-startup", havingValue = "true")
public class CryptoStartupRunner implements ApplicationRunner {

    private final CryptoReportScheduler cryptoReportScheduler;
    private final ApplicationContext    applicationContext;

    @Override
    public void run(ApplicationArguments args) {
        log.info("crypto.run-on-startup=true → running one-shot crypto report");
        try {
            cryptoReportScheduler.runHourlyReport();
            log.info("One-shot crypto report complete — shutting down.");
        } finally {
            int exitCode = SpringApplication.exit(applicationContext, () -> 0);
            System.exit(exitCode);
        }
    }
}
