package com.sharemarket.smc.service;

import com.sharemarket.smc.model.Candle;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RsiCalculatorService {

    public record RsiResult(double rsiBlue, double rsiYellow, boolean enoughData) {}

    public RsiResult calculate(List<Candle> candles, int period, int maPeriod) {
        int minBars = period + maPeriod + 1;
        if (candles.size() < minBars) {
            return new RsiResult(50.0, 50.0, false);
        }

        List<Double> closes = candles.stream().map(Candle::getClose).toList();
        List<Double> rsiSeries = computeRsiSeries(closes, period);
        if (rsiSeries.isEmpty()) {
            return new RsiResult(50.0, 50.0, false);
        }

        double rsiBlue = rsiSeries.get(rsiSeries.size() - 1);
        int from = Math.max(0, rsiSeries.size() - maPeriod);
        double sum = 0.0;
        for (int i = from; i < rsiSeries.size(); i++) {
            sum += rsiSeries.get(i);
        }
        double rsiYellow = sum / (rsiSeries.size() - from);

        return new RsiResult(rsiBlue, rsiYellow, true);
    }

    private List<Double> computeRsiSeries(List<Double> closes, int period) {
        List<Double> out = new ArrayList<>();
        if (closes.size() <= period) {
            return out;
        }

        double gain = 0.0;
        double loss = 0.0;
        for (int i = 1; i <= period; i++) {
            double diff = closes.get(i) - closes.get(i - 1);
            gain += Math.max(diff, 0.0);
            loss += Math.max(-diff, 0.0);
        }

        double avgGain = gain / period;
        double avgLoss = loss / period;
        out.add(toRsi(avgGain, avgLoss));

        for (int i = period + 1; i < closes.size(); i++) {
            double diff = closes.get(i) - closes.get(i - 1);
            double currentGain = Math.max(diff, 0.0);
            double currentLoss = Math.max(-diff, 0.0);

            avgGain = ((avgGain * (period - 1)) + currentGain) / period;
            avgLoss = ((avgLoss * (period - 1)) + currentLoss) / period;
            out.add(toRsi(avgGain, avgLoss));
        }

        return out;
    }

    private double toRsi(double avgGain, double avgLoss) {
        if (avgLoss == 0.0) {
            return 100.0;
        }
        if (avgGain == 0.0) {
            return 0.0;
        }
        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }
}
