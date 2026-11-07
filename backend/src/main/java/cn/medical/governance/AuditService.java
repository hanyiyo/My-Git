package cn.medical.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AuditService {
    public record Integrity(boolean valid, long signedEvents, long legacyUnsignedEvents, String publicKey, long checkpointSequence) {}
    private final AuditMapper audits;
    private final Clock clock;
    private final AuditSigner signer;
    private final ObjectMapper json;
    public AuditService(AuditMapper audits, Clock clock, ObjectMapper json, AuditSigner signer) {
        this.audits = audits; this.clock = clock; this.json = json; this.signer = signer;
    }
    @Transactional
    public void record(String actor, String action, String type, String id, String reason) {
        var head = audits.lockHead();
        var checkpoint = signer.checkpoint();
        var anchored = checkpoint.sequence() == 0 ? null : audits.signedEvent(checkpoint.sequence());
        var tail = head.sequenceNo() == 0 ? null : audits.signedEvent(head.sequenceNo());
        if (head.sequenceNo() < checkpoint.sequence() || (checkpoint.sequence() > 0 && (anchored == null || !anchored.signature().equals(checkpoint.signature())))
            || (head.sequenceNo() > 0 && (tail == null || !tail.signature().equals(head.signature()) || !valid(tail))))
            throw new cn.medical.identity.ApiFailure(503,"AUDIT_INTEGRITY_FAILED","审计连续性校验失败，已阻止写入，请联系管理员恢复审计数据");
        var event = new ReviewMapper.Audit(UUID.randomUUID().toString(), actor, action, type, id, reason == null ? "" : reason, clock.millis());
        long sequence = head.sequenceNo() + 1;
        String signature = signer.sign(payload(sequence, head.signature(), event));
        audits.insert(event); audits.sign(sequence, event.id(), head.signature(), signature); audits.updateHead(sequence, signature);
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { signer.checkpoint(sequence,signature); }
        });
    }
    @Transactional(readOnly = true)
    public Integrity verify() {
        // Repeatable-read gives the verifier one committed view of events and the persisted tail.
        var checkpoint = signer.checkpoint(); var head = audits.head(); long sequence = 0; String previous = "0".repeat(128);
        boolean checkpointFound = checkpoint.sequence() == 0;
        for (var row : audits.signedEvents()) {
            var event = new ReviewMapper.Audit(row.id(), row.actorId(), row.action(), row.objectType(), row.objectId(), row.reason(), row.createdAt());
            if (row.sequenceNo() != ++sequence || !row.previousSignature().equals(previous)
                || !signer.verify(payload(sequence, previous, event),row.signature()))
                return integrity(false,sequence,checkpoint.sequence());
            previous = row.signature();
            if (sequence == checkpoint.sequence()) checkpointFound = previous.equals(checkpoint.signature());
        }
        return integrity(checkpointFound && sequence == head.sequenceNo() && previous.equals(head.signature()) && audits.unsignedCount() == head.legacyUnsignedEvents(),sequence,checkpoint.sequence());
    }
    private Integrity integrity(boolean valid, long sequence, long checkpoint) { return new Integrity(valid,sequence,audits.unsignedCount(),signer.publicKey(),checkpoint); }
    private boolean valid(AuditMapper.SignedEvent row) {
        return signer.verify(payload(row.sequenceNo(),row.previousSignature(),new ReviewMapper.Audit(row.id(),row.actorId(),row.action(),row.objectType(),row.objectId(),row.reason(),row.createdAt())),row.signature());
    }
    private byte[] payload(long sequence, String previous, ReviewMapper.Audit event) {
        try { return json.writeValueAsBytes(List.of("medical-audit-v1",sequence,previous,event)); }
        catch (Exception error) { throw new IllegalStateException("Audit serialization failed",error); }
    }
}
