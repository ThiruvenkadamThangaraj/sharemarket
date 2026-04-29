package com.sharemarket.crypto.model;

public record ReportGenerationResponse(
    String reportPath,
    int rowCount
) {
}
