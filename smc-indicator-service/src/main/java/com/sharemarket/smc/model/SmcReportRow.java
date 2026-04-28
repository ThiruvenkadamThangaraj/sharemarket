package com.sharemarket.smc.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmcReportRow {
    private String type;
    private String sector;
    private String symbol;

    private double currentPrice;

    private String buyZone;
    private String sellZone;
    
    private double buyZoneLow;
    private double buyZoneHigh;
    private double sellZoneLow;
    private double sellZoneHigh;
    
    private String action;
}
