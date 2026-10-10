package ar.com.athiel.device;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Port of the Python {@code TuyaSigner}. Produces the HMAC-SHA256 signature
 * (upper-case hex) Tuya OpenAPI v1 requires.
 *
 * <pre>
 * stringToSign = METHOD \n sha256(body) \n headers \n url
 * token request : sign(clientId + t + stringToSign)
 * other request : sign(clientId + accessToken + t + stringToSign)
 * </pre>
 */
public class TuyaSigner {

    private final String clientId;
    private final String secret;

    public TuyaSigner(String clientId, String secret) {
        this.clientId = clientId;
        this.secret = secret;
    }

    public String signForToken(String method, String url, String body, String t) {
        return hmac(clientId + t + stringToSign(method, url, body));
    }

    public String signWithToken(String method, String url, String accessToken, String body, String t) {
        return hmac(clientId + accessToken + t + stringToSign(method, url, body));
    }

    private static String stringToSign(String method, String url, String body) {
        return method + "\n" + sha256(body == null ? "" : body) + "\n" + "" + "\n" + url;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String hmac(String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)))
                    .toUpperCase(Locale.ROOT);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
