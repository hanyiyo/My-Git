package cn.medical.delivery;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface RuntimeTaskMapper {
    record Task(String id,String taskType,String objectId,String state,int attempts,long nextAttemptAt,String leaseToken,Long leaseExpiresAt,String lastError) {}
    @Insert("INSERT INTO runtime_task(id,task_type,object_id,next_attempt_at) VALUES(#{id},#{type},#{object},#{deadline}) ON DUPLICATE KEY UPDATE id=id")
    void insert(@Param("id") String id,@Param("type") String type,@Param("object") String object,@Param("deadline") long deadline);
    @Select("SELECT id FROM runtime_task WHERE (state='PENDING' AND next_attempt_at<=#{now}) OR (state='RUNNING' AND lease_expires_at<=#{now}) ORDER BY next_attempt_at,id LIMIT 100") List<String> due(long now);
    @Select("SELECT id,task_type,object_id,state,attempts,next_attempt_at,lease_token,lease_expires_at,last_error FROM runtime_task WHERE id=#{id} FOR UPDATE") Task lock(String id);
    @Update("UPDATE runtime_task SET state='RUNNING',attempts=attempts+1,lease_token=#{token},lease_expires_at=#{expiry} WHERE id=#{id}")
    void claim(@Param("id") String id,@Param("token") String token,@Param("expiry") long expiry);
    @Update("UPDATE runtime_task SET state=#{state},last_error=#{error},next_attempt_at=#{next},lease_token=NULL,lease_expires_at=NULL WHERE id=#{id} AND lease_token=#{token}")
    int finish(@Param("id") String id,@Param("token") String token,@Param("state") String state,@Param("error") String error,@Param("next") long next);
    @Select("SELECT id,task_type,object_id,state,attempts,next_attempt_at,lease_token,lease_expires_at,last_error FROM runtime_task ORDER BY next_attempt_at DESC,id LIMIT 100") List<Task> recent();
}
