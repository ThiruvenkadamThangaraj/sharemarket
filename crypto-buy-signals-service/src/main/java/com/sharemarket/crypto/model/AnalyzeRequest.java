package com.sharemarket.crypto.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AnalyzeRequest(
    @NotEmpty(message = "symbols must not be empty")
    List<@NotBlank(message = "symbol must not be blank") String> symbols
) {
}
