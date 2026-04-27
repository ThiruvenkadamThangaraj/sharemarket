package com.sharemarket.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Triggers one analysis run immediately after the application context is ready.
 * Only active when {@code market.run-on-startup=true} in application.properties.
 *
 * Useful for:
 *  - Manual ad-hoc runs:   java -jar sharemarket.jar
 *  - CI / integration tests
 *  - First-time setup verification
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "market.run-on-startup", havingValue = "true")
public class StartupRunner implements ApplicationRunner {

    private final DailyMarketJob dailyMarketJob;

    @Override
    public void run(ApplicationArguments args) {
        log.info("market.run-on-startup=true → triggering immediate analysis run");
        dailyMarketJob.runDailyAnalysis();
    }
}
