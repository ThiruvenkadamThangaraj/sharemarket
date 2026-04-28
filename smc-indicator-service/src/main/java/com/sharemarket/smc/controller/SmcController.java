package com.sharemarket.smc.controller;

import com.sharemarket.smc.model.SmcAnalyzeRequest;
import com.sharemarket.smc.model.SmcAnalyzeResponse;
import com.sharemarket.smc.service.SmcAnalyzerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/smc")
@RequiredArgsConstructor
public class SmcController {

    private final SmcAnalyzerService analyzerService;

    @PostMapping("/analyze")
    public SmcAnalyzeResponse analyze(@Valid @RequestBody SmcAnalyzeRequest request) {
        return analyzerService.analyze(request);
    }
}
