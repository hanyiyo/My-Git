package cn.medical.delivery;

import cn.medical.booking.BookingService;
import cn.medical.governance.GovernanceService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Transactional(propagation=Propagation.NOT_SUPPORTED)
public class RuntimeTaskProcessor {
    private final RuntimeTaskMapper tasks;
    private final BookingService booking;
    private final GovernanceService governance;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private record Claim(RuntimeTaskMapper.Task task,String token) {}
    public RuntimeTaskProcessor(RuntimeTaskMapper tasks,BookingService booking,GovernanceService governance,Clock clock,PlatformTransactionManager manager) {
        this.tasks=tasks;this.booking=booking;this.governance=governance;this.clock=clock;
        transactions=new TransactionTemplate(manager);transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    public int processDue() {
        int count=0;
        for(String id:tasks.due(clock.millis())) { process(id);count++; }
        return count;
    }
    public void process(String id) {
        var claim=transactions.execute(status -> claim(id));if(claim==null) return;
        // Business effect and task completion share a transaction; a dead worker cannot commit effects after losing its lease.
        try { transactions.executeWithoutResult(status -> {
            var current=tasks.lock(id);if(!claim.token().equals(current.leaseToken())) return;
            switch(current.taskType()) {
                case "HOLD_EXPIRY" -> booking.expireHold(current.objectId());
                case "SUBSTITUTION_EXPIRY" -> governance.expireChoiceTask(current.objectId());
                case "CHECK_IN_REMINDER" -> booking.checkInReminder(current.objectId());
                default -> throw new IllegalStateException("Unknown durable task type");
            }
            tasks.finish(id,claim.token(),"SUCCEEDED",null,clock.millis());
        }); } catch(RuntimeException failure) {
            transactions.executeWithoutResult(status -> {
                var current=tasks.lock(id);if(!claim.token().equals(current.leaseToken())) return;
                tasks.finish(id,claim.token(),current.attempts()>=3?"FAILED":"PENDING","TASK_EXECUTION_FAILED",clock.millis()+60_000);
            });
        }
    }
    private Claim claim(String id) {
        var task=tasks.lock(id);if(task==null || task.state().equals("SUCCEEDED") || task.state().equals("FAILED"))return null;
        long now=clock.millis();if(task.state().equals("RUNNING") && task.leaseExpiresAt()>now || task.state().equals("PENDING") && task.nextAttemptAt()>now)return null;
        if(task.attempts()>=3) { tasks.finish(id,task.leaseToken(),"FAILED","TASK_LEASE_EXHAUSTED",now);return null; }
        String token=UUID.randomUUID().toString();tasks.claim(id,token,now+30_000);return new Claim(task,token);
    }
}
