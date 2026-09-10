package com.sharemarket.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Runs the hourly RSI alert check once immediately on startup, then forces
 * the application to exit so GitHub Actions jobs complete cleanly.
 *
 * Since the app is a one-shot process here (it exits right after this runs),
 * the {@code @Scheduled} daily watchlist job never gets a chance to fire on
 * its own — so this also piggybacks the daily watchlist check onto the one
 * daily run whose UTC hour is 0 (i.e. the 00:00 UTC GitHub Actions
 * run, matching 8 PM ET during DST / 7 PM ET during standard time).
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

            if (ZonedDateTime.now(ZoneOffset.UTC).getHour() == 0) {
                log.info("UTC hour is 0 → also running daily watchlist check (4H + Daily)");
                hourlyRsiAlertJob.runDailyWatchlistCheck();
            }

            log.info("One-shot RSI alert check complete — shutting down.");
        } finally {
            // Force exit so the scheduler threads don't keep the JVM alive.
            // This is required for GitHub Actions to finish the job cleanly.
            int exitCode = SpringApplication.exit(applicationContext, () -> 0);
            System.exit(exitCode);
        }
    }
}
