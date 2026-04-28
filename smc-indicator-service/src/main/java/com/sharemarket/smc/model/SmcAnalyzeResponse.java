package com.sharemarket.smc.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmcAnalyzeResponse {

    private String symbol;
    private SmcSignalStatus status;
    private String reason;

    private double rangeLow;
    private double rangeHigh;
    private double rangeMid;

    private boolean inBuyZone;
    private boolean inSellZone;
    private boolean inMidpointAvoidZone;

    private boolean bullishBos;
    private boolean bearishBos;
    private boolean bullishChoch;
    private boolean bearishChoch;

    private boolean bullishOrderBlock;
    private boolean bearishOrderBlock;
    private boolean bullishFvg;
    private boolean bearishFvg;

    private boolean liquiditySweepHigh;
    private boolean liquiditySweepLow;
}
