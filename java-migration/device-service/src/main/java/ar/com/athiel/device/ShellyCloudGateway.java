package ar.com.athiel.device;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.http.client.SimpleClientHttpRequestFactory;

/**
 * Port of {@code shelly_api.py}. Differences: nothing runs at import time, the
 * state is read per channel, and failures are raised as
 * {@link DeviceUnavailableException} instead of leaking {@code requests} errors.
 */
@Component
public class ShellyCloudGateway implements DeviceGateway {

    private final RestClient http;
    private final ShellyProperties props;

    public ShellyCloudGateway(RestClient.Builder builder, ShellyProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());
        this.http = builder.requestFactory(factory).baseUrl(props.baseUrl() == null ? "" : props.baseUrl()).build();
    }

    @Override
    public Vendor vendor() {
        return Vendor.SHELLY_CLOUD;
    }

    @Override
    public void setPower(DeviceProperties.Device device, boolean on) {
        try {
            http.post()
                    .uri(uri -> uri.path("/set/switch").queryParam("auth_key", props.authKey()).build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("id", device.externalId(), "channel", device.channel(), "on", on))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new DeviceUnavailableException("Shelly set/switch failed for " + device.id(), e);
        }
    }

    @Override
    public DeviceStatus readPower(DeviceProperties.Device device) {
        try {
            JsonNode response = http.post()
                    .uri(uri -> uri.path("/get").queryParam("auth_key", props.authKey()).build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("ids", List.of(device.externalId()), "select", List.of("status")))
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode output = response == null ? null
                    : response.path(0).path("status").path("switch:" + device.channel()).path("output");
            if (output == null || output.isMissingNode() || output.isNull()) {
                return DeviceStatus.UNKNOWN;
            }
            return output.asBoolean() ? DeviceStatus.ON : DeviceStatus.OFF;
        } catch (RestClientException e) {
            throw new DeviceUnavailableException("Shelly get status failed for " + device.id(), e);
        }
    }
}
