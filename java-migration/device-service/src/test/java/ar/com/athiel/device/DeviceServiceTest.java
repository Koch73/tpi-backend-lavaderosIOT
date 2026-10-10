package ar.com.athiel.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.com.athiel.common.DeviceCommandResponse;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
class DeviceServiceTest {

    /** Scripted gateway standing in for Shelly. */
    static class FakeGateway implements DeviceGateway {
        boolean reachable = true;
        boolean obeys = true;
        DeviceStatus state = DeviceStatus.OFF;
        final List<Boolean> sent = new ArrayList<>();

        @Override public Vendor vendor() { return Vendor.SHELLY_CLOUD; }

        @Override public void setPower(DeviceProperties.Device d, boolean on) {
            if (!reachable) throw new DeviceUnavailableException("offline");
            sent.add(on);
            if (obeys) state = on ? DeviceStatus.ON : DeviceStatus.OFF;
        }

        @Override public DeviceStatus readPower(DeviceProperties.Device d) {
            if (!reachable) throw new DeviceUnavailableException("offline");
            return state;
        }
    }

    @TestConfiguration
    static class Config {
        // same bean name as the real component so it replaces it (one gateway per vendor)
        @Bean(name = "shellyCloudGateway") FakeGateway fakeGateway() { return new FakeGateway(); }
    }

    @Autowired DeviceService service;
    @Autowired FakeGateway gateway;

    private void reset() {
        gateway.reachable = true;
        gateway.obeys = true;
        gateway.state = DeviceStatus.OFF;
        gateway.sent.clear();
    }

    @Test
    void turnOnIsConfirmedByReadBack() {
        reset();
        DeviceCommandResponse r = service.turnOn("box1", "s-1");
        assertTrue(r.confirmed());
        assertEquals("ON", r.state());
    }

    @Test
    void sameIdempotencyKeyDoesNotSendTwice() {
        reset();
        service.turnOn("box1", "s-2");
        service.turnOn("box1", "s-2");
        assertEquals(1, gateway.sent.size());
    }

    @Test
    void unreachableDeviceRaisesAfterRetries() {
        reset();
        gateway.reachable = false;
        assertThrows(DeviceUnavailableException.class, () -> service.turnOn("box1", "s-3"));
    }

    @Test
    void acceptedButNotObeyedIsReportedUnconfirmed() {
        reset();
        gateway.obeys = false;
        DeviceCommandResponse r = service.turnOn("box1", "s-4");
        assertFalse(r.confirmed());
        assertEquals("UNKNOWN", r.state());
        assertEquals(2, gateway.sent.size()); // two attempts configured in test profile
    }

    @Test
    void unknownDeviceIsRefused() {
        assertThrows(UnknownDeviceException.class, () -> service.turnOn("nope", "k"));
    }
}
