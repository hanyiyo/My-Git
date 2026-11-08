package cn.medical.delivery;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/notifications")
public class DeliveryAdminController {
    private final DeliveryService delivery;
    private final RuntimeTaskMapper tasks;
    public DeliveryAdminController(DeliveryService delivery,RuntimeTaskMapper tasks) { this.delivery = delivery;this.tasks=tasks; }
    @PostMapping("/retry") int retry(JwtAuthenticationToken auth) { return delivery.retry(); }
    @GetMapping("/tasks") java.util.List<RuntimeTaskMapper.Task> tasks() { return tasks.recent(); }
}
