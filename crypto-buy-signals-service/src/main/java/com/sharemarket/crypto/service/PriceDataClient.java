package com.sharemarket.crypto.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sharemarket.crypto.model.Candle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class PriceDataClient {

    private static final Logger log = LoggerFactory.getLogger(PriceDataClient.class);
    private static final String YAHOO_URL =
        "https://query1.finance.yahoo.com/v8/finance/chart/%s?interval=%s&range=%s";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    public List<Candle> fetchCandles(String symbol, String interval, String range) {
        String url = String.format(YAHOO_URL, symbol, interval, range);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (compatible; CryptoSignalBot/1.0)")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Yahoo Finance HTTP {} for {}", response.statusCode(), symbol);
                return List.of();
            }

            return parseResponse(symbol, response.body());
        } catch (Exception ex) {
            log.warn("Failed to fetch data for {}: {}", symbol, ex.getMessage());
            return List.of();
        }
    }

    private List<Candle> parseResponse(String symbol, String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode result = root.path("chart").path("result");

        if (!result.isArray() || result.isEmpty()) {
            return List.of();
        }

        JsonNode chartResult = result.get(0);
        JsonNode timestamps = chartResult.path("timestamp");
        JsonNode quote = chartResult.path("indicators").path("quote");

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

            double volume = 0.0;
            if (volumes.get(i) != null && !volumes.get(i).isNull()) {
                volume = volumes.get(i).asDouble();
            }

            candles.add(new Candle(
                symbol,
                timestamps.get(i).asLong(),
                opens.get(i).asDouble(),
                highs.get(i).asDouble(),
                lows.get(i).asDouble(),
                closes.get(i).asDouble(),
                volume
            ));
        }

        return candles;
    }
}
