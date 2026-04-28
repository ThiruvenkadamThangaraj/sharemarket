package com.sharemarket.smc.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Candle {

    @NotNull
    private Long timestamp;

    @NotNull
    @PositiveOrZero
    private Double open;

    @NotNull
    @PositiveOrZero
    private Double high;

    @NotNull
    @PositiveOrZero
    private Double low;

    @NotNull
    @PositiveOrZero
    private Double close;

    @PositiveOrZero
    private Double volume;
}
