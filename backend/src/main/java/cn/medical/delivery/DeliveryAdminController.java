package cn.medical.delivery;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/notifications")
public class DeliveryAdminController {
    private final DeliveryService delivery;
    public DeliveryAdminController(DeliveryService delivery) { this.delivery = delivery; }
    @PostMapping("/retry") int retry(JwtAuthenticationToken auth) { return delivery.retry(); }
}
