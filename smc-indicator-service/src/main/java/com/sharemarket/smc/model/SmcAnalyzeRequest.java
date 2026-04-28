package com.sharemarket.smc.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmcAnalyzeRequest {

    @NotBlank
    private String symbol;

    @NotEmpty
    @Valid
    private List<Candle> candles;

    private Double rsiBlue;
    private Double rsiYellow;

    @Builder.Default
    @Positive
    private int swingWindow = 3;

    @Builder.Default
    @Positive
    private int rangeLookback = 40;

    @Builder.Default
    @DecimalMin("0.1")
    @DecimalMax("20.0")
    private double midpointAvoidPercent = 3.0;
}
