package cn.medical.delivery;

import cn.medical.identity.ApiFailure;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class DeliveryService {
    private final DeliveryMapper delivery;
    private final Clock clock;
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, SseEmitter>> streams = new ConcurrentHashMap<>();
    public DeliveryService(DeliveryMapper delivery, Clock clock) { this.delivery = delivery; this.clock = clock; }

    public void publish(String recipientId, String eventType, String objectId, String payload, String dedupeKey) {
        long now = clock.millis(); var event = new DeliveryMapper.Notification(0, recipientId, eventType, objectId, payload, dedupeKey, "PENDING", 0, null, now, now, null);
        try { delivery.insert(event); } catch (DuplicateKeyException ignored) { return; }
        var persisted = delivery.byDedupe(dedupeKey);
        if (persisted == null) return;
        if (emit(persisted.id(), recipientId, eventType, payload))
            delivery.delivery(persisted.id(), "SENT", 1, null, now);
        else
            delivery.delivery(persisted.id(), "FAILED", 1, "SSE_DELIVERY_FAILED", now + 60_000L);
    }

    public List<DeliveryMapper.Notification> after(String recipientId, long cursor, int limit) {
        if (limit < 1 || limit > 100) throw new ApiFailure(400, "INVALID_INPUT", "通知查询数量范围为1至100");
        return delivery.after(recipientId, Math.max(0, cursor), limit);
    }
    public int unread(String recipientId) { return delivery.unread(recipientId); }
    public void markRead(String recipientId, long id) { if (delivery.markRead(id, recipientId, clock.millis()) != 1) throw new ApiFailure(404, "NOT_FOUND", "通知不存在"); }
    public int retry() {
        int retried = 0; long now = clock.millis();
        for (var event : delivery.retryable(now)) {
            if (event.attempts() >= 3) continue;
            if (emit(event.id(), event.recipientId(), event.eventType(), event.payload())) {
                delivery.delivery(event.id(), "SENT", event.attempts() + 1, null, now); retried++;
            } else {
                delivery.delivery(event.id(), "FAILED", event.attempts() + 1, "SSE_DELIVERY_FAILED", now + 60_000L);
            }
        }
        return retried;
    }

    public SseEmitter stream(String recipientId, long cursor) {
        var emitter = new SseEmitter(30 * 60_000L);
        var userStreams = streams.computeIfAbsent(recipientId, ignored -> new ConcurrentHashMap<>());
        userStreams.put(System.identityHashCode(emitter) * 1L, emitter);
        emitter.onCompletion(() -> userStreams.values().remove(emitter));
        emitter.onTimeout(() -> userStreams.values().remove(emitter));
        for (var event : after(recipientId, cursor, 100)) send(emitter, event.id(), event.eventType(), event.payload());
        return emitter;
    }
    private boolean emit(long id, String recipientId, String eventType, String payload) {
        var userStreams = streams.get(recipientId); if (userStreams == null) return true;
        boolean delivered = true;
        for (var emitter : userStreams.values()) delivered &= send(emitter, id, eventType, payload);
        return delivered;
    }
    private boolean send(SseEmitter emitter, long id, String type, String payload) {
        try { emitter.send(SseEmitter.event().id(Long.toString(id)).name(type).data(payload)); return true; }
        catch (Exception error) { emitter.completeWithError(error); return false; }
    }
}
