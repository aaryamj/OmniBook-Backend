package com.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Currency Exchange Service
 * Provides dynamic and resilient NPR-to-USD conversion rates.
 * Single source-of-truth base currency for OmniBook is NPR.
 */
@Slf4j
@Service
public class CurrencyExchangeService {

    // Standard baseline rate (1 USD = 135.00 NPR)
    public static final double DEFAULT_NPR_PER_USD = 135.00;

    // Cache holding the latest verified rate
    private volatile double cachedNprPerUsd = DEFAULT_NPR_PER_USD;
    private volatile LocalDateTime lastFetchedAt = null;
    private volatile String rateSource = "OmniBook Institutional Baseline";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    /**
     * Retrieves the current NPR per 1 USD exchange rate (e.g. 135.0).
     * Refreshes every hour if live external rates are reachable; otherwise falls back safely.
     */
    public synchronized double getNprToUsdRate() {
        if (lastFetchedAt != null && lastFetchedAt.isAfter(LocalDateTime.now().minusHours(1))) {
            return cachedNprPerUsd;
        }

        try {
            // Attempt to fetch from public Forex API (Open Exchange Rates / ER-API)
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://open.er-api.com/v6/latest/USD"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null) {
                // Parse "NPR": 136.25 using regex to avoid extra dependencies
                Matcher matcher = Pattern.compile("\"NPR\"\\s*:\\s*([0-9]+(\\.[0-9]+)?)").matcher(resp.body());
                if (matcher.find()) {
                    double liveRate = Double.parseDouble(matcher.group(1));
                    if (liveRate >= 100.0 && liveRate <= 200.0) {
                        cachedNprPerUsd = Math.round(liveRate * 100.0) / 100.0;
                        lastFetchedAt = LocalDateTime.now();
                        rateSource = "Live Forex Market Rate (Open Exchange API)";
                        log.info("Updated NPR/USD exchange rate from live market: 1 USD = {} NPR", cachedNprPerUsd);
                        return cachedNprPerUsd;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not fetch live NPR/USD exchange rate ({}), utilizing fallback rate {}", e.getMessage(), cachedNprPerUsd);
        }

        // If fetch failed or network offline, keep cached/default rate and mark timestamp
        if (lastFetchedAt == null) {
            lastFetchedAt = LocalDateTime.now();
        }
        return cachedNprPerUsd;
    }

    /**
     * Converts an amount in NPR to USD.
     * Enforces Stripe's minimum threshold ($0.50 USD).
     */
    public double convertNprToUsd(double amountNpr, double nprPerUsdRate) {
        if (amountNpr <= 0) return 0.0;
        double rate = nprPerUsdRate > 0 ? nprPerUsdRate : DEFAULT_NPR_PER_USD;
        double usd = amountNpr / rate;
        double rounded = Math.round(usd * 100.0) / 100.0;
        // Stripe requires a minimum charge of $0.50 USD
        return Math.max(0.50, rounded);
    }

    /**
     * Converts an amount in USD to NPR.
     */
    public double convertUsdToNpr(double amountUsd, double nprPerUsdRate) {
        if (amountUsd <= 0) return 0.0;
        double rate = nprPerUsdRate > 0 ? nprPerUsdRate : DEFAULT_NPR_PER_USD;
        return Math.round((amountUsd * rate) * 100.0) / 100.0;
    }

    /**
     * Returns full diagnostic and rate information.
     */
    public Map<String, Object> getRateDetails() {
        double rate = getNprToUsdRate();
        Map<String, Object> details = new HashMap<>();
        details.put("baseCurrency", "NPR");
        details.put("targetCurrency", "USD");
        details.put("nprPerUsd", rate);
        details.put("usdPerNpr", Math.round((1.0 / rate) * 10000.0) / 10000.0);
        details.put("source", rateSource);
        details.put("lastUpdated", lastFetchedAt != null ? lastFetchedAt.toString() : LocalDateTime.now().toString());
        return details;
    }
}
