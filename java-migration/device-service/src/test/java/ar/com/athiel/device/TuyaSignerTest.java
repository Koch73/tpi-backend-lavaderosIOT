package ar.com.athiel.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TuyaSignerTest {

    @Test
    void signatureIsUpperCaseHexAndDeterministic() {
        TuyaSigner signer = new TuyaSigner("client", "secret");
        String a = signer.signWithToken("POST", "/v1.0/iot-03/devices/d/commands", "tok", "{}", "1700000000000");
        String b = signer.signWithToken("POST", "/v1.0/iot-03/devices/d/commands", "tok", "{}", "1700000000000");
        assertEquals(a, b);
        assertEquals(64, a.length());
        assertTrue(a.equals(a.toUpperCase()));
    }

    @Test
    void tokenSignatureDiffersFromAuthenticatedSignature() {
        TuyaSigner signer = new TuyaSigner("client", "secret");
        String t = "1700000000000";
        assertTrue(!signer.signForToken("GET", "/v1.0/token?grant_type=1", "", t)
                .equals(signer.signWithToken("GET", "/v1.0/token?grant_type=1", "tok", "", t)));
    }
}
