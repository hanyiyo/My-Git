package cn.medical.delivery;

import cn.medical.identity.IdentityService;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class SseTransport {
    private static final class Stream {
        final String recipient,session;final long expiresAt;final SseEmitter emitter;long cursor;
        Stream(String recipient,String session,long expiresAt,SseEmitter emitter,long cursor) { this.recipient=recipient;this.session=session;this.expiresAt=expiresAt;this.emitter=emitter;this.cursor=cursor; }
    }
    private final Set<Stream> streams=ConcurrentHashMap.newKeySet();
    private final IdentityService identity;private final Clock clock;private final DeliveryMapper notifications;
    public SseTransport(IdentityService identity,Clock clock,DeliveryMapper notifications) { this.identity=identity;this.clock=clock;this.notifications=notifications; }
    public SseEmitter open(String recipient,String session,long expiresAt,long cursor) {
        var emitter=new SseEmitter(30*60_000L);var stream=new Stream(recipient,session,expiresAt,emitter,cursor);streams.add(stream);
        emitter.onCompletion(() -> streams.remove(stream));emitter.onTimeout(() -> streams.remove(stream));emitter.onError(error -> streams.remove(stream));
        synchronized(stream) { try { emitter.send(SseEmitter.event().comment("connected"));pump(stream); }
        catch(Exception failure) { streams.remove(stream);emitter.completeWithError(failure); } }
        return emitter;
    }
    public boolean deliver(DeliveryMapper.Notification event) {
        boolean succeeded=true;
        for(var stream:streams)if(stream.recipient.equals(event.recipientId()))succeeded &= pump(stream);
        return succeeded;
    }
    // Both replay and live wakes drain the same committed cursor sequence under the stream lock.
    private boolean pump(Stream stream) {
        synchronized(stream) {
            if(!active(stream))return true;
            try {
                List<DeliveryMapper.Notification> batch;
                do { batch=notifications.after(stream.recipient,stream.cursor,100);
                    for(var event:batch) { stream.emitter.send(SseEmitter.event().id(Long.toString(event.cursorNo())).name("notification").data(event));stream.cursor=event.cursorNo(); }
                } while(batch.size()==100);
                return true;
            } catch(Exception failure) { streams.remove(stream);stream.emitter.completeWithError(failure);return false; }
        }
    }
    public void heartbeat() {
        for(var stream:streams)synchronized(stream) { if(active(stream))try { stream.emitter.send(SseEmitter.event().comment("heartbeat")); }
        catch(Exception failure) { streams.remove(stream);stream.emitter.completeWithError(failure); } }
    }
    private boolean active(Stream stream) {
        if(clock.millis()<stream.expiresAt && identity.active(stream.recipient,stream.session))return true;
        streams.remove(stream);stream.emitter.complete();return false;
    }
}
