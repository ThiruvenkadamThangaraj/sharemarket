package com.sharemarket.service;

import com.sharemarket.model.OHLCData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Wraps TA4J to compute:
 *   - RSI(period)         — the "blue line"
 *   - SMA(maPeriod) of RSI — the "yellow line"
 *   - Support (lowest low over lookback window)
 *   - Resistance (highest high over lookback window)
 */
@Slf4j
@Service
public class IndicatorService {

    // ── Public result types ───────────────────────────────────────────────────

    /**
     * RSI and its moving average at the last bar.
     *
     * @param rsi        RSI-14 value
     * @param rsiMA      SMA-9 of RSI (yellow line)
     * @param enoughData false when the series is too short for reliable values
     */
    public record RSIResult(double rsi, double rsiMA, boolean enoughData) {}

    /**
     * Traditional Pivot Points calculated from the previous completed bar's H/L/C.
     *
     * Red zone (sell / resistance) begins at R1.
     * Blue zone (buy  / support)  begins at S4.
     */
    public record PivotPoints(
        double p,
        double r1, double r2, double r3, double r4, double r5,
        double s1, double s2, double s3, double s4, double s5
    ) {}

    // ── Public methods ────────────────────────────────────────────────────────

    /**
     * Builds a TA4J TimeSeries from the supplied bars and returns RSI + RSI-MA
     * values at the final bar.
     */
    public RSIResult calculateRSI(List<OHLCData> bars, int rsiPeriod, int maPeriod) {
        int minRequired = rsiPeriod + maPeriod;

        if (bars.size() < minRequired) {
            log.warn("Only {} bars available for {}; need ≥ {} for RSI-{}/MA-{}. Returning defaults.",
                bars.size(), bars.isEmpty() ? "?" : bars.get(0).getSymbol(),
                minRequired, rsiPeriod, maPeriod);
            return new RSIResult(50.0, 50.0, false);
        }

        BarSeries series = buildSeries(bars);

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        RSIIndicator        rsiInd     = new RSIIndicator(closePrice, rsiPeriod);
        SMAIndicator        rsiMA      = new SMAIndicator(rsiInd, maPeriod);

        int    lastIdx  = series.getEndIndex();
        double rsiVal   = rsiInd.getValue(lastIdx).doubleValue();
        double rsiMAVal = rsiMA.getValue(lastIdx).doubleValue();

        log.debug("{} | RSI({})={} | RSI-MA({})={}",
            bars.get(0).getSymbol(), rsiPeriod,
            String.format("%.2f", rsiVal), maPeriod,
            String.format("%.2f", rsiMAVal));

        return new RSIResult(rsiVal, rsiMAVal, true);
    }

    /**
     * Returns [support, resistance] from the last {@code lookback} bars.
     * Support = lowest low; Resistance = highest high.
     */
    public double[] calculateSupportResistance(List<OHLCData> bars, int lookback) {
        int start  = Math.max(0, bars.size() - lookback);
        List<OHLCData> window = bars.subList(start, bars.size());

        double support    = window.stream().mapToDouble(OHLCData::getLow).min().orElse(0.0);
        double resistance = window.stream().mapToDouble(OHLCData::getHigh).max().orElse(0.0);

        return new double[]{support, resistance};
    }

    /**
     * Calculates Traditional Pivot Points from the previous completed bar's H/L/C.
     * Pass daily bars so each bar represents one full trading session.
     *
     * Returns {@code null} if fewer than 2 bars are available.
     */
    public PivotPoints calculatePivotPoints(List<OHLCData> bars) {
        if (bars == null || bars.size() < 2) {
            return null;
        }
        // Use the second-to-last bar — the most recent fully completed session
        OHLCData prev = bars.get(bars.size() - 2);
        double h = prev.getHigh();
        double l = prev.getLow();
        double c = prev.getClose();

        double p  = (h + l + c) / 3.0;
        double r1 = 2 * p - l;
        double r2 = p + (h - l);
        double r3 = h + 2 * (p - l);
        double r4 = h + 3 * (p - l);
        double r5 = h + 4 * (p - l);
        double s1 = 2 * p - h;
        double s2 = p - (h - l);
        double s3 = l - 2 * (h - p);
        double s4 = l - 3 * (h - p);
        double s5 = l - 4 * (h - p);

        log.debug("Pivot Points | P={} R1={} R2={} S1={} S2={} S4={}",
            String.format("%.2f", p),  String.format("%.2f", r1),
            String.format("%.2f", r2), String.format("%.2f", s1),
            String.format("%.2f", s2), String.format("%.2f", s4));

        return new PivotPoints(p, r1, r2, r3, r4, r5, s1, s2, s3, s4, s5);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private BarSeries buildSeries(List<OHLCData> bars) {
        BarSeries series = new BaseBarSeries(bars.get(0).getSymbol());

        for (OHLCData bar : bars) {
            ZonedDateTime endTime = ZonedDateTime.ofInstant(
                Instant.ofEpochSecond(bar.getTimestamp()), ZoneOffset.UTC);

            // BaseTimeSeries.addBar(ZonedDateTime, Number open, high, low, close, volume)
            series.addBar(endTime,
                bar.getOpen(), bar.getHigh(), bar.getLow(),
                bar.getClose(), bar.getVolume());
        }

        return series;
    }
}
