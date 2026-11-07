package cn.medical.delivery;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface DeliveryMapper {
    record Notification(long id, String recipientId, String eventType, String objectId, String payload, String dedupeKey,
        String state, int attempts, String lastError, long nextAttemptAt, long createdAt, Long readAt) {}
    @Insert("INSERT INTO notification (recipient_id, event_type, object_id, payload, dedupe_key, state, next_attempt_at, created_at) VALUES (#{recipientId}, #{eventType}, #{objectId}, #{payload}, #{dedupeKey}, 'PENDING', #{createdAt}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Notification notification);
    @Select("SELECT id, recipient_id, event_type, object_id, payload, dedupe_key, state, attempts, last_error, next_attempt_at, created_at, read_at FROM notification WHERE dedupe_key=#{dedupeKey}")
    Notification byDedupe(@Param("dedupeKey") String dedupeKey);
    @Select("SELECT id, recipient_id, event_type, object_id, payload, dedupe_key, state, attempts, last_error, next_attempt_at, created_at, read_at FROM notification WHERE id=#{id}")
    Notification notification(long id);
    @Select("SELECT id, recipient_id, event_type, object_id, payload, dedupe_key, state, attempts, last_error, next_attempt_at, created_at, read_at FROM notification WHERE recipient_id=#{recipientId} AND id > #{after} ORDER BY id LIMIT #{limit}")
    List<Notification> after(@Param("recipientId") String recipientId, @Param("after") long after, @Param("limit") int limit);
    @Select("SELECT COUNT(*) FROM notification WHERE recipient_id=#{recipientId} AND read_at IS NULL")
    int unread(@Param("recipientId") String recipientId);
    @Update("UPDATE notification SET read_at=#{now} WHERE id=#{id} AND recipient_id=#{recipientId}")
    int markRead(@Param("id") long id, @Param("recipientId") String recipientId, @Param("now") long now);
    @Update("UPDATE notification SET state=#{state}, attempts=#{attempts}, last_error=#{error}, next_attempt_at=#{nextAttemptAt} WHERE id=#{id}")
    int delivery(@Param("id") long id, @Param("state") String state, @Param("attempts") int attempts,
        @Param("error") String error, @Param("nextAttemptAt") long nextAttemptAt);
    @Select("SELECT id, recipient_id, event_type, object_id, payload, dedupe_key, state, attempts, last_error, next_attempt_at, created_at, read_at FROM notification WHERE state='FAILED' AND attempts < 3 AND next_attempt_at <= #{now} ORDER BY id LIMIT 100")
    List<Notification> retryable(long now);
}
