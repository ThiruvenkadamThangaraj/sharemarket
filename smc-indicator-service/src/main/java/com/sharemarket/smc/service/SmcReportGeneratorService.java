package com.sharemarket.smc.service;

import com.sharemarket.smc.config.SmcMarketConfig;
import com.sharemarket.smc.model.Candle;
import com.sharemarket.smc.model.SmcReportRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmcReportGeneratorService {

    private final SmcMarketConfig config;
    private final SmcPriceDataService priceDataService;
    private final SmcExcelReportService excelReportService;

    public String generateExcelReport() throws Exception {
        List<SmcReportRow> rows = new ArrayList<>();

        for (String symbol : config.getSymbols().getCryptoList()) {
            rows.add(analyzeOne(symbol, "CRYPTO", "Crypto"));
        }

        for (Map.Entry<String, List<String>> sectorEntry : config.getSymbols().getSectorMap().entrySet()) {
            String sector = sectorEntry.getKey();
            for (String symbol : sectorEntry.getValue()) {
                rows.add(analyzeOne(symbol, "STOCK", sector));
            }
        }

        return excelReportService.writeReport(rows);
    }

    private SmcReportRow analyzeOne(String symbol, String type, String sector) {
        try {
            List<Candle> candles = priceDataService.fetchCandles(symbol);
            if (candles.isEmpty()) {
                return errorRow(type, sector, symbol);
            }

            int from = Math.max(0, candles.size() - config.getRangeLookback());
            List<Candle> window = candles.subList(from, candles.size());

            double currentPrice = candles.get(candles.size() - 1).getClose();

            Zone buyZoneObj = findBullishOrderBlockZone(window, config.getSwingWindow());
            Zone sellZoneObj = findBearishOrderBlockZone(window, config.getSwingWindow());

            double buyZoneLow = buyZoneObj.low();
            double buyZoneHigh = buyZoneObj.high();
            double sellZoneLow = sellZoneObj.low();
            double sellZoneHigh = sellZoneObj.high();

            // Keep boundaries ordered before validation.
            if (buyZoneHigh < buyZoneLow) {
                double tmp = buyZoneHigh;
                buyZoneHigh = buyZoneLow;
                buyZoneLow = tmp;
            }
            if (sellZoneHigh < sellZoneLow) {
                double tmp = sellZoneHigh;
                sellZoneHigh = sellZoneLow;
                sellZoneLow = tmp;
            }

            // Hard validation rules from report requirements:
            // 1) Buy must be fully below current (buyHigh < current)
            // 2) Sell must be fully above current (sellLow > current)
            // 3) If both zones exist, buy zone must stay below sell zone (buyHigh < sellLow)
            boolean validBuyZone = buyZoneLow < buyZoneHigh && buyZoneHigh < currentPrice;
            boolean validSellZone = sellZoneLow < sellZoneHigh && sellZoneLow > currentPrice;

            // Only invalidate both if they exist AND overlap
            if (validBuyZone && validSellZone && buyZoneHigh >= sellZoneLow) {
                // Zones overlap - blank both
                validBuyZone = false;
                validSellZone = false;
            }

            String buyZone = validBuyZone ? String.format("%.2f - %.2f", buyZoneLow, buyZoneHigh) : "N/A";
            String sellZone = validSellZone ? String.format("%.2f - %.2f", sellZoneLow, sellZoneHigh) : "N/A";

            String action = calculateAction(currentPrice, buyZoneLow, buyZoneHigh, sellZoneLow, sellZoneHigh, validBuyZone, validSellZone);

            return SmcReportRow.builder()
                .type(type)
                .sector(sector)
                .symbol(symbol)
                .currentPrice(currentPrice)
                .buyZone(buyZone)
                .sellZone(sellZone)
                .buyZoneLow(buyZoneLow)
                .buyZoneHigh(buyZoneHigh)
                .sellZoneLow(sellZoneLow)
                .sellZoneHigh(sellZoneHigh)
                .action(action)
                .build();
        } catch (Exception ex) {
            log.warn("SMC analyze failed for {}: {}", symbol, ex.getMessage());
            return errorRow(type, sector, symbol);
        }
    }

    private SmcReportRow errorRow(String type, String sector, String symbol) {
        return SmcReportRow.builder()
            .type(type)
            .sector(sector)
            .symbol(symbol)
            .currentPrice(0.0)
            .buyZone("N/A")
            .sellZone("N/A")
            .buyZoneLow(0.0)
            .buyZoneHigh(0.0)
            .sellZoneLow(0.0)
            .sellZoneHigh(0.0)
            .action("WAIT")
            .build();
    }

    private Zone findBullishOrderBlockZone(List<Candle> candles, int swingWindow) {
        int bosIndex = findLatestBullishBosIndex(candles, swingWindow);
        if (bosIndex >= 0) {
            Candle ob = findLastBearishCandleBeforeImpulse(candles, bosIndex, config.getImpulseConfirmCandles());
            if (ob != null) {
                // Tight bullish OB zone: upper wick to open (or close if higher).
                double zoneLow = Math.max(ob.getOpen(), ob.getClose());
                double zoneHigh = ob.getHigh();
                return new Zone(zoneLow, zoneHigh);
            }
        }

        double rangeLow = candles.stream().mapToDouble(Candle::getLow).min().orElse(0.0);
        double rangeHigh = candles.stream().mapToDouble(Candle::getHigh).max().orElse(0.0);
        double rangeSize = Math.max(rangeHigh - rangeLow, 0.00001);
        return new Zone(rangeLow, rangeLow + (rangeSize * config.getBuyZonePercent() / 100.0));
    }

    private Zone findBearishOrderBlockZone(List<Candle> candles, int swingWindow) {
        int bosIndex = findLatestBearishBosIndex(candles, swingWindow);
        if (bosIndex >= 0) {
            Candle ob = findLastBullishCandleBeforeImpulse(candles, bosIndex, config.getImpulseConfirmCandles());
            if (ob != null) {
                // Tight bearish OB zone: low to open (or close if lower).
                double zoneLow = ob.getLow();
                double zoneHigh = Math.min(ob.getOpen(), ob.getClose());
                return new Zone(zoneLow, zoneHigh);
            }
        }

        double rangeLow = candles.stream().mapToDouble(Candle::getLow).min().orElse(0.0);
        double rangeHigh = candles.stream().mapToDouble(Candle::getHigh).max().orElse(0.0);
        double rangeSize = Math.max(rangeHigh - rangeLow, 0.00001);
        return new Zone(rangeHigh - (rangeSize * config.getSellZonePercent() / 100.0), rangeHigh);
    }

    private int findLatestBullishBosIndex(List<Candle> candles, int swingWindow) {
        int lastBos = -1;
        int lookback = Math.max(5, config.getImpulseRangeLookback());
        for (int i = (swingWindow * 2) + 1; i < candles.size(); i++) {
            int swingHighIdx = findLatestSwingHighIndexBefore(candles, i - 1, swingWindow);
            if (swingHighIdx < 0) {
                continue;
            }
            double swingHigh = candles.get(swingHighIdx).getHigh();
            if (candles.get(i).getClose() > swingHigh && isStrongBos(candles, i, lookback, true)) {
                lastBos = i;
            }
        }
        return lastBos;
    }

    private int findLatestBearishBosIndex(List<Candle> candles, int swingWindow) {
        int lastBos = -1;
        int lookback = Math.max(5, config.getImpulseRangeLookback());
        for (int i = (swingWindow * 2) + 1; i < candles.size(); i++) {
            int swingLowIdx = findLatestSwingLowIndexBefore(candles, i - 1, swingWindow);
            if (swingLowIdx < 0) {
                continue;
            }
            double swingLow = candles.get(swingLowIdx).getLow();
            if (candles.get(i).getClose() < swingLow && isStrongBos(candles, i, lookback, false)) {
                lastBos = i;
            }
        }
        return lastBos;
    }

    private int findLatestSwingHighIndexBefore(List<Candle> candles, int endIndex, int window) {
        int start = window;
        int last = Math.min(endIndex, candles.size() - 1 - window);
        for (int i = last; i >= start; i--) {
            if (isSwingHigh(candles, i, window)) {
                return i;
            }
        }
        return -1;
    }

    private int findLatestSwingLowIndexBefore(List<Candle> candles, int endIndex, int window) {
        int start = window;
        int last = Math.min(endIndex, candles.size() - 1 - window);
        for (int i = last; i >= start; i--) {
            if (isSwingLow(candles, i, window)) {
                return i;
            }
        }
        return -1;
    }

    private Candle findLastBearishCandleBeforeImpulse(List<Candle> candles, int bosIndex, int confirmCandles) {
        for (int i = bosIndex - 1; i >= 0; i--) {
            Candle c = candles.get(i);
            if (c.getClose() < c.getOpen() && hasBullishImpulseAfter(candles, i, bosIndex, confirmCandles)) {
                return c;
            }
        }
        return null;
    }

    private Candle findLastBullishCandleBeforeImpulse(List<Candle> candles, int bosIndex, int confirmCandles) {
        for (int i = bosIndex - 1; i >= 0; i--) {
            Candle c = candles.get(i);
            if (c.getClose() > c.getOpen() && hasBearishImpulseAfter(candles, i, bosIndex, confirmCandles)) {
                return c;
            }
        }
        return null;
    }

    private boolean hasBullishImpulseAfter(List<Candle> candles, int obIndex, int bosIndex, int confirmCandles) {
        int end = Math.min(obIndex + confirmCandles, bosIndex - 1);
        if (end < obIndex + 1) {
            return false;
        }
        for (int i = obIndex + 1; i <= end; i++) {
            Candle c = candles.get(i);
            if (c.getClose() <= c.getOpen()) {
                return false;
            }
        }
        return true;
    }

    private boolean hasBearishImpulseAfter(List<Candle> candles, int obIndex, int bosIndex, int confirmCandles) {
        int end = Math.min(obIndex + confirmCandles, bosIndex - 1);
        if (end < obIndex + 1) {
            return false;
        }
        for (int i = obIndex + 1; i <= end; i++) {
            Candle c = candles.get(i);
            if (c.getClose() >= c.getOpen()) {
                return false;
            }
        }
        return true;
    }

    private boolean isStrongBos(List<Candle> candles, int bosIndex, int lookback, boolean bullish) {
        if (bosIndex <= 0) {
            return false;
        }
        double avgRange = averageRange(candles, bosIndex, lookback);
        if (avgRange <= 0.0) {
            return false;
        }

        double move = bullish
            ? candles.get(bosIndex).getClose() - candles.get(bosIndex - 1).getClose()
            : candles.get(bosIndex - 1).getClose() - candles.get(bosIndex).getClose();

        return move > (avgRange * config.getBosStrengthMultiplier());
    }

    private double averageRange(List<Candle> candles, int endIndexInclusive, int lookback) {
        int start = Math.max(0, endIndexInclusive - lookback + 1);
        double sum = 0.0;
        int count = 0;
        for (int i = start; i <= endIndexInclusive; i++) {
            Candle c = candles.get(i);
            sum += Math.max(c.getHigh() - c.getLow(), 0.0);
            count++;
        }
        return count == 0 ? 0.0 : sum / count;
    }

    private boolean isSwingLow(List<Candle> candles, int i, int window) {
        double center = candles.get(i).getLow();
        for (int j = i - window; j <= i + window; j++) {
            if (j == i) {
                continue;
            }
            if (candles.get(j).getLow() <= center) {
                return false;
            }
        }
        return true;
    }

    private boolean isSwingHigh(List<Candle> candles, int i, int window) {
        double center = candles.get(i).getHigh();
        for (int j = i - window; j <= i + window; j++) {
            if (j == i) {
                continue;
            }
            if (candles.get(j).getHigh() >= center) {
                return false;
            }
        }
        return true;
    }

    private String calculateAction(double currentPrice, double buyZoneLow, double buyZoneHigh,
                                   double sellZoneLow, double sellZoneHigh,
                                   boolean validBuyZone, boolean validSellZone) {
        // If zones are not valid, we're in wait state
        if (!validBuyZone || !validSellZone) {
            return "WAIT";
        }

        // Current price within buy zone: BUY ZONE
        if (currentPrice >= buyZoneLow && currentPrice <= buyZoneHigh) {
            return "BUY ZONE";
        }

        // Current price within sell zone: SELL ZONE
        if (currentPrice >= sellZoneLow && currentPrice <= sellZoneHigh) {
            return "SELL ZONE";
        }

        // Calculate threshold for "NEAR" zones: 2% of price
        double nearThreshold = currentPrice * 0.02;

        // Close to buy zone from above
        if (currentPrice > buyZoneHigh && currentPrice <= (buyZoneHigh + nearThreshold)) {
            return "NEAR BUY";
        }

        // Close to sell zone from below
        if (currentPrice < sellZoneLow && currentPrice >= (sellZoneLow - nearThreshold)) {
            return "NEAR SELL";
        }

        // Between zones or far from both
        return "WAIT";
    }

    private record Zone(double low, double high) {}
}
