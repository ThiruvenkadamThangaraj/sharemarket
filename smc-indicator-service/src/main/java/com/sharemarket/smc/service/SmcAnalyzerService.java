package com.sharemarket.smc.service;

import com.sharemarket.smc.model.Candle;
import com.sharemarket.smc.model.SmcAnalyzeRequest;
import com.sharemarket.smc.model.SmcAnalyzeResponse;
import com.sharemarket.smc.model.SmcSignalStatus;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class SmcAnalyzerService {

    public SmcAnalyzeResponse analyze(SmcAnalyzeRequest request) {
        List<Candle> candles = request.getCandles();
        int minBars = Math.max(request.getRangeLookback(), (request.getSwingWindow() * 2) + 3);
        if (candles.size() < minBars) {
            throw new IllegalArgumentException("Not enough candles. Required at least " + minBars);
        }

        int from = Math.max(0, candles.size() - request.getRangeLookback());
        List<Candle> rangeWindow = candles.subList(from, candles.size());

        double rangeLow = rangeWindow.stream().map(Candle::getLow).min(Comparator.naturalOrder()).orElse(0.0);
        double rangeHigh = rangeWindow.stream().map(Candle::getHigh).max(Comparator.naturalOrder()).orElse(0.0);
        double rangeMid = (rangeLow + rangeHigh) / 2.0;

        double rangeSize = Math.max(rangeHigh - rangeLow, 0.00001);
        double midpointBandHalf = rangeSize * (request.getMidpointAvoidPercent() / 100.0) / 2.0;

        Candle latest = candles.get(candles.size() - 1);
        Candle previous = candles.get(candles.size() - 2);

        boolean inBuyZone = latest.getClose() >= rangeLow && latest.getClose() < (rangeMid - midpointBandHalf);
        boolean inSellZone = latest.getClose() <= rangeHigh && latest.getClose() > (rangeMid + midpointBandHalf);
        boolean inMidpointAvoidZone = !inBuyZone && !inSellZone;

        SwingPoint lastSwingHigh = findLastSwingHigh(candles, request.getSwingWindow());
        SwingPoint lastSwingLow = findLastSwingLow(candles, request.getSwingWindow());

        boolean bullishBos = lastSwingHigh != null
            && previous.getClose() <= lastSwingHigh.level
            && latest.getClose() > lastSwingHigh.level;

        boolean bearishBos = lastSwingLow != null
            && previous.getClose() >= lastSwingLow.level
            && latest.getClose() < lastSwingLow.level;

        boolean uptrend = previous.getClose() > rangeMid;
        boolean downtrend = previous.getClose() < rangeMid;

        boolean bullishChoch = downtrend && bullishBos;
        boolean bearishChoch = uptrend && bearishBos;

        boolean bullishOrderBlock = bullishBos && hasLastBearishCandle(candles);
        boolean bearishOrderBlock = bearishBos && hasLastBullishCandle(candles);

        boolean bullishFvg = latest.getLow() > previous.getHigh();
        boolean bearishFvg = latest.getHigh() < previous.getLow();

        boolean liquiditySweepHigh = lastSwingHigh != null
            && latest.getHigh() > lastSwingHigh.level
            && latest.getClose() < lastSwingHigh.level;

        boolean liquiditySweepLow = lastSwingLow != null
            && latest.getLow() < lastSwingLow.level
            && latest.getClose() > lastSwingLow.level;

        boolean rsiBullish = request.getRsiBlue() != null
            && request.getRsiYellow() != null
            && request.getRsiBlue() > request.getRsiYellow();

        boolean rsiBearish = request.getRsiBlue() != null
            && request.getRsiYellow() != null
            && request.getRsiBlue() < request.getRsiYellow();

        boolean bullishBreak = bullishBos || bullishChoch;
        boolean bearishBreak = bearishBos || bearishChoch;

        boolean bullishConfluence = bullishOrderBlock || bullishFvg;
        boolean bearishConfluence = bearishOrderBlock || bearishFvg;

        SmcSignalStatus status;
        String reason;

        if (bearishBreak && inSellZone && bearishConfluence && rsiBearish) {
            status = SmcSignalStatus.SHORT_CONFIRM_SMC;
            reason = "Bearish break + premium/sell zone + bearish OB/FVG + RSI bearish";
        } else if (bullishBreak && inBuyZone && bullishConfluence && rsiBullish) {
            status = SmcSignalStatus.BUY_CONFIRM_SMC;
            reason = "Bullish break + discount/buy zone + bullish OB/FVG + RSI bullish";
        } else if (liquiditySweepHigh) {
            status = SmcSignalStatus.SWEEP_TRAP_SHORT;
            reason = "Liquidity sweep above swing high with rejection";
        } else if (liquiditySweepLow) {
            status = SmcSignalStatus.SWEEP_TRAP_LONG;
            reason = "Liquidity sweep below swing low with recovery";
        } else if (inMidpointAvoidZone) {
            status = SmcSignalStatus.MIDPOINT_AVOID;
            reason = "Price is near range midpoint; avoid low-conviction entries";
        } else if (bullishBreak && inBuyZone) {
            status = SmcSignalStatus.DISCOUNT_BUY_BIAS;
            reason = "Bullish structure in discount zone; waiting for full confluence";
        } else if (bearishBreak && inSellZone) {
            status = SmcSignalStatus.PREMIUM_SELL_BIAS;
            reason = "Bearish structure in premium zone; waiting for full confluence";
        } else {
            status = SmcSignalStatus.STRUCTURE_WAIT;
            reason = "No complete SMC confluence yet";
        }

        return SmcAnalyzeResponse.builder()
            .symbol(request.getSymbol())
            .status(status)
            .reason(reason)
            .rangeLow(rangeLow)
            .rangeHigh(rangeHigh)
            .rangeMid(rangeMid)
            .inBuyZone(inBuyZone)
            .inSellZone(inSellZone)
            .inMidpointAvoidZone(inMidpointAvoidZone)
            .bullishBos(bullishBos)
            .bearishBos(bearishBos)
            .bullishChoch(bullishChoch)
            .bearishChoch(bearishChoch)
            .bullishOrderBlock(bullishOrderBlock)
            .bearishOrderBlock(bearishOrderBlock)
            .bullishFvg(bullishFvg)
            .bearishFvg(bearishFvg)
            .liquiditySweepHigh(liquiditySweepHigh)
            .liquiditySweepLow(liquiditySweepLow)
            .build();
    }

    private SwingPoint findLastSwingHigh(List<Candle> candles, int swingWindow) {
        for (int i = candles.size() - 1 - swingWindow; i >= swingWindow; i--) {
            if (isSwingHigh(candles, i, swingWindow)) {
                return new SwingPoint(i, candles.get(i).getHigh());
            }
        }
        return null;
    }

    private SwingPoint findLastSwingLow(List<Candle> candles, int swingWindow) {
        for (int i = candles.size() - 1 - swingWindow; i >= swingWindow; i--) {
            if (isSwingLow(candles, i, swingWindow)) {
                return new SwingPoint(i, candles.get(i).getLow());
            }
        }
        return null;
    }

    private boolean isSwingHigh(List<Candle> candles, int index, int swingWindow) {
        double center = candles.get(index).getHigh();
        for (int i = index - swingWindow; i <= index + swingWindow; i++) {
            if (i == index) {
                continue;
            }
            if (candles.get(i).getHigh() >= center) {
                return false;
            }
        }
        return true;
    }

    private boolean isSwingLow(List<Candle> candles, int index, int swingWindow) {
        double center = candles.get(index).getLow();
        for (int i = index - swingWindow; i <= index + swingWindow; i++) {
            if (i == index) {
                continue;
            }
            if (candles.get(i).getLow() <= center) {
                return false;
            }
        }
        return true;
    }

    private boolean hasLastBearishCandle(List<Candle> candles) {
        for (int i = candles.size() - 2; i >= 0; i--) {
            Candle c = candles.get(i);
            if (c.getClose() < c.getOpen()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasLastBullishCandle(List<Candle> candles) {
        for (int i = candles.size() - 2; i >= 0; i--) {
            Candle c = candles.get(i);
            if (c.getClose() > c.getOpen()) {
                return true;
            }
        }
        return false;
    }

    private record SwingPoint(int index, double level) {}
}
