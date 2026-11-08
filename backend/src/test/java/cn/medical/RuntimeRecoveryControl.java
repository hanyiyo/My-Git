package cn.medical;

import cn.medical.delivery.*;
import cn.medical.ai.AiService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@TestConfiguration
public class RuntimeRecoveryControl {
    @RestController
    @RequestMapping("/api/v1/admin/test/runtime")
    static class Controller {
        private final DeliveryService delivery;private final RuntimeTaskProcessor tasks;private final AiService ai;
        Controller(DeliveryService delivery,RuntimeTaskProcessor tasks,AiService ai) { this.delivery=delivery;this.tasks=tasks;this.ai=ai; }
        @PostMapping("/scan") Map<String,Integer> scan() { ai.processDue();int count=tasks.processDue();return Map.of("tasks",count,"notifications",delivery.retry()); }
    }
}
