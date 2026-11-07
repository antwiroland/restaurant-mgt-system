package com.restaurantmanager.core.notification;

import com.restaurantmanager.core.notification.whatsapp.MetaWhatsAppGatewayTestAccess;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NotificationGatewayTest {

    @Test
    void phoneNumbers_areNormalisedToInternationalFormat() {
        assertThat(PhoneNumbers.toInternational("024 412 3456", "233")).isEqualTo("233244123456");
        assertThat(PhoneNumbers.toInternational("+233244123456", "233")).isEqualTo("233244123456");
        assertThat(PhoneNumbers.toInternational("00233244123456", "233")).isEqualTo("233244123456");
    }

    @Test
    void metaGateway_sendsTextMessageToCloudApi() {
        NotificationProps props = new NotificationProps();
        props.getWhatsapp().setPhoneNumberId("PNID");
        RestClient.Builder builder = RestClient.builder().baseUrl("https://graph.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://graph.test/v20.0/PNID/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.to").value("233244123456"))
                .andExpect(jsonPath("$.type").value("text"))
                .andExpect(jsonPath("$.text.body").value("Order status: READY"))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.1\"}]}", MediaType.APPLICATION_JSON));

        MetaWhatsAppGatewayTestAccess.create(props, builder.build()).send("0244123456", "Order status: READY");

        server.verify();
    }

    @Test
    void metaGateway_usesTemplateWhenConfigured() {
        NotificationProps props = new NotificationProps();
        props.getWhatsapp().setPhoneNumberId("PNID");
        props.getWhatsapp().setTemplateName("order_update");
        RestClient.Builder builder = RestClient.builder().baseUrl("https://graph.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://graph.test/v20.0/PNID/messages"))
                .andExpect(jsonPath("$.type").value("template"))
                .andExpect(jsonPath("$.template.name").value("order_update"))
                .andExpect(jsonPath("$.template.components[0].parameters[0].text").value("hello"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        MetaWhatsAppGatewayTestAccess.create(props, builder.build()).send("233244123456", "hello");

        server.verify();
    }
}
