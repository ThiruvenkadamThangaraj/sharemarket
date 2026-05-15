package com.sharemarket.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Runs the hourly RSI alert check once immediately on startup, then the
 * application exits naturally (no scheduler, no long-running process).
 *
 * Only active when {@code alert.run-on-startup=true}.
 *
 * Used by GitHub Actions to perform a single check on GitHub's cloud servers
 * without needing your local machine to be switched on.
 *
 * GitHub Actions invokes the jar as:
 *   java -Dalert.run-on-startup=true \
 *        -Dspring.mail.username=${{ secrets.GMAIL_USER }} \
 *        -Dspring.mail.password=${{ secrets.GMAIL_APP_PASSWORD }} \
 *        -Dalert.email.to=${{ secrets.ALERT_EMAIL_TO }} \
 *        -jar sharemarket.jar
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "alert.run-on-startup", havingValue = "true")
public class AlertStartupRunner implements ApplicationRunner {

    private final HourlyRsiAlertJob hourlyRsiAlertJob;

    @Override
    public void run(ApplicationArguments args) {
        log.info("alert.run-on-startup=true → running one-shot RSI alert check");
        hourlyRsiAlertJob.runHourlyRsiCheck();
        log.info("One-shot RSI alert check complete — application will exit.");
    }
}
