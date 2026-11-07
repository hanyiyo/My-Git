package cn.medical.governance;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ReviewMapper {
    record ReviewCase(String id, String appointmentId, String requesterId, String reason, String scope, String state, long createdAt, long expiresAt) {}
    record Flag(String id, String appointmentId, String targetType, String targetId, String markedBy, String state, String reason, long createdAt) {}
    record Audit(String id, String actorId, String action, String objectType, String objectId, String reason, long createdAt) {}
    @Insert("INSERT INTO medical_review_case (id, appointment_id, requester_id, reason, scope, state, created_at, expires_at) VALUES (#{id}, #{appointmentId}, #{requesterId}, #{reason}, #{scope}, 'GRANTED', #{createdAt}, #{expiresAt})")
    void insertReview(ReviewCase value);
    @Select("SELECT id, appointment_id, requester_id, reason, scope, state, created_at, expires_at FROM medical_review_case WHERE id=#{id} AND requester_id=#{requesterId} FOR UPDATE")
    ReviewCase review(@Param("id") String id, @Param("requesterId") String requesterId);
    @Update("UPDATE medical_review_case SET state=#{state} WHERE id=#{id}")
    int updateReviewState(@Param("id") String id, @Param("state") String state);
    @Insert("INSERT INTO medical_content_flag (id, appointment_id, target_type, target_id, marked_by, state, reason, created_at) VALUES (#{id}, #{appointmentId}, #{targetType}, #{targetId}, #{markedBy}, #{state}, #{reason}, #{createdAt})")
    void insertFlag(Flag value);
    @Select("SELECT id, appointment_id, target_type, target_id, marked_by, state, reason, created_at FROM medical_content_flag WHERE appointment_id=#{appointmentId} ORDER BY created_at, id")
    List<Flag> flags(@Param("appointmentId") String appointmentId);
    @Insert("INSERT INTO audit_event (id, actor_id, action, object_type, object_id, reason, created_at) VALUES (#{id}, #{actorId}, #{action}, #{objectType}, #{objectId}, #{reason}, #{createdAt})")
    void audit(@Param("id") String id, @Param("actorId") String actorId, @Param("action") String action, @Param("objectType") String objectType, @Param("objectId") String objectId, @Param("reason") String reason, @Param("createdAt") long createdAt);
    @Select("SELECT id, actor_id, action, object_type, object_id, reason, created_at FROM audit_event WHERE object_id=#{objectId} ORDER BY created_at, id")
    List<Audit> audits(@Param("objectId") String objectId);
}
