package cn.medical.delivery;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface DeliveryMapper {
    record Notification(long id, String recipientId, String eventType, String objectId, String payload, String dedupeKey,
        String state, int attempts, String lastError, long nextAttemptAt, long createdAt, Long readAt, Long cursorNo) {}
    record Lease(String token, Long expiresAt) {}
    @Insert("INSERT INTO notification (recipient_id,event_type,object_id,payload,dedupe_key,state,next_attempt_at,created_at) VALUES (#{recipientId},#{eventType},#{objectId},#{payload},#{dedupeKey},'PENDING',#{createdAt},#{createdAt}) ON DUPLICATE KEY UPDATE dedupe_key=dedupe_key")
    void insert(Notification notification);
    @Select("SELECT id,recipient_id,event_type,object_id,payload,dedupe_key,state,attempts,last_error,next_attempt_at,created_at,read_at,cursor_no FROM notification WHERE dedupe_key=#{key}") Notification byDedupe(String key);
    @Select("SELECT id,recipient_id,event_type,object_id,payload,dedupe_key,state,attempts,last_error,next_attempt_at,created_at,read_at,cursor_no FROM notification WHERE id=#{id}") Notification notification(long id);
    @Select("SELECT id,recipient_id,event_type,object_id,payload,dedupe_key,state,attempts,last_error,next_attempt_at,created_at,read_at,cursor_no FROM notification WHERE recipient_id=#{recipientId} AND cursor_no > #{after} ORDER BY cursor_no LIMIT #{limit}")
    List<Notification> after(@Param("recipientId") String recipientId,@Param("after") long after,@Param("limit") int limit);
    @Select("SELECT COUNT(*) FROM notification WHERE recipient_id=#{id} AND read_at IS NULL") int unread(String id);
    @Update("UPDATE notification SET read_at=COALESCE(read_at,#{now}) WHERE id=#{id} AND recipient_id=#{recipient}")
    int markRead(@Param("id") long id,@Param("recipient") String recipient,@Param("now") long now);
    @Select("SELECT id FROM notification WHERE state IN ('PENDING','FAILED') AND ((lease_token IS NULL AND attempts<3 AND next_attempt_at<=#{now}) OR (lease_token IS NOT NULL AND lease_expires_at<=#{now})) ORDER BY id LIMIT 100") List<Long> due(long now);
    @Select("SELECT id,recipient_id,event_type,object_id,payload,dedupe_key,state,attempts,last_error,next_attempt_at,created_at,read_at,cursor_no FROM notification WHERE id=#{id} FOR UPDATE") Notification lock(long id);
    @Select("SELECT lease_token AS token,COALESCE(lease_expires_at,0) AS expires_at FROM notification WHERE id=#{id}") Lease lease(long id);
    @Select("SELECT last_cursor FROM notification_cursor WHERE id=1 FOR UPDATE") long lockCursor();
    @Update("UPDATE notification_cursor SET last_cursor=#{cursor} WHERE id=1") void cursor(long cursor);
    @Update("UPDATE notification SET cursor_no=COALESCE(cursor_no,#{cursor}),lease_token=#{token},lease_expires_at=#{lease},attempts=attempts+1 WHERE id=#{id}")
    void claim(@Param("id") long id,@Param("cursor") long cursor,@Param("token") String token,@Param("lease") long lease);
    @Update("UPDATE notification SET state=#{state},last_error=#{error},next_attempt_at=#{next},lease_token=NULL,lease_expires_at=NULL WHERE id=#{id} AND lease_token=#{token}")
    int finish(@Param("id") long id,@Param("token") String token,@Param("state") String state,@Param("error") String error,@Param("next") long next);
}
