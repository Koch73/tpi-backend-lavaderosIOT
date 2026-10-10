package ar.com.athiel.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param accessToken       Mercado Pago access token (env MP_ACCESS_TOKEN), never logged
 * @param baseUrl           Mercado Pago REST base URL
 * @param webhookSecret     secret used to verify x-signature; when blank verification is skipped
 * @param notificationUrl   public URL Mercado Pago calls (WEBHOOK_URL + /webhook)
 * @param backUrl           where the customer returns after paying (BASE_URL)
 * @param approvalWindow    a payment older than this is not allowed to start a wash
 */
@ConfigurationProperties(prefix = "athiel.mercadopago")
public record MercadoPagoProperties(
        String accessToken,
        String baseUrl,
        String webhookSecret,
        String notificationUrl,
        String backUrl,
        Duration approvalWindow) {
}
