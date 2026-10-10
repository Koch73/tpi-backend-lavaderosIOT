package ar.com.athiel.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Validates the {@code x-signature} header Mercado Pago adds to webhooks.
 * The signed manifest is {@code id:<data.id>;request-id:<x-request-id>;ts:<ts>;}
 * and the signature is HMAC-SHA256 with the webhook secret.
 *
 * The Python service did not do this check. When no secret is configured the
 * check is skipped (with a warning) so the migration can be run before the
 * secret is available; configure it before going live.
 */
@Component
public class WebhookSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(WebhookSignatureVerifier.class);

    private final String secret;

    public WebhookSignatureVerifier(MercadoPagoProperties props) {
        this.secret = props.webhookSecret();
    }

    public boolean verify(String signatureHeader, String requestId, String dataId) {
        if (secret == null || secret.isBlank()) {
            log.warn("MP_WEBHOOK_SECRET is not set: webhook signature NOT verified");
            return true;
        }
        if (signatureHeader == null || dataId == null) {
            return false;
        }

        String ts = null;
        String v1 = null;
        for (String part : signatureHeader.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2) {
                if (kv[0].equals("ts")) {
                    ts = kv[1];
                } else if (kv[0].equals("v1")) {
                    v1 = kv[1];
                }
            }
        }
        if (ts == null || v1 == null) {
            return false;
        }

        String manifest = "id:" + dataId.toLowerCase() + ";"
                + (requestId != null ? "request-id:" + requestId + ";" : "")
                + "ts:" + ts + ";";
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8), v1.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot compute HMAC", e);
        }
    }
}
