package com.sharemarket.crypto.service;

import com.sharemarket.crypto.config.CryptoFrameworkProperties;
import com.sharemarket.crypto.model.Candle;
import com.sharemarket.crypto.model.CoinSignalResponse;
import com.sharemarket.crypto.model.Decision;
import com.sharemarket.crypto.model.Strength;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

@Service
public class CryptoSignalAnalyzerService {

    private final PriceDataClient priceDataClient;
    private final IndicatorService indicatorService;
    private final CryptoFrameworkProperties properties;

    public CryptoSignalAnalyzerService(
        PriceDataClient priceDataClient,
        IndicatorService indicatorService,
        CryptoFrameworkProperties properties
    ) {
        this.priceDataClient = priceDataClient;
        this.indicatorService = indicatorService;
        this.properties = properties;
    }

    public List<CoinSignalResponse> analyze(List<String> symbols) {
        Set<String> allowedSymbols = properties.getAllowedSymbols().stream()
            .map(this::normalizeConfiguredSymbol)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        List<CoinSignalResponse> responses = new ArrayList<>();
        for (String symbol : symbols) {
            String normalized = resolveRequestedSymbol(symbol, allowedSymbols);
            if (!allowedSymbols.contains(normalized)) {
                throw new IllegalArgumentException(
                    "Unsupported symbol '" + symbol + "'. Allowed symbols: "
                        + String.join(", ", allowedSymbols)
                );
            }
            responses.add(analyzeOne(normalized));
        }
        return responses;
    }

    private String normalizeConfiguredSymbol(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    private String resolveRequestedSymbol(String symbol, Set<String> allowedSymbols) {
        String normalized = normalizeConfiguredSymbol(symbol);

        if (allowedSymbols.contains(normalized)) {
            return normalized;
        }

        if (normalized.endsWith("-USD")) {
            String base = normalized.substring(0, normalized.length() - 4);
            if (allowedSymbols.contains(base)) {
                return base;
            }
        } else {
            String usd = normalized + "-USD";
            if (allowedSymbols.contains(usd)) {
                return usd;
            }
        }

        return normalized;
    }

    private CoinSignalResponse analyzeOne(String symbol) {
        List<Candle> candles = priceDataClient.fetchCandles(symbol, properties.getInterval(), properties.getRange());
        if (candles.isEmpty()) {
            return new CoinSignalResponse(
                symbol,
                0.0,
                0.0,
                0.0,
                Double.NaN,
                Map.of(),
                0,
                false,
                false,
                false,
                "NO_DATA",
                Decision.WAIT,
                Strength.NONE,
                "No market data returned from Yahoo Finance"
            );
        }

        candles = candles.stream().sorted(Comparator.comparingLong(Candle::timestamp)).toList();

        Candle latest = candles.get(candles.size() - 1);
        double currentPrice = latest.close();
        double athPrice = candles.stream().mapToDouble(Candle::high).max().orElse(0.0);
        double athThreshold = athPrice * (1.0 - properties.getAthDiscountPercent() / 100.0);
        boolean athDiscountMet = currentPrice <= athThreshold;

        IndicatorService.IndicatorSnapshot indicators =
            indicatorService.calculate(candles, properties.getRsiPeriod(), properties.getMaPeriods());

        if (!indicators.enoughData()) {
            return new CoinSignalResponse(
                symbol,
                currentPrice,
                athPrice,
                athThreshold,
                Double.NaN,
                Map.of(),
                0,
                athDiscountMet,
                false,
                false,
                "INSUFFICIENT_DATA",
                Decision.WAIT,
                Strength.NONE,
                "Insufficient bars for reliable RSI/MA calculations"
            );
        }

        double rsi = indicators.rsi();
        boolean rsiMet = rsi < properties.getRsiBuyThreshold();

        int maHits = 0;
        for (double maValue : indicators.movingAverages().values()) {
            if (currentPrice <= maValue) {
                maHits++;
            }
        }
        boolean maSignalMet = maHits > 0;

        DecisionEngine.DecisionResult decision = DecisionEngine.evaluate(
            athDiscountMet,
            rsiMet,
            maHits,
            rsi,
            properties.getRsiSellThreshold()
        );

        String reason = decision.reason();
        if (decision.decision() == Decision.WAIT) {
            reason = buildWaitReason(
                athDiscountMet,
                rsiMet,
                maSignalMet,
                currentPrice,
                athThreshold,
                rsi,
                properties.getRsiBuyThreshold(),
                indicators.movingAverages()
            );
        }

        String scenario = buildScenario(
            athDiscountMet,
            rsiMet,
            maSignalMet,
            decision.decision(),
            decision.strength()
        );

        return new CoinSignalResponse(
            symbol,
            currentPrice,
            athPrice,
            athThreshold,
            rsi,
            indicators.movingAverages(),
            maHits,
            athDiscountMet,
            rsiMet,
            maSignalMet,
            scenario,
            decision.decision(),
            decision.strength(),
            reason
        );
    }

    private String buildScenario(
        boolean athDiscountMet,
        boolean rsiMet,
        boolean maSignalMet,
        Decision decision,
        Strength strength
    ) {
        if (athDiscountMet && rsiMet && maSignalMet) {
            return switch (strength) {
                case VERY_STRONG -> "ALL_CONDITIONS_MET (VERY_STRONG_BUY)";
                case STRONG -> "ALL_CONDITIONS_MET (STRONG_BUY)";
                case BASIC -> "ALL_CONDITIONS_MET (BASIC_BUY)";
                default -> "ALL_CONDITIONS_MET";
            };
        }

        if (decision == Decision.SELL_WATCH) {
            return "SELL_WATCH_SCENARIO";
        }

        List<String> missing = new ArrayList<>();
        if (!athDiscountMet) {
            missing.add("ATH_DISCOUNT");
        }
        if (!rsiMet) {
            missing.add("RSI");
        }
        if (!maSignalMet) {
            missing.add("MA");
        }

        if (missing.isEmpty()) {
            return "WAIT_SCENARIO";
        }

        return "MISSING_" + String.join("_AND_", missing);
    }

    private String buildWaitReason(
        boolean athDiscountMet,
        boolean rsiMet,
        boolean maSignalMet,
        double currentPrice,
        double athThreshold,
        double rsi,
        double rsiBuyThreshold,
        Map<Integer, Double> movingAverages
    ) {
        StringJoiner joiner = new StringJoiner(" | ");

        if (!athDiscountMet) {
            joiner.add(String.format(
                "ATH discount not met: price %.4f > threshold %.4f",
                currentPrice,
                athThreshold
            ));
        }

        if (!rsiMet) {
            joiner.add(String.format(
                "RSI condition not met: RSI %.2f >= %.2f",
                rsi,
                rsiBuyThreshold
            ));
        }

        if (!maSignalMet) {
            joiner.add(String.format(
                "MA condition not met: price %.4f above MA50 %.4f, MA100 %.4f, MA200 %.4f",
                currentPrice,
                movingAverages.getOrDefault(50, Double.NaN),
                movingAverages.getOrDefault(100, Double.NaN),
                movingAverages.getOrDefault(200, Double.NaN)
            ));
        }

        if (joiner.length() == 0) {
            return "Conditions are close but not sufficient for BUY";
        }

        return joiner.toString();
    }
}
