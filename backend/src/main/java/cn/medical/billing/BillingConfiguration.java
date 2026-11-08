package cn.medical.billing;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BillingConfiguration {
    @Bean RefundProvider localSimulatedRefundProvider() {
        // No transfer occurs; the same persisted refund key yields the same result after restart.
        return (key, payment, amount) -> "SIMULATED-" + key;
    }
}
