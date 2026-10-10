package ar.com.athiel.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.athiel.common.DeviceCommandResponse;
import ar.com.athiel.common.PaymentApprovedEvent;
import ar.com.athiel.order.client.DeviceCallException;
import ar.com.athiel.order.client.DeviceClient;
import ar.com.athiel.order.client.PaymentClient;
import ar.com.athiel.order.domain.Order;
import ar.com.athiel.order.domain.OrderRepository;
import ar.com.athiel.order.domain.OrderStatus;
import ar.com.athiel.order.domain.RelayErrorRepository;
import ar.com.athiel.order.domain.SessionStatus;
import ar.com.athiel.order.domain.WashHistoryRepository;
import ar.com.athiel.order.domain.WashSession;
import ar.com.athiel.order.domain.WashSessionRepository;
import ar.com.athiel.order.service.ActivationFailedException;
import ar.com.athiel.order.service.OrderService;
import ar.com.athiel.order.service.SessionService;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class SessionFlowTest {

    @Autowired OrderService orderService;
    @Autowired SessionService sessionService;
    @Autowired OrderRepository orders;
    @Autowired WashSessionRepository sessions;
    @Autowired WashHistoryRepository history;
    @Autowired RelayErrorRepository errors;

    @MockitoBean DeviceClient devices;
    @MockitoBean PaymentClient payments;

    @BeforeEach
    void clean() {
        reset(devices, payments);
        history.deleteAll();
        sessions.deleteAll();
        orders.deleteAll();
        errors.deleteAll();
        when(payments.createCheckout(anyString(), anyString(), any())).thenReturn("https://mp/checkout");
        when(devices.on(anyString(), any())).thenReturn(new DeviceCommandResponse("box1", "ON", true));
        when(devices.off(anyString(), any())).thenReturn(new DeviceCommandResponse("box1", "OFF", true));
    }

    private String newOrder() {
        return orderService.createOrder("WASH").orderId();
    }

    private PaymentApprovedEvent paid(String orderId, String paymentId) {
        Order o = orders.findById(orderId).orElseThrow();
        return new PaymentApprovedEvent(orderId, paymentId, o.getAmount());
    }

    private WashSession onlySession() {
        assertEquals(1, sessions.count());
        return sessions.findAll().get(0);
    }

    @Test
    void paidOrderStartsThenExpiryCompletesAndWritesHistory() {
        String orderId = newOrder();
        orderService.onPaymentApproved(paid(orderId, "p1"));

        WashSession s = onlySession();
        assertEquals(SessionStatus.RUNNING, s.getStatus());
        assertNotNull(s.getExpiresAt());
        assertTrue(sessionService.secondsLeft("box1") > 0);
        verify(devices).on("box1", s.getId());

        // let the time pass: move expiry into the past and run one timer tick
        s.setExpiresAt(Instant.now().minusSeconds(1));
        sessions.save(s);
        sessionService.expireDue();

        assertEquals(SessionStatus.COMPLETED, sessions.findById(s.getId()).orElseThrow().getStatus());
        assertEquals(1, history.count());
        assertEquals(0.0, sessionService.secondsLeft("box1"));
    }

    @Test
    void duplicatePaymentEventStartsOnlyOnce() {
        String orderId = newOrder();
        PaymentApprovedEvent event = paid(orderId, "p1");
        orderService.onPaymentApproved(event);
        orderService.onPaymentApproved(event);

        verify(devices, times(1)).on(anyString(), any());
        assertEquals(1, sessions.count());
    }

    @Test
    void wrongAmountDoesNotStartTheWasher() {
        String orderId = newOrder();
        orderService.onPaymentApproved(new PaymentApprovedEvent(orderId, "p1", new BigDecimal("1")));

        assertEquals(OrderStatus.AMOUNT_MISMATCH, orders.findById(orderId).orElseThrow().getStatus());
        verify(devices, never()).on(anyString(), any());
        assertEquals(0, sessions.count());
        assertEquals(1, errors.count());
    }

    @Test
    void unknownOrderIsLoggedAndIgnored() {
        orderService.onPaymentApproved(new PaymentApprovedEvent("nope", "p1", new BigDecimal("3500")));
        verify(devices, never()).on(anyString(), any());
        assertEquals(1, errors.count());
    }

    @Test
    void unreachableDeviceMarksActivationFailedAndIsNotShownAsRunning() {
        when(devices.on(anyString(), any())).thenThrow(new DeviceCallException("offline", null));
        String orderId = newOrder();

        assertThrows(ActivationFailedException.class, () -> orderService.onPaymentApproved(paid(orderId, "p1")));

        assertEquals(SessionStatus.ACTIVATION_FAILED, onlySession().getStatus());
        assertEquals(0.0, sessionService.secondsLeft("box1"));
        assertEquals(1, errors.count());
    }

    @Test
    void unconfirmedOnIsAFailure() {
        when(devices.on(anyString(), any())).thenReturn(new DeviceCommandResponse("box1", "UNKNOWN", false));
        String orderId = newOrder();

        assertThrows(ActivationFailedException.class, () -> orderService.onPaymentApproved(paid(orderId, "p1")));
        assertEquals(SessionStatus.ACTIVATION_FAILED, onlySession().getStatus());
    }

    @Test
    void secondPaymentWhileRunningExtendsTheSameSession() {
        String first = newOrder();
        orderService.onPaymentApproved(paid(first, "p1"));
        Instant firstExpiry = onlySession().getExpiresAt();

        String second = newOrder();
        orderService.onPaymentApproved(paid(second, "p2"));

        WashSession s = onlySession();
        assertEquals(firstExpiry.plusSeconds(15 * 60), s.getExpiresAt());
        assertEquals(0, new BigDecimal("7000").compareTo(s.getTotalAmount()));
        verify(devices, times(1)).on(anyString(), any());
    }

    @Test
    void failedShutdownIsRetriedUntilItWorks() {
        String orderId = newOrder();
        orderService.onPaymentApproved(paid(orderId, "p1"));
        WashSession s = onlySession();
        s.setExpiresAt(Instant.now().minusSeconds(1));
        sessions.save(s);

        when(devices.off(anyString(), any())).thenThrow(new DeviceCallException("no response", null));
        sessionService.expireDue();
        assertEquals(SessionStatus.SHUTDOWN_FAILED, sessions.findById(s.getId()).orElseThrow().getStatus());
        assertEquals(0, history.count());

        doReturn(new DeviceCommandResponse("box1", "OFF", true)).when(devices).off(anyString(), any());
        sessionService.expireDue(); // shutdown-retry is 0s in the test profile
        assertEquals(SessionStatus.COMPLETED, sessions.findById(s.getId()).orElseThrow().getStatus());
        assertEquals(1, history.count());
    }

    @Test
    void restartWithExpiredRunningSessionStopsItOnTheNextTick() {
        String orderId = newOrder();
        orderService.onPaymentApproved(paid(orderId, "p1"));
        WashSession s = onlySession();
        s.setExpiresAt(Instant.now().minusSeconds(30)); // expired while the service was down
        sessions.save(s);

        sessionService.reconcileOnStartup();
        sessionService.expireDue();

        assertEquals(SessionStatus.COMPLETED, sessions.findById(s.getId()).orElseThrow().getStatus());
        verify(devices).off(eq("box1"), any());
    }

    @Test
    void manualPaymentStartsTheWasher() {
        orderService.registerManualPayment();
        assertEquals(SessionStatus.RUNNING, onlySession().getStatus());
        assertEquals("manual", onlySession().getPaymentMethod());
    }
}
