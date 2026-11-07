package cn.medical.billing;

import cn.medical.identity.ApiFailure;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class RefundProcessor {
    private static final int MAX_ATTEMPTS = 3;
    private static final long LEASE_MILLIS = 30_000;
    private static final long RETRY_MILLIS = 60_000;
    private final BillingMapper billing;
    private final RefundProvider provider;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private record Claim(String id, String paymentId, long amountMinor, String token) {}
    public RefundProcessor(BillingMapper billing, RefundProvider provider, Clock clock, PlatformTransactionManager manager) {
        this.billing = billing; this.provider = provider; this.clock = clock;
        transaction = new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    public BillingService.Refund process(String id) {
        return process(id, false);
    }
    public BillingService.Refund process(String id, boolean crashAfterClaim) {
        var claim = transaction.execute(status -> claim(id, crashAfterClaim));
        if (claim == null) return billing.refundById(id);
        if (crashAfterClaim) return billing.refundById(id);
        String reference = null; String error = null;
        try {
            reference = provider.refund(claim.id(), claim.paymentId(), claim.amountMinor());
            if (reference == null || reference.isBlank() || reference.length() > 200)
                throw new IllegalStateException("Refund provider returned an invalid reference");
        } catch (Exception failure) {
            String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            error = message.substring(0, Math.min(message.length(), 500));
        }
        String resultReference = reference; String resultError = error;
        return transaction.execute(status -> finish(claim, resultReference, resultError));
    }
    private Claim claim(String id, boolean force) {
        var work = billing.lockRefund(id);
        if (work == null) throw new ApiFailure(404, "NOT_FOUND", "退款记录不存在");
        long now = clock.millis();
        if (work.state().equals("REFUNDED")) return null;
        if (work.state().equals("REFUND_PROCESSING")) {
            if (work.leaseExpiresAt() != null && work.leaseExpiresAt() > now) return null;
            String error = "Refund claim lease expired";
            billing.finishAttempt(work.attemptToken(), "ABANDONED", now, error, null);
            if (work.attempts() >= MAX_ATTEMPTS) {
                billing.finishRefund(id, work.attemptToken(), "REFUND_FAILED", null, error, null);
                return null;
            }
        } else if (work.attempts() >= MAX_ATTEMPTS || (!force && (work.nextAttemptAt() == null || work.nextAttemptAt() > now))) return null;
        String token = UUID.randomUUID().toString();
        billing.claimRefund(id, token, now + LEASE_MILLIS);
        billing.insertAttempt(id, token, work.attempts() + 1, now);
        return new Claim(id, work.paymentId(), work.amountMinor(), token);
    }
    private BillingService.Refund finish(Claim claim, String reference, String error) {
        var work = billing.lockRefund(claim.id());
        // A recovered lease owns a different token; a delayed old worker cannot overwrite it.
        if (!work.state().equals("REFUND_PROCESSING") || !claim.token().equals(work.attemptToken()))
            return billing.refundById(claim.id());
        long now = clock.millis();
        boolean succeeded = error == null;
        Long next = succeeded || work.attempts() >= MAX_ATTEMPTS ? null : now + RETRY_MILLIS;
        billing.finishRefund(claim.id(), claim.token(), succeeded ? "REFUNDED" : "REFUND_FAILED", next, error, succeeded ? reference : null);
        billing.finishAttempt(claim.token(), succeeded ? "SUCCEEDED" : "FAILED", now, error, succeeded ? reference : null);
        return billing.refundById(claim.id());
    }
    public void processDue() {
        for (var id : billing.dueRefunds(clock.millis())) process(id);
    }
}
