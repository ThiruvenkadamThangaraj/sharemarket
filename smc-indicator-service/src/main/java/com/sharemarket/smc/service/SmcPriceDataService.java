package com.sharemarket.smc.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sharemarket.smc.config.SmcMarketConfig;
import com.sharemarket.smc.model.Candle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmcPriceDataService {

    private static final String YAHOO_URL =
        "https://query1.finance.yahoo.com/v8/finance/chart/%s?interval=%s&range=%s";

    private final SmcMarketConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    public List<Candle> fetchCandles(String symbol) {
        String url = String.format(YAHOO_URL, symbol, config.getDataInterval(), config.getDataRange());
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (compatible; SmcReportBot/1.0)")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Yahoo HTTP {} for {}", response.statusCode(), symbol);
                return List.of();
            }
            return parse(symbol, response.body());
        } catch (Exception e) {
            log.warn("Failed to fetch {}: {}", symbol, e.getMessage());
            return List.of();
        }
    }

    private List<Candle> parse(String symbol, String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode result = root.path("chart").path("result");
        if (!result.isArray() || result.isEmpty()) {
            return List.of();
        }

        JsonNode chart = result.get(0);
        JsonNode timestamps = chart.path("timestamp");
        JsonNode quote = chart.path("indicators").path("quote");
        if (!timestamps.isArray() || quote.isEmpty()) {
            return List.of();
        }

        JsonNode q = quote.get(0);
        JsonNode opens = q.path("open");
        JsonNode highs = q.path("high");
        JsonNode lows = q.path("low");
        JsonNode closes = q.path("close");
        JsonNode volumes = q.path("volume");

        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < timestamps.size(); i++) {
            if (opens.get(i) == null || highs.get(i) == null || lows.get(i) == null || closes.get(i) == null
                || opens.get(i).isNull() || highs.get(i).isNull() || lows.get(i).isNull() || closes.get(i).isNull()) {
                continue;
            }

            candles.add(Candle.builder()
                .timestamp(timestamps.get(i).asLong())
                .open(opens.get(i).asDouble())
                .high(highs.get(i).asDouble())
                .low(lows.get(i).asDouble())
                .close(closes.get(i).asDouble())
                .volume(volumes.get(i) == null || volumes.get(i).isNull() ? 0.0 : volumes.get(i).asDouble())
                .build());
        }

        log.info("Fetched {} candles for {}", candles.size(), symbol);
        return candles;
    }
}
