package cn.medical.billing;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name="medical.refund-scanner-enabled", havingValue="true", matchIfMissing=true)
public class RefundScheduler {
    private final RefundProcessor refunds;
    public RefundScheduler(RefundProcessor refunds) { this.refunds = refunds; }
    @Scheduled(fixedDelayString="${medical.refund-scan-delay:5000}", initialDelayString="${medical.refund-scan-delay:5000}")
    public void processDue() { refunds.processDue(); }
}
