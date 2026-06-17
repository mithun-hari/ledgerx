package com.ledgerx.wallet_service.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class FxServiceClient {

    @Value("${fx.service.url}")
    private String fxServiceUrl;

    // WebClient is Spring's modern HTTP client
    private final WebClient.Builder webClientBuilder;

    public BigDecimal getExchangeRate(String fromCurrency, String toCurrency) {

        // Same currency — rate is always 1
        if (fromCurrency.equalsIgnoreCase(toCurrency)) {
            return BigDecimal.ONE;
        }

        // Call FX service GET /rates/{from}/{to}
        Map response = webClientBuilder
                .baseUrl(fxServiceUrl)
                .build()
                .get()
                .uri("/rates/{from}/{to}", fromCurrency, toCurrency)
                .retrieve()
                .bodyToMono(Map.class)
                .block(); // block() waits for response

        if (response == null || !response.containsKey("rate")) {
            throw new RuntimeException(
                    "Could not fetch rate for " + fromCurrency + " → " + toCurrency
            );
        }

        return new BigDecimal(response.get("rate").toString());
    }
}