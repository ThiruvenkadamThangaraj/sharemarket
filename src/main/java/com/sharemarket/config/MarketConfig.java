package com.sharemarket.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Strongly typed config bound from application.properties (prefix "market").
 * Registered automatically via @ConfigurationPropertiesScan in the main class.
 */
@Data
@ConfigurationProperties(prefix = "market")
public class MarketConfig {

    /** Set true to run one analysis pass immediately on startup (dev / testing). */
    private boolean runOnStartup = false;

    /** Where to write the Excel reports. */
    private Output output = new Output();

    /** Comma-separated symbol lists. */
    private Symbols symbols = new Symbols();

    /** RSI indicator parameters. */
    private Rsi rsi = new Rsi();

    /** Yahoo Finance historical range, e.g. "3mo". */
    private String dataRange = "3mo";

    /** Yahoo Finance bar interval, e.g. "1d". */
    private String dataInterval = "1d";

    /** Number of recent bars used to compute support / resistance. */
    private int supportResistanceLookback = 20;

    /**
     * Price must be within this percentage of support/resistance to be
     * considered "near" it (e.g. 3.0 → within 3 %).
     */
    private double rangeThresholdPercent = 3.0;

    // ── Nested config classes ──────────────────────────────────────────────

    @Data
    public static class Output {
        private String directory = "./reports";
    }

    @Data
    public static class Symbols {
        private String crypto = "BTC-USD,ETH-USD,BNB-USD,SOL-USD,XRP-USD";

        /** Sector key (kebab-case) → comma-separated tickers. Bound from market.symbols.sectors.* */
        private Map<String, String> sectors = new LinkedHashMap<>();

        public List<String> getCryptoList() {
            return Arrays.asList(crypto.split(","));
        }

        /**
         * Returns ordered sector display-name → symbol list.
         * "big-tech" becomes "Big Tech", etc.
         */
        public Map<String, List<String>> getSectorMap() {
            Map<String, List<String>> result = new LinkedHashMap<>();
            sectors.forEach((key, val) ->
                result.put(toDisplayName(key),
                    Arrays.stream(val.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList())));
            return result;
        }

        /** Flat list of all stock symbols across all sectors. */
        public List<String> getStocksList() {
            return getSectorMap().values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        }

        private static String toDisplayName(String key) {
            return Arrays.stream(key.split("-"))
                .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(Collectors.joining(" "));
        }
    }

    @Data
    public static class Rsi {
        /** RSI look-back period (default 14). */
        private int period   = 14;
        /** Moving-average period applied to the RSI line (default 9). */
        private int maPeriod = 9;
        /** Minimum RSI (blue) value to classify "BULLISH START". */
        private double bullishStartBlueMin = 40.0;
        /** Minimum RSI MA (yellow) value to classify "BULLISH START". */
        private double bullishStartYellowMin = 30.0;
        /** RSI range lower bound for bullish phase (inclusive). */
        private double bullishRangeMin = 30.0;
        /** RSI range upper bound for bullish phase (inclusive). */
        private double bullishRangeMax = 40.0;
        /** RSI above this value is treated as overbought. */
        private double overboughtLevel = 70.0;
        /** RSI above this value is treated as extreme overbought (short signal). */
        private double extremeOverboughtLevel = 80.0;
        /** RSI above this value is treated as critically overbought (very high reversal risk). */
        private double criticalOverboughtLevel = 95.0;
        /** RSI below this value is treated as extreme oversold (strong buy watch). */
        private double extremeOversoldLevel = 25.0;
        /** RSI below this value is treated as critically oversold (very high reversal/bounce potential). */
        private double criticalOversoldLevel = 20.0;
    }
}
