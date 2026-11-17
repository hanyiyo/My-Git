package cn.medical.delivery;

import cn.medical.identity.*;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class DeliveryService {
    private final DeliveryMapper delivery;
    private final RuntimeTaskMapper tasks;
    private final SseTransport transport;
    private final IdentityService identity;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private record Claim(DeliveryMapper.Notification event,String token) {}
    public DeliveryService(DeliveryMapper delivery,RuntimeTaskMapper tasks,SseTransport transport,IdentityService identity,Clock clock,PlatformTransactionManager manager) {
        this.delivery=delivery;this.tasks=tasks;this.transport=transport;this.identity=identity;this.clock=clock;
        transactions=new TransactionTemplate(manager);transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    @Transactional
    public void publish(String recipient,String type,String object,String payload,String key) {
        long now=clock.millis();delivery.insert(new DeliveryMapper.Notification(0,recipient,type,object,payload,key,"PENDING",0,null,now,now,null,null));
    }
    @Transactional
    public void administrators(String type,String object,String payload,String key) {
        for(String admin:identity.administrators()) publish(admin,type,object,payload,key+":"+admin);
    }
    @Transactional
    public void schedule(String type,String object,long deadline) { tasks.insert(type+":"+object,type,object,deadline); }
    public List<DeliveryMapper.Notification> after(String recipient,long cursor,int limit) {
        if(limit<1 || limit>100 || cursor<0) throw new ApiFailure(400,"INVALID_INPUT","通知游标须非负，数量范围为1至100");
        return delivery.after(recipient,cursor,limit);
    }
    public int unread(String recipient) { return delivery.unread(recipient); }
    @Transactional
    public void markRead(String recipient,long id) { if(delivery.markRead(id,recipient,clock.millis())!=1) throw new ApiFailure(404,"NOT_FOUND","通知不存在"); }
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public int retry() {
        int count=0;
        for(long id:delivery.due(clock.millis())) {
            var claim=transactions.execute(status -> claim(id));if(claim==null) continue;
            boolean sent;
            try { sent=transport.deliver(claim.event()); } catch(RuntimeException failure) { sent=false; }
            boolean result=sent;
            transactions.executeWithoutResult(status -> delivery.finish(id,claim.token(),result?"SENT":"FAILED",result?null:"SSE_DELIVERY_FAILED",clock.millis()+60_000));count++;
        }
        return count;
    }
    private Claim claim(long id) {
        // Cursor is allocated in this short post-commit transaction, so a later cursor cannot hide a late business commit.
        long cursor=delivery.lockCursor();var event=delivery.lock(id);if(event==null || event.state().equals("SENT")) return null;
        var lease=delivery.lease(id);long now=clock.millis();
        if(lease.token()!=null && lease.expiresAt()>now) return null;
        if(event.attempts()>=3) { if(lease.token()!=null) delivery.finish(id,lease.token(),"FAILED","DELIVERY_LEASE_EXHAUSTED",now);return null; }
        if(lease.token()==null && event.nextAttemptAt()>now) return null;
        if(event.cursorNo()==null) delivery.cursor(++cursor);else cursor=event.cursorNo();
        String token=UUID.randomUUID().toString();delivery.claim(id,cursor,token,now+30_000);
        return new Claim(delivery.notification(id),token);
    }
    public SseEmitter stream(String recipient,String session,long expiresAt,long cursor) {
        if(cursor<0)throw new ApiFailure(400,"INVALID_INPUT","通知游标须非负");
        return transport.open(recipient,session,expiresAt,cursor);
    }
}
