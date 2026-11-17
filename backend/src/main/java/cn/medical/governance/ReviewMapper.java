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
    @Select("SELECT id, appointment_id, target_type, target_id, marked_by, state, reason, created_at FROM medical_content_flag WHERE appointment_id=#{appointmentId} ORDER BY sequence_no")
    List<Flag> flags(@Param("appointmentId") String appointmentId);
    @Select("SELECT id, appointment_id, target_type, target_id, marked_by, state, reason, created_at FROM medical_content_flag WHERE appointment_id=#{appointment} AND target_type=#{type} AND target_id=#{id} ORDER BY sequence_no DESC LIMIT 1")
    Flag latestFlag(@Param("appointment") String appointment, @Param("type") String type, @Param("id") String id);
    @Insert("INSERT INTO medical_reviewer_permission (account_id,granted_by,enabled,reason,updated_at) VALUES (#{account},#{admin},#{enabled},#{reason},#{now}) ON DUPLICATE KEY UPDATE granted_by=#{admin},enabled=#{enabled},reason=#{reason},updated_at=#{now}")
    void permission(@Param("account") String account, @Param("admin") String admin, @Param("enabled") boolean enabled, @Param("reason") String reason, @Param("now") long now);
    @Select("SELECT COUNT(*) > 0 FROM medical_reviewer_permission WHERE account_id=#{actor} AND enabled=TRUE") boolean permissionEnabled(String actor);
    @Update("UPDATE medical_review_case SET state='REVOKED' WHERE requester_id=#{actor} AND state='GRANTED'") void revokeForReviewer(String actor);
    @Select("SELECT id, actor_id, action, object_type, object_id, reason, created_at FROM audit_event WHERE object_id=#{objectId} ORDER BY created_at, id")
    List<Audit> audits(@Param("objectId") String objectId);
}
