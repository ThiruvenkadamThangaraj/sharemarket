package com.sharemarket.smc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@ConfigurationProperties(prefix = "smc")
public class SmcMarketConfig {

    private boolean runOnStartup = true;
    private String dataInterval = "1d";
    private String dataRange = "3mo";

    private int swingWindow = 3;
    private int rangeLookback = 120;
    private double midpointAvoidPercent = 3.0;
    /** Lookback bars for average range used in strong BOS validation. */
    private int impulseRangeLookback = 20;
    /** BOS candle close-to-close move must exceed avg range * multiplier. */
    private double bosStrengthMultiplier = 1.5;
    /** Consecutive candles after OB required to confirm impulse leg. */
    private int impulseConfirmCandles = 2;
    /** Ignore very recent minor pivots; use older zones. */
    private int minPivotAgeBars = 10;
    /** Minimum impulse after pivot as fraction of full range (0.0 - 1.0). */
    private double minImpulseFraction = 0.20;
    /** Buy zone width as % of full range, measured upward from range low. */
    private double buyZonePercent = 15.0;
    /** Sell zone width as % of full range, measured downward from range high. */
    private double sellZonePercent = 15.0;

    private Output output = new Output();
    private Rsi rsi = new Rsi();
    private Symbols symbols = new Symbols();

    @Data
    public static class Output {
        private String directory = "./smc-reports";
    }

    @Data
    public static class Rsi {
        private int period = 14;
        private int maPeriod = 9;
    }

    @Data
    public static class Symbols {
        private String crypto = "BTC-USD,ETH-USD,BNB-USD,SOL-USD,XRP-USD";
        private Map<String, String> sectors = new LinkedHashMap<>();

        public List<String> getCryptoList() {
            return Arrays.stream(crypto.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        }

        public Map<String, List<String>> getSectorMap() {
            Map<String, List<String>> out = new LinkedHashMap<>();
            sectors.forEach((key, value) -> out.put(
                toDisplayName(key),
                Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList())
            ));
            return out;
        }

        public List<String> getStocksList() {
            return getSectorMap().values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        }

        private static String toDisplayName(String key) {
            return Arrays.stream(key.split("-"))
                .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .collect(Collectors.joining(" "));
        }
    }
}
