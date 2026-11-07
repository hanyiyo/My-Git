package cn.medical.delivery;

import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/notifications")
public class DeliveryController {
    private final DeliveryService delivery;
    public DeliveryController(DeliveryService delivery) { this.delivery = delivery; }
    @GetMapping List<DeliveryMapper.Notification> list(JwtAuthenticationToken auth, @RequestParam(defaultValue="0") long after, @RequestParam(defaultValue="50") int limit) { return delivery.after(auth.getName(), after, limit); }
    @GetMapping("/unread") int unread(JwtAuthenticationToken auth) { return delivery.unread(auth.getName()); }
    @PostMapping("/{id}/read") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) void read(JwtAuthenticationToken auth, @PathVariable long id) { delivery.markRead(auth.getName(), id); }
    @GetMapping(value="/stream", produces=MediaType.TEXT_EVENT_STREAM_VALUE) SseEmitter stream(JwtAuthenticationToken auth, @RequestHeader(value="Last-Event-ID", defaultValue="0") long cursor) { return delivery.stream(auth.getName(), auth.getToken().getClaimAsString("sid"),auth.getToken().getExpiresAt().toEpochMilli(),cursor); }
}
