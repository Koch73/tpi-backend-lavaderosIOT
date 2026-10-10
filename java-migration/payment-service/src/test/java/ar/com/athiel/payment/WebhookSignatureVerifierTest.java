package ar.com.athiel.payment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

class WebhookSignatureVerifierTest {

    private static final String SECRET = "test-webhook-secret";

    private static MercadoPagoProperties props(String secret) {
        return new MercadoPagoProperties("t", "http://x", secret, "http://n", "http://b", Duration.ofMinutes(5));
    }

    private static String sign(String manifest) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void validSignatureIsAccepted() throws Exception {
        String v1 = sign("id:12345;request-id:req-1;ts:1700000000;");
        var verifier = new WebhookSignatureVerifier(props(SECRET));
        assertTrue(verifier.verify("ts=1700000000,v1=" + v1, "req-1", "12345"));
    }

    @Test
    void tamperedDataIdIsRejected() throws Exception {
        String v1 = sign("id:12345;request-id:req-1;ts:1700000000;");
        var verifier = new WebhookSignatureVerifier(props(SECRET));
        assertFalse(verifier.verify("ts=1700000000,v1=" + v1, "req-1", "99999"));
    }

    @Test
    void missingHeaderIsRejectedWhenSecretConfigured() {
        var verifier = new WebhookSignatureVerifier(props(SECRET));
        assertFalse(verifier.verify(null, "req-1", "12345"));
    }

    @Test
    void checkIsSkippedWhenNoSecretConfigured() {
        var verifier = new WebhookSignatureVerifier(props(""));
        assertTrue(verifier.verify(null, null, "12345"));
    }
}
