package cn.medical.billing;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingService {
    public record Payment(String id, String appointmentId, long amountMinor, String state, String mode, long paidAt) {}
    private final BillingMapper billing;
    public BillingService(BillingMapper billing) { this.billing = billing; }
    public Payment payment(String appointment) { return billing.payment(appointment); }
    @Transactional(propagation = Propagation.MANDATORY)
    public Payment recordSimulatedSuccess(String appointment, long amountMinor, long paidAt) {
        // Local simulation records no actual transfer and performs no external call.
        var payment = new Payment(UUID.randomUUID().toString(), appointment, amountMinor, "SUCCEEDED", "SIMULATED", paidAt);
        billing.insert(payment);
        return payment;
    }
}
