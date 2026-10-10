package ar.com.athiel.order.client;

import ar.com.athiel.common.DeviceCommandResponse;
import ar.com.athiel.order.OrderProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

@Component
public class HttpDeviceClient implements DeviceClient {

    private final RestClient http;

    public HttpDeviceClient(RestClient.Builder builder, OrderProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());
        // device-service may retry a command several times before answering
        factory.setReadTimeout((int) Duration.ofSeconds(40).toMillis());
        this.http = builder.requestFactory(factory).baseUrl(props.deviceServiceUrl()).build();
    }

    @Override
    public DeviceCommandResponse on(String deviceId, String idempotencyKey) {
        return command("on", deviceId, idempotencyKey);
    }

    @Override
    public DeviceCommandResponse off(String deviceId, String idempotencyKey) {
        return command("off", deviceId, idempotencyKey);
    }

    @Override
    public String status(String deviceId) {
        try {
            JsonNode body = http.get().uri("/devices/{id}/status", deviceId).retrieve().body(JsonNode.class);
            return body == null ? "UNKNOWN" : body.path("state").asText("UNKNOWN");
        } catch (RestClientException e) {
            throw new DeviceCallException("device-service status failed: " + e.getMessage(), e);
        }
    }

    private DeviceCommandResponse command(String action, String deviceId, String key) {
        try {
            RestClient.RequestBodySpec spec = http.post().uri("/devices/{id}/" + action, deviceId);
            if (key != null) {
                spec.header("Idempotency-Key", key);
            }
            DeviceCommandResponse r = spec.retrieve().body(DeviceCommandResponse.class);
            if (r == null) {
                throw new DeviceCallException("Empty response from device-service", null);
            }
            return r;
        } catch (RestClientException e) {
            throw new DeviceCallException("device-service " + action + " failed: " + e.getMessage(), e);
        }
    }
}
