package com.restaurantmanager.core.payment;

import com.restaurantmanager.core.common.ApiException;
import com.restaurantmanager.core.config.PaymentProps;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpPaystackClientTest {
    private MockRestServiceServer server;
    private HttpPaystackClient client;

    @BeforeEach
    void setUp() {
        PaymentProps.Paystack props = new PaymentProps.Paystack();
        props.setSecretKey("sk_test_x");
        props.setBaseUrl("https://api.paystack.test");
        RestClient.Builder builder = RestClient.builder().baseUrl(props.getBaseUrl());
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpPaystackClient(props, builder.build());
    }

    @Test
    void initiate_sendsAmountInPesewasAndReturnsAuthorizationUrl() {
        server.expect(requestTo("https://api.paystack.test/transaction/initialize"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.amount").value(2550))
                .andExpect(jsonPath("$.currency").value("GHS"))
                .andExpect(jsonPath("$.email").value("momo-0244123456@customers.restaurant.local"))
                .andRespond(withSuccess("""
                        {"status":true,"message":"ok","data":{"authorization_url":"https://checkout.paystack.com/abc","reference":"PAY-REF1"}}
                        """, MediaType.APPLICATION_JSON));

        PaystackClient.InitiateResult result = client.initiate("idem-1", new BigDecimal("25.50"), "GHS", "0244123456");

        assertThat(result.reference()).isEqualTo("PAY-REF1");
        assertThat(result.authorizationUrl()).isEqualTo("https://checkout.paystack.com/abc");
        server.verify();
    }

    @Test
    void verify_mapsProviderStatus() {
        server.expect(requestTo("https://api.paystack.test/transaction/verify/PAY-REF1"))
                .andRespond(withSuccess("""
                        {"status":true,"message":"Verification successful","data":{"status":"success","gateway_response":"Approved"}}
                        """, MediaType.APPLICATION_JSON));

        PaystackClient.VerifyResult result = client.verify("PAY-REF1");

        assertThat(result.providerStatus()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("Approved");
    }

    @Test
    void refund_postsTransactionReferenceAndPartialAmount() {
        server.expect(requestTo("https://api.paystack.test/refund"))
                .andExpect(jsonPath("$.transaction").value("PAY-REF1"))
                .andExpect(jsonPath("$.amount").value(1000))
                .andRespond(withSuccess("""
                        {"status":true,"message":"Refund has been queued for processing","data":{"id":123,"status":"pending"}}
                        """, MediaType.APPLICATION_JSON));

        PaystackClient.RefundResult result = client.refund("PAY-REF1", new BigDecimal("10.00"), "GHS");

        assertThat(result.providerStatus()).isEqualTo("pending");
        assertThat(result.failed()).isFalse();
    }

    @Test
    void providerErrorBecomes502() {
        server.expect(requestTo("https://api.paystack.test/transaction/verify/BAD"))
                .andRespond(withBadRequest().body("{\"status\":false,\"message\":\"Transaction reference not found\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.verify("BAD"))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(502);
    }
}
