package ar.com.athiel.payment;

import ar.com.athiel.common.CheckoutRequest;
import ar.com.athiel.common.CheckoutResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class PaymentController {

    private final MercadoPagoClient mercadoPago;
    private final PaymentService payments;
    private final WebhookSignatureVerifier signature;

    public PaymentController(MercadoPagoClient mercadoPago, PaymentService payments,
                             WebhookSignatureVerifier signature) {
        this.mercadoPago = mercadoPago;
        this.payments = payments;
        this.signature = signature;
    }

    /** Internal: called by order-service. Not routed by the gateway. */
    @PostMapping("/checkouts")
    public CheckoutResponse createCheckout(@RequestBody CheckoutRequest request) {
        return new CheckoutResponse(
                mercadoPago.createPreference(request.orderId(), request.title(), request.amount()));
    }

    /**
     * Same path as the FastAPI route. Mercado Pago sends the payment id in the
     * body ({@code data.id}) and, for signed notifications, as the
     * {@code data.id} query parameter.
     */
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> webhook(
            @RequestBody(required = false) JsonNode body,
            @RequestParam(name = "data.id", required = false) String queryDataId,
            @RequestHeader(name = "x-signature", required = false) String xSignature,
            @RequestHeader(name = "x-request-id", required = false) String xRequestId) {

        String dataId = queryDataId;
        if (dataId == null && body != null && body.path("data").hasNonNull("id")) {
            dataId = body.path("data").get("id").asText();
        }

        if (!signature.verify(xSignature, xRequestId, dataId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("detail", "Invalid signature"));
        }

        if (dataId != null) {
            payments.handleNotification(dataId);
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/success")
    public Map<String, String> success() {
        return Map.of("message", "Pago aprobado");
    }

    @GetMapping("/failure")
    public Map<String, String> failure() {
        return Map.of("message", "Pago rechazado");
    }

    @GetMapping("/pending")
    public Map<String, String> pending() {
        return Map.of("message", "Pago pendiente");
    }
}
