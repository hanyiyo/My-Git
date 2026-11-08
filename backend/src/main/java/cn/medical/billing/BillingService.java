package cn.medical.billing;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingService {
    public record Payment(String id, String appointmentId, long amountMinor, String state, String mode, long paidAt) {}
    public record Refund(String id, String paymentId, long amountMinor, String reason, String state, long createdAt,
        int attempts, Long nextAttemptAt, String lastError, String providerReference) {}
    public record FinancialStatus(Payment payment, Refund refund) {}
    private final BillingMapper billing;
    public BillingService(BillingMapper billing) { this.billing = billing; }
    public Payment payment(String appointment) { return billing.payment(appointment); }
    public FinancialStatus financialStatus(String appointment) {
        var payment = billing.payment(appointment);
        return new FinancialStatus(payment, payment == null ? null : billing.refund(payment.id()));
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public Refund requestFullRefund(String appointment, String reason, long now) {
        var payment = billing.payment(appointment);
        if (payment == null) return null;
        var existing = billing.refund(payment.id());
        if (existing != null) return existing;
        var refund = new Refund(UUID.randomUUID().toString(), payment.id(), payment.amountMinor(), reason, "REFUND_PENDING", now, 0, now, null, null);
        billing.insertRefund(refund);
        return refund;
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public Payment recordSimulatedSuccess(String appointment, long amountMinor, long paidAt) {
        // Local simulation records no actual transfer and performs no external call.
        var payment = new Payment(UUID.randomUUID().toString(), appointment, amountMinor, "SUCCEEDED", "SIMULATED", paidAt);
        billing.insert(payment);
        return payment;
    }
}
