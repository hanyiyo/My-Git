package cn.medical.billing;

import org.apache.ibatis.annotations.*;

@Mapper
public interface BillingMapper {
    @Select("SELECT id, appointment_id, amount_minor, state, mode, paid_at FROM payment_order WHERE appointment_id=#{appointment}")
    BillingService.Payment payment(String appointment);
    @Insert("INSERT INTO payment_order (id, appointment_id, amount_minor, state, mode, paid_at) VALUES (#{id}, #{appointmentId}, #{amountMinor}, #{state}, #{mode}, #{paidAt})")
    void insert(BillingService.Payment payment);
    @Select("SELECT id, payment_id, amount_minor, reason, state, created_at, attempts, next_attempt_at, last_error, provider_reference FROM refund WHERE payment_id=#{payment}")
    BillingService.Refund refund(String payment);
    @Insert("INSERT INTO refund (id, payment_id, amount_minor, reason, state, created_at, attempts, next_attempt_at) VALUES (#{id}, #{paymentId}, #{amountMinor}, #{reason}, #{state}, #{createdAt}, #{attempts}, #{nextAttemptAt})")
    void insertRefund(BillingService.Refund refund);
    @Select("SELECT id, payment_id, amount_minor, reason, state, created_at, attempts, next_attempt_at, last_error, provider_reference FROM refund WHERE id=#{id}")
    BillingService.Refund refundById(String id);
    record Work(String id, String paymentId, long amountMinor, String state, int attempts,
        Long nextAttemptAt, String attemptToken, Long leaseExpiresAt) {}
    @Select("SELECT id, payment_id, amount_minor, state, attempts, next_attempt_at, attempt_token, lease_expires_at FROM refund WHERE id=#{id} FOR UPDATE")
    Work lockRefund(String id);
    @Select("SELECT id FROM refund WHERE (state IN ('REFUND_PENDING','REFUND_FAILED') AND next_attempt_at <= #{now}) OR (state='REFUND_PROCESSING' AND lease_expires_at <= #{now}) ORDER BY created_at, id LIMIT 100")
    java.util.List<String> dueRefunds(long now);
    @Update("UPDATE refund SET state='REFUND_PROCESSING', attempts=attempts+1, next_attempt_at=NULL, attempt_token=#{token}, lease_expires_at=#{lease} WHERE id=#{id}")
    void claimRefund(@Param("id") String id, @Param("token") String token, @Param("lease") long lease);
    @Insert("INSERT INTO refund_attempt (token, refund_id, attempt_number, started_at, state) VALUES (#{token}, #{id}, #{attempt}, #{now}, 'PROCESSING')")
    void insertAttempt(@Param("id") String id, @Param("token") String token, @Param("attempt") int attempt, @Param("now") long now);
    @Update("UPDATE refund_attempt SET state=#{state}, finished_at=#{now}, error=#{error}, provider_reference=#{reference} WHERE token=#{token} AND state='PROCESSING'")
    void finishAttempt(@Param("token") String token, @Param("state") String state, @Param("now") long now, @Param("error") String error, @Param("reference") String reference);
    @Update("UPDATE refund SET state=#{state}, next_attempt_at=#{next}, last_error=COALESCE(#{error},last_error), provider_reference=#{reference}, attempt_token=NULL, lease_expires_at=NULL WHERE id=#{id} AND attempt_token=#{token} AND state='REFUND_PROCESSING'")
    int finishRefund(@Param("id") String id, @Param("token") String token, @Param("state") String state,
        @Param("next") Long next, @Param("error") String error, @Param("reference") String reference);
}
