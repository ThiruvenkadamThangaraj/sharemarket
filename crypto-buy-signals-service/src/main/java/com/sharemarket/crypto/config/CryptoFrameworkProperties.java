package com.sharemarket.crypto.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "crypto.framework")
public class CryptoFrameworkProperties {

    @NotBlank
    private String interval = "1d";

    @NotBlank
    private String range = "1y";

    @Min(2)
    @Max(100)
    private int rsiPeriod = 14;

    @DecimalMin("1.0")
    @DecimalMax("99.0")
    private double rsiBuyThreshold = 40.0;

    @DecimalMin("1.0")
    @DecimalMax("99.0")
    private double rsiSellThreshold = 70.0;

    @DecimalMin("1.0")
    @DecimalMax("99.0")
    private double athDiscountPercent = 50.0;

    @NotEmpty
    private List<@Min(2) @Max(1000) Integer> maPeriods = List.of(50, 100, 200);

    @NotEmpty
    private List<@NotBlank String> allowedSymbols = List.of("BTC-USD", "ETH-USD", "XRP-USD", "BNB-USD");

    public String getInterval() {
        return interval;
    }

    public void setInterval(String interval) {
        this.interval = interval;
    }

    public String getRange() {
        return range;
    }

    public void setRange(String range) {
        this.range = range;
    }

    public int getRsiPeriod() {
        return rsiPeriod;
    }

    public void setRsiPeriod(int rsiPeriod) {
        this.rsiPeriod = rsiPeriod;
    }

    public double getRsiBuyThreshold() {
        return rsiBuyThreshold;
    }

    public void setRsiBuyThreshold(double rsiBuyThreshold) {
        this.rsiBuyThreshold = rsiBuyThreshold;
    }

    public double getRsiSellThreshold() {
        return rsiSellThreshold;
    }

    public void setRsiSellThreshold(double rsiSellThreshold) {
        this.rsiSellThreshold = rsiSellThreshold;
    }

    public double getAthDiscountPercent() {
        return athDiscountPercent;
    }

    public void setAthDiscountPercent(double athDiscountPercent) {
        this.athDiscountPercent = athDiscountPercent;
    }

    public List<Integer> getMaPeriods() {
        return maPeriods;
    }

    public void setMaPeriods(List<Integer> maPeriods) {
        this.maPeriods = maPeriods;
    }

    public List<String> getAllowedSymbols() {
        return allowedSymbols;
    }

    public void setAllowedSymbols(List<String> allowedSymbols) {
        this.allowedSymbols = allowedSymbols;
    }
}
