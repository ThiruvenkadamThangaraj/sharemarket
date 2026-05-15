package com.sharemarket.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Runs the hourly RSI alert check once immediately on startup, then forces
 * the application to exit so GitHub Actions jobs complete cleanly.
 *
 * Only active when {@code alert.run-on-startup=true}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "alert.run-on-startup", havingValue = "true")
public class AlertStartupRunner implements ApplicationRunner {

    private final HourlyRsiAlertJob hourlyRsiAlertJob;
    private final ApplicationContext applicationContext;

    @Override
    public void run(ApplicationArguments args) {
        log.info("alert.run-on-startup=true → running one-shot RSI alert check");
        try {
            hourlyRsiAlertJob.runHourlyRsiCheck();
            log.info("One-shot RSI alert check complete — shutting down.");
        } finally {
            // Force exit so the scheduler threads don't keep the JVM alive.
            // This is required for GitHub Actions to finish the job cleanly.
            int exitCode = SpringApplication.exit(applicationContext, () -> 0);
            System.exit(exitCode);
        }
    }
}
