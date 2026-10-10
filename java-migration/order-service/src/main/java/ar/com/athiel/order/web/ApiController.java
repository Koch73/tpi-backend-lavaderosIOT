package ar.com.athiel.order.web;

import ar.com.athiel.order.client.DeviceCallException;
import ar.com.athiel.order.client.DeviceClient;
import ar.com.athiel.order.domain.RelayError;
import ar.com.athiel.order.domain.RelayErrorRepository;
import ar.com.athiel.order.domain.Settings;
import ar.com.athiel.order.domain.SettingsRepository;
import ar.com.athiel.order.domain.WashHistory;
import ar.com.athiel.order.domain.WashHistoryRepository;
import ar.com.athiel.order.service.ActivationFailedException;
import ar.com.athiel.order.service.NotFoundException;
import ar.com.athiel.order.service.OrderService;
import ar.com.athiel.order.service.ServiceCatalog;
import ar.com.athiel.order.service.SessionService;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The HTTP surface FastAPI exposed for orders, settings, boxes and reports,
 * with the same paths, parameters and JSON field names so the existing
 * frontend keeps working. Errors use {"detail": "..."} like FastAPI.
 */
@RestController
public class ApiController {

    public record SettingsUpdate(
            @JsonProperty("wash_price") BigDecimal washPrice,
            @JsonProperty("wash_minutes") Integer washMinutes,
            @JsonProperty("wash_seconds") Integer washSeconds) {
    }

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final OrderService orders;
    private final SessionService sessions;
    private final ServiceCatalog catalog;
    private final SettingsRepository settings;
    private final WashHistoryRepository history;
    private final RelayErrorRepository errors;
    private final DeviceClient devices;

    public ApiController(OrderService orders, SessionService sessions, ServiceCatalog catalog,
                         SettingsRepository settings, WashHistoryRepository history, RelayErrorRepository errors,
                         DeviceClient devices) {
        this.devices = devices;
        this.orders = orders;
        this.sessions = sessions;
        this.catalog = catalog;
        this.settings = settings;
        this.history = history;
        this.errors = errors;
    }

    // --------------------------------------------------------------- payments

    @PostMapping("/create_preference")
    public Map<String, String> createPreference() {
        OrderService.Checkout checkout = orders.createOrder(ServiceCatalog.WASH);
        Map<String, String> body = new LinkedHashMap<>();
        body.put("checkout_url", checkout.checkoutUrl());
        body.put("order_id", checkout.orderId());
        return body;
    }

    @PostMapping("/payments/manual")
    public Map<String, String> manualPayment() {
        try {
            orders.registerManualPayment();
        } catch (ActivationFailedException e) {
            throw new BadGatewayException("No se pudo iniciar el lavado: " + e.getMessage());
        }
        return Map.of("message", "Pago manual registrado, lavado iniciado");
    }

    // --------------------------------------------------------------- settings

    @GetMapping("/settings")
    public Map<String, Object> getSettings() {
        Settings s = currentSettings();
        return settingsBody(s);
    }

    @PutMapping("/settings")
    public Map<String, String> updateSettings(@RequestBody SettingsUpdate update) {
        if (update.washPrice() == null || update.washMinutes() == null || update.washSeconds() == null
                || update.washPrice().signum() < 0 || update.washMinutes() < 0 || update.washSeconds() < 0) {
            throw new IllegalArgumentException("wash_price, wash_minutes y wash_seconds deben ser >= 0");
        }
        Settings s = currentSettings();
        s.update(update.washPrice(), update.washMinutes(), update.washSeconds());
        settings.save(s);
        return Map.of("message", "Configuración actualizada");
    }

    private Settings currentSettings() {
        return settings.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new NotFoundException("Configuración inexistente"));
    }

    private static Map<String, Object> settingsBody(Settings s) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("wash_price", s.getWashPrice());
        body.put("wash_minutes", s.getWashMinutes());
        body.put("wash_seconds", s.getWashSeconds());
        return body;
    }

    // ------------------------------------------------------------------ boxes

    @PostMapping("/boxes/{boxName}")
    public Map<String, Object> controlBox(@PathVariable String boxName, @RequestParam String action) {
        requireBox(boxName);
        if (!"stop".equals(action)) {
            throw new IllegalArgumentException("Unknown action: " + action);
        }
        try {
            sessions.stopNow(boxName);
        } catch (DeviceCallException e) {
            errors.save(new RelayError(boxName, "turn_off", e.getMessage()));
            throw new BadGatewayException(e.getMessage());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("action", "stop");
        body.put("state", "stopped");
        return body;
    }

    /** Read-only: unlike the Python version, a GET never stops the relay. */
    @GetMapping("/boxes/time_left")
    public Map<String, Object> timeLeft(@RequestParam("box_name") String boxName) {
        requireBox(boxName);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("state", sessions.isRunning(boxName) ? "started" : "stopped");
        body.put("seconds_left", sessions.secondsLeft(boxName));
        return body;
    }

    @GetMapping("/boxes/relay_status")
    public Map<String, String> relayStatus(@RequestParam("box_name") String boxName) {
        requireBox(boxName);
        try {
            String state = devices.status(boxName);
            return Map.of("state", "ON".equals(state) ? "started" : "stopped");
        } catch (DeviceCallException e) {
            errors.save(new RelayError(boxName, "relay_status", e.getMessage()));
            throw new BadGatewayException(e.getMessage());
        }
    }

    private void requireBox(String boxName) {
        if (!catalog.deviceIds().contains(boxName)) {
            errors.save(new RelayError(boxName, "box_lookup", "Box '" + boxName + "' not found"));
            throw new NotFoundException("Box '" + boxName + "' not found");
        }
    }

    // ---------------------------------------------------------------- reports

    @GetMapping("/reports/monthly")
    public Map<String, List<Map<String, Object>>> monthly(@RequestParam int year, @RequestParam int month) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Mes inválido");
        }
        java.time.YearMonth ym = java.time.YearMonth.of(year, month);
        String from = ym.atDay(1).toString();
        String to = ym.atEndOfMonth().toString();

        Map<String, List<Map<String, Object>>> report = new LinkedHashMap<>();
        for (WashHistory h : history.findByWashDateBetweenOrderByWashDateAscCreatedAtAsc(from, to)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", h.getId());
            row.put("boxId", h.getRelayName());
            row.put("time", h.getCreatedAt() == null ? null
                    : HH_MM.format(h.getCreatedAt().atZone(SessionService.ARGENTINA)));
            row.put("amount", h.getPrice());
            row.put("paymentMethod", h.getPaymentMethod());
            report.computeIfAbsent(h.getWashDate(), k -> new ArrayList<>()).add(row);
        }
        return report;
    }

    // ---------------------------------------------------------------- errors

    static class BadGatewayException extends RuntimeException {
        BadGatewayException(String message) {
            super(message);
        }
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<Map<String, String>> notFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("detail", e.getMessage()));
    }

    @ExceptionHandler(BadGatewayException.class)
    ResponseEntity<Map<String, String>> badGateway(BadGatewayException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("detail", e.getMessage()));
    }

    @ExceptionHandler(java.time.DateTimeException.class)
    ResponseEntity<Map<String, String>> badDate(java.time.DateTimeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("detail", "Fecha inválida"));
    }
}
