package ar.com.athiel.order.client;

import ar.com.athiel.common.CheckoutRequest;
import ar.com.athiel.common.CheckoutResponse;
import ar.com.athiel.order.OrderProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@Component
public class HttpPaymentClient implements PaymentClient {

    private final RestClient http;

    public HttpPaymentClient(RestClient.Builder builder, OrderProperties props) {
        this.http = builder.baseUrl(props.paymentServiceUrl()).build();
    }

    @Override
    public String createCheckout(String orderId, String title, BigDecimal amount) {
        CheckoutResponse response = http.post()
                .uri("/checkouts")
                .body(new CheckoutRequest(orderId, title, amount))
                .retrieve()
                .body(CheckoutResponse.class);
        if (response == null || response.checkoutUrl() == null) {
            throw new IllegalStateException("payment-service returned no checkout URL");
        }
        return response.checkoutUrl();
    }
}
