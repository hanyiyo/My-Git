package cn.medical.delivery;

import cn.medical.ai.AiService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name="medical.delivery-scanner-enabled",havingValue="true",matchIfMissing=true)
public class DeliveryScheduler {
    private final DeliveryService delivery;private final RuntimeTaskProcessor tasks;private final AiService ai;private final SseTransport transport;
    public DeliveryScheduler(DeliveryService delivery,RuntimeTaskProcessor tasks,AiService ai,SseTransport transport) { this.delivery=delivery;this.tasks=tasks;this.ai=ai;this.transport=transport; }
    @Scheduled(fixedDelayString="${medical.delivery-scan-delay:2000}",initialDelayString="${medical.delivery-scan-delay:2000}")
    public void deliver() { run(delivery::retry);run(transport::heartbeat); }
    @Scheduled(fixedDelayString="${medical.task-scan-delay:5000}",initialDelayString="${medical.task-scan-delay:5000}")
    public void recover() { run(tasks::processDue);run(ai::processDue); }
    private void run(Runnable action) { try { action.run(); } catch(RuntimeException failure) { org.slf4j.LoggerFactory.getLogger(getClass()).error("Persistent runtime scan failed; next scan will retry",failure); } }
}
