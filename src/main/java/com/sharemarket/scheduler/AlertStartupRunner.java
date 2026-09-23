package com.sharemarket.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Runs the appropriate one-shot alert task on startup, then forces the
 * application to exit so GitHub Actions jobs complete cleanly.
 *
 * Since the app is a one-shot process here, scheduled methods never get a
 * chance to fire on their own. GitHub Actions wakes the app hourly and this
 * runner invokes the task due at the current UTC/Eastern time.
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
        ZonedDateTime utcNow = ZonedDateTime.now(ZoneId.of("UTC"));
        ZonedDateTime easternNow = utcNow.withZoneSameInstant(ZoneId.of("America/New_York"));
        try {
            if (utcNow.getHour() % 4 == 0) {
                hourlyRsiAlertJob.runCryptoRsiCheck();
            }

            boolean weekday = easternNow.getDayOfWeek().compareTo(DayOfWeek.MONDAY) >= 0
                && easternNow.getDayOfWeek().compareTo(DayOfWeek.FRIDAY) <= 0;
            if (weekday && (easternNow.getHour() == 10 || easternNow.getHour() == 14)) {
                hourlyRsiAlertJob.runStockRsiCheck();
            }

            if (easternNow.getHour() == 8) {
                hourlyRsiAlertJob.runDailyWatchlistCheck();
            }

            log.info("One-shot alert check complete for UTC={} / Eastern={} — shutting down.",
                utcNow, easternNow);
        } finally {
            // Force exit so the scheduler threads don't keep the JVM alive.
            // This is required for GitHub Actions to finish the job cleanly.
            int exitCode = SpringApplication.exit(applicationContext, () -> 0);
            System.exit(exitCode);
        }
    }
}
