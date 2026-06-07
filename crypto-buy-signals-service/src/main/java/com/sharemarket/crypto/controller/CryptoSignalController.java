package com.sharemarket.crypto.controller;

import com.sharemarket.crypto.model.AnalyzeRequest;
import com.sharemarket.crypto.model.CoinSignalResponse;
import com.sharemarket.crypto.model.ReportGenerationResponse;
import com.sharemarket.crypto.service.CryptoSignalAnalyzerService;
import com.sharemarket.crypto.service.CryptoExcelReportService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/crypto/signals")
public class CryptoSignalController {

    private final CryptoSignalAnalyzerService analyzerService;
    private final CryptoExcelReportService reportService;

    public CryptoSignalController(CryptoSignalAnalyzerService analyzerService,
                                  CryptoExcelReportService reportService) {
        this.analyzerService = analyzerService;
        this.reportService = reportService;
    }

    @PostMapping("/analyze")
    public List<CoinSignalResponse> analyze(@Valid @RequestBody AnalyzeRequest request) {
        return analyzerService.analyze(request.symbols(), request.sellTargets());
    }

    @PostMapping("/analyze/report")
    public ReportGenerationResponse analyzeAndGenerateReport(@Valid @RequestBody AnalyzeRequest request) {
        List<CoinSignalResponse> rows = analyzerService.analyze(request.symbols(), request.sellTargets());
        String reportPath = reportService.writeReport(rows);
        return new ReportGenerationResponse(reportPath, rows.size());
    }
}
