package com.sharemarket.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sharemarket.model.OHLCData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches daily OHLCV bars from the Yahoo Finance v8 chart API.
 *
 * No API key required. The User-Agent header is set to avoid 403 responses.
 * Yahoo Finance does rate-limit aggressive callers; a 0.5–1 s sleep between
 * symbols is sufficient for a 10-symbol daily run.
 */
@Slf4j
@Service
public class PriceDataService {

    private static final String YAHOO_URL =
        "https://query1.finance.yahoo.com/v8/finance/chart/%s?interval=%s&range=%s";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    /**
     * Returns historical daily bars for the symbol, oldest-bar first.
     * Returns an empty list on any failure (logged as an error).
     *
     * @param symbol   e.g. "BTC-USD", "AAPL"
     * @param interval e.g. "1d"
     * @param range    e.g. "3mo"
     */
    public List<OHLCData> fetchOHLC(String symbol, String interval, String range) {
        String url = String.format(YAHOO_URL, symbol, interval, range);
        log.info("Fetching OHLC for {} (interval={}, range={})", symbol, interval, range);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                // Yahoo Finance returns 403 without a browser-like User-Agent
                .header("User-Agent", "Mozilla/5.0 (compatible; MarketSignalBot/1.0)")
                .header("Accept",     "application/json")
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

            HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Yahoo Finance HTTP {} for {}: {}",
                    response.statusCode(), symbol, response.body());
                return List.of();
            }

            List<OHLCData> bars = parseYahooResponse(symbol, response.body());
            log.info("Received {} bars for {}", bars.size(), symbol);
            return bars;

        } catch (Exception e) {
            log.error("Failed to fetch data for {}: {}", symbol, e.getMessage());
            return List.of();
        }
    }

    // ── JSON parsing ──────────────────────────────────────────────────────────

    private List<OHLCData> parseYahooResponse(String symbol, String json) throws Exception {
        JsonNode root   = objectMapper.readTree(json);
        JsonNode result = root.path("chart").path("result");

        if (result.isMissingNode() || !result.isArray() || result.isEmpty()) {
            log.warn("No chart result in Yahoo Finance response for {}", symbol);
            return List.of();
        }

        JsonNode chartResult = result.get(0);
        JsonNode timestamps  = chartResult.path("timestamp");
        JsonNode quote       = chartResult.path("indicators").path("quote");

        if (timestamps.isMissingNode() || quote.isEmpty()) {
            log.warn("Missing timestamps or quote data for {}", symbol);
            return List.of();
        }

        JsonNode q       = quote.get(0);
        JsonNode opens   = q.path("open");
        JsonNode highs   = q.path("high");
        JsonNode lows    = q.path("low");
        JsonNode closes  = q.path("close");
        JsonNode volumes = q.path("volume");

        List<OHLCData> bars = new ArrayList<>();

        for (int i = 0; i < timestamps.size(); i++) {
            // Yahoo Finance can return null entries for days with no trading
            if (closes.get(i) == null || closes.get(i).isNull()
                || opens.get(i).isNull() || highs.get(i).isNull()
                || lows.get(i).isNull()) {
                continue;
            }

            bars.add(OHLCData.builder()
                .symbol(symbol)
                .timestamp(timestamps.get(i).asLong())
                .open(opens.get(i).asDouble())
                .high(highs.get(i).asDouble())
                .low(lows.get(i).asDouble())
                .close(closes.get(i).asDouble())
                .volume(volumes.get(i) == null || volumes.get(i).isNull()
                    ? 0.0 : volumes.get(i).asDouble())
                .build());
        }

        return bars;
    }
}
