package com.sharemarket.crypto.service;

import com.sharemarket.crypto.model.Candle;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class IndicatorService {

    public record IndicatorSnapshot(double rsi, Map<Integer, Double> movingAverages, boolean enoughData) {
    }

    public IndicatorSnapshot calculate(List<Candle> candles, int rsiPeriod, List<Integer> maPeriods) {
        if (candles == null || candles.isEmpty() || maPeriods == null || maPeriods.isEmpty()) {
            return new IndicatorSnapshot(Double.NaN, Map.of(), false);
        }

        int maxMa = maPeriods.stream().max(Integer::compareTo).orElse(200);
        int minBars = Math.max(maxMa, rsiPeriod + 1);
        if (candles.size() < minBars) {
            return new IndicatorSnapshot(Double.NaN, Map.of(), false);
        }

        BarSeries series = new BaseBarSeries(candles.get(0).symbol());
        for (Candle candle : candles) {
            ZonedDateTime endTime = ZonedDateTime.ofInstant(Instant.ofEpochSecond(candle.timestamp()), ZoneOffset.UTC);
            series.addBar(endTime, candle.open(), candle.high(), candle.low(), candle.close(), candle.volume());
        }

        int lastIdx = series.getEndIndex();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        RSIIndicator rsiIndicator = new RSIIndicator(closePrice, rsiPeriod);

        Map<Integer, Double> maMap = new LinkedHashMap<>();
        for (int period : maPeriods) {
            SMAIndicator sma = new SMAIndicator(closePrice, period);
            maMap.put(period, sma.getValue(lastIdx).doubleValue());
        }

        return new IndicatorSnapshot(
            rsiIndicator.getValue(lastIdx).doubleValue(),
            Map.copyOf(maMap),
            true
        );
    }
}
