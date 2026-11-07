package com.restaurantmanager.core.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.config.PaymentProps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Live Paystack client (payments.paystack.mode=live).
 * Uses the Transaction Initialize / Verify and Refund APIs.
 */
public class HttpPaystackClient implements PaystackClient {
    private static final Logger log = LoggerFactory.getLogger(HttpPaystackClient.class);

    private final RestClient restClient;
    private final PaymentProps.Paystack props;

    public HttpPaystackClient(PaymentProps.Paystack props) {
        this.props = props;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(props.getConnectTimeoutMs());
        requestFactory.setReadTimeout(props.getReadTimeoutMs());
        this.restClient = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.getSecretKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    HttpPaystackClient(PaymentProps.Paystack props, RestClient restClient) {
        this.props = props;
        this.restClient = restClient;
    }

    @Override
    public InitiateResult initiate(String idempotencyKey, BigDecimal amount, String currency, String momoPhone) {
        String reference = "PAY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", customerEmail(momoPhone));
        body.put("amount", toMinorUnits(amount));
        body.put("currency", currency);
        body.put("reference", reference);
        body.put("channels", List.of("mobile_money", "card"));
        body.put("metadata", Map.of("idempotency_key", idempotencyKey, "momo_phone", momoPhone == null ? "" : momoPhone));
        if (props.getCallbackUrl() != null && !props.getCallbackUrl().isBlank()) {
            body.put("callback_url", props.getCallbackUrl());
        }

        JsonNode data = post("/transaction/initialize", body).path("data");
        return new InitiateResult(
                data.path("reference").asText(reference),
                data.path("authorization_url").asText(null),
                "Approve payment on your phone or complete checkout"
        );
    }

    @Override
    public VerifyResult verify(String reference) {
        JsonNode root = get("/transaction/verify/" + reference);
        JsonNode data = root.path("data");
        String status = data.path("status").asText("pending");
        String message = data.path("gateway_response").asText(root.path("message").asText(""));
        return new VerifyResult(status, message);
    }

    @Override
    public RefundResult refund(String reference, BigDecimal amount, String currency) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("transaction", reference);
        body.put("amount", toMinorUnits(amount));
        body.put("currency", currency);
        JsonNode root = post("/refund", body);
        JsonNode data = root.path("data");
        return new RefundResult(
                data.path("id").asText(null),
                data.path("status").asText("pending"),
                root.path("message").asText("Refund queued")
        );
    }

    private JsonNode post(String path, Object body) {
        try {
            JsonNode node = restClient.post().uri(path).body(body).retrieve().body(JsonNode.class);
            return requireOk(node, path);
        } catch (RestClientResponseException ex) {
            throw providerError(path, ex);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Paystack call {} failed: {}", path, ex.getMessage());
            throw new ApiException(502, "Payment provider unavailable");
        }
    }

    private JsonNode get(String path) {
        try {
            JsonNode node = restClient.get().uri(path).retrieve().body(JsonNode.class);
            return requireOk(node, path);
        } catch (RestClientResponseException ex) {
            throw providerError(path, ex);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Paystack call {} failed: {}", path, ex.getMessage());
            throw new ApiException(502, "Payment provider unavailable");
        }
    }

    private JsonNode requireOk(JsonNode node, String path) {
        if (node == null || !node.path("status").asBoolean(false)) {
            String message = node == null ? "Empty response" : node.path("message").asText("Request failed");
            log.warn("Paystack call {} rejected: {}", path, message);
            throw new ApiException(502, "Payment provider rejected request: " + message);
        }
        return node;
    }

    private ApiException providerError(String path, RestClientResponseException ex) {
        log.warn("Paystack call {} returned {}: {}", path, ex.getStatusCode().value(), ex.getResponseBodyAsString());
        return new ApiException(502, "Payment provider error (" + ex.getStatusCode().value() + ")");
    }

    private String customerEmail(String momoPhone) {
        String digits = momoPhone == null ? "" : momoPhone.replaceAll("[^0-9]", "");
        String local = digits.isBlank() ? "guest-" + UUID.randomUUID().toString().substring(0, 8) : "momo-" + digits;
        return local + "@" + props.getCustomerEmailDomain();
    }

    static long toMinorUnits(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }
}
