package ar.com.athiel.payment;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Thin client over the Mercado Pago REST API, covering the two calls the
 * Python code made through the SDK: {@code preference().create} and
 * {@code payment().get}.
 */
@Component
public class MercadoPagoClient {

    private final RestClient http;
    private final MercadoPagoProperties props;

    public MercadoPagoClient(RestClient.Builder builder, MercadoPagoProperties props) {
        this.props = props;
        this.http = builder
                .baseUrl(props.baseUrl())
                .defaultHeader("Authorization", "Bearer " + props.accessToken())
                .build();
    }

    /** @return the {@code init_point} the customer must open to pay */
    public String createPreference(String orderId, String title, BigDecimal amount) {
        Map<String, Object> body = Map.of(
                "items", List.of(Map.of(
                        "title", title,
                        "quantity", 1,
                        "unit_price", amount)),
                "external_reference", orderId,
                "notification_url", props.notificationUrl(),
                "back_urls", Map.of(
                        "success", props.backUrl(),
                        "failure", props.backUrl(),
                        "pending", props.backUrl()),
                "auto_return", "approved");

        JsonNode response = http.post()
                .uri("/checkout/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (response == null || !response.hasNonNull("init_point")) {
            throw new IllegalStateException("Mercado Pago did not return init_point");
        }
        return response.get("init_point").asText();
    }

    public PaymentInfo getPayment(String paymentId) {
        JsonNode p = http.get()
                .uri("/v1/payments/{id}", paymentId)
                .retrieve()
                .body(JsonNode.class);

        if (p == null) {
            throw new IllegalStateException("Empty payment response for " + paymentId);
        }
        return new PaymentInfo(
                paymentId,
                text(p, "status"),
                text(p, "external_reference"),
                p.hasNonNull("transaction_amount") ? p.get("transaction_amount").decimalValue() : null,
                p.hasNonNull("date_created") ? OffsetDateTime.parse(p.get("date_created").asText()) : null);
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
