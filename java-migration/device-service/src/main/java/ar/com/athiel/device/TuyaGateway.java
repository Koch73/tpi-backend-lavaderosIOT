package ar.com.athiel.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Port of {@code tuya/tuya_api.py}: token cache, signed command POST to
 * {@code /v1.0/iot-03/devices/{id}/commands} (code {@code switch_1}) and
 * status GET. Kept as the legacy vendor behind {@link DeviceGateway}.
 */
@Component
public class TuyaGateway implements DeviceGateway {

    private static final String SWITCH_CODE = "switch_1";

    private final RestClient http;
    private final TuyaProperties props;
    private final TuyaSigner signer;
    private final ObjectMapper mapper;

    private String accessToken;
    private long tokenExpiresAtEpochSec;

    public TuyaGateway(RestClient.Builder builder, TuyaProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.signer = new TuyaSigner(props.clientId(), props.clientSecret());
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());
        this.http = builder.requestFactory(factory)
                .baseUrl(props.baseUrl() == null ? "" : props.baseUrl()).build();
    }

    @Override
    public Vendor vendor() {
        return Vendor.TUYA;
    }

    @Override
    public void setPower(DeviceProperties.Device device, boolean on) {
        String path = "/v1.0/iot-03/devices/" + device.externalId() + "/commands";
        String body;
        try {
            body = mapper.writeValueAsString(
                    Map.of("commands", List.of(Map.of("code", SWITCH_CODE, "value", on))));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        JsonNode data = call("POST", path, body);
        if (!data.path("success").asBoolean(false) || data.path("result").isBoolean() && !data.path("result").asBoolean()) {
            throw new DeviceUnavailableException(
                    "Tuya rejected command for " + device.id() + ": device offline or Tuya cloud down");
        }
    }

    @Override
    public DeviceStatus readPower(DeviceProperties.Device device) {
        JsonNode data = call("GET", "/v1.0/iot-03/devices/" + device.externalId() + "/status", "");
        if (!data.path("success").asBoolean(false)) {
            throw new DeviceUnavailableException("Tuya status failed for " + device.id());
        }
        for (JsonNode item : data.path("result")) {
            if (SWITCH_CODE.equals(item.path("code").asText())) {
                return item.path("value").asBoolean() ? DeviceStatus.ON : DeviceStatus.OFF;
            }
        }
        return DeviceStatus.UNKNOWN;
    }

    private JsonNode call(String method, String path, String body) {
        try {
            String token = token();
            String t = String.valueOf(System.currentTimeMillis());
            String sign = signer.signWithToken(method, path, token, body, t);

            RestClient.RequestBodySpec spec = http.method(org.springframework.http.HttpMethod.valueOf(method))
                    .uri(path)
                    .header("client_id", props.clientId())
                    .header("access_token", token)
                    .header("t", t)
                    .header("sign_method", "HMAC-SHA256")
                    .header("sign", sign)
                    .contentType(MediaType.APPLICATION_JSON);
            if (!body.isEmpty()) {
                spec.body(body);
            }
            JsonNode response = spec.retrieve().body(JsonNode.class);
            if (response == null) {
                throw new DeviceUnavailableException("Empty Tuya response");
            }
            return response;
        } catch (RestClientException e) {
            throw new DeviceUnavailableException("Tuya request failed: " + e.getMessage(), e);
        }
    }

    private synchronized String token() {
        long now = System.currentTimeMillis() / 1000;
        if (accessToken != null && now < tokenExpiresAtEpochSec) {
            return accessToken;
        }
        String path = "/v1.0/token?grant_type=1";
        String t = String.valueOf(System.currentTimeMillis());
        JsonNode data = http.get()
                .uri(path)
                .header("client_id", props.clientId())
                .header("t", t)
                .header("sign_method", "HMAC-SHA256")
                .header("sign", signer.signForToken("GET", path, "", t))
                .retrieve()
                .body(JsonNode.class);
        if (data == null || !data.path("success").asBoolean(false)) {
            throw new DeviceUnavailableException("Tuya token request failed");
        }
        accessToken = data.path("result").path("access_token").asText();
        tokenExpiresAtEpochSec = now + data.path("result").path("expire_time").asLong() - 60;
        return accessToken;
    }
}
