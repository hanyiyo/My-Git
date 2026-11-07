package cn.medical.governance;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AuditMapper {
    record Head(long sequenceNo, String signature, long legacyUnsignedEvents) {}
    record SignedEvent(long sequenceNo, String previousSignature, String signature, String id, String actorId,
        String action, String objectType, String objectId, String reason, long createdAt) {}
    @Select("SELECT sequence_no, signature, legacy_unsigned_events FROM audit_chain_head WHERE id=1 FOR UPDATE") Head lockHead();
    @Select("SELECT sequence_no, signature, legacy_unsigned_events FROM audit_chain_head WHERE id=1") Head head();
    @Insert("INSERT INTO audit_event (id, actor_id, action, object_type, object_id, reason, created_at) VALUES (#{id},#{actorId},#{action},#{objectType},#{objectId},#{reason},#{createdAt})")
    void insert(ReviewMapper.Audit value);
    @Insert("INSERT INTO audit_signature (sequence_no,event_id,previous_signature,signature) VALUES (#{sequence},#{id},#{previous},#{signature})")
    void sign(@Param("sequence") long sequence, @Param("id") String id, @Param("previous") String previous, @Param("signature") String signature);
    @Update("UPDATE audit_chain_head SET sequence_no=#{sequence},signature=#{signature} WHERE id=1")
    void updateHead(@Param("sequence") long sequence, @Param("signature") String signature);
    @Select("SELECT s.sequence_no,s.previous_signature,s.signature,e.id,e.actor_id,e.action,e.object_type,e.object_id,e.reason,e.created_at FROM audit_signature s JOIN audit_event e ON e.id=s.event_id ORDER BY s.sequence_no")
    List<SignedEvent> signedEvents();
    @Select("SELECT s.sequence_no,s.previous_signature,s.signature,e.id,e.actor_id,e.action,e.object_type,e.object_id,e.reason,e.created_at FROM audit_signature s JOIN audit_event e ON e.id=s.event_id WHERE s.sequence_no=#{sequence} FOR SHARE")
    SignedEvent signedEvent(long sequence);
    @Select("SELECT COUNT(*) FROM audit_event e LEFT JOIN audit_signature s ON s.event_id=e.id WHERE s.event_id IS NULL") long unsignedCount();
}
