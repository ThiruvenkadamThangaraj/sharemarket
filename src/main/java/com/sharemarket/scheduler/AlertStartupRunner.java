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
 * Runs the daily 4H + Daily watchlist check once on startup, then forces the
 * application to exit so GitHub Actions jobs complete cleanly.
 *
 * Since the app is a one-shot process here (it exits right after this runs),
 * the {@code @Scheduled} daily watchlist job never gets a chance to fire on
 * its own, so the watchlist check is invoked directly. It reports only the
 * 4-hour and Daily charts, matching 8 PM ET during DST / 7 PM ET during
 * standard time when GitHub Actions runs at 00:00 UTC.
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
        log.info("alert.run-on-startup=true → running one-shot 4H + Daily watchlist check");
        try {
            hourlyRsiAlertJob.runDailyWatchlistCheck();

            log.info("One-shot 4H + Daily watchlist check complete — shutting down.");
        } finally {
            // Force exit so the scheduler threads don't keep the JVM alive.
            // This is required for GitHub Actions to finish the job cleanly.
            int exitCode = SpringApplication.exit(applicationContext, () -> 0);
            System.exit(exitCode);
        }
    }
}
