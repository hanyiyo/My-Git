package cn.medical.billing;

import org.apache.ibatis.annotations.*;

@Mapper
public interface BillingMapper {
    @Select("SELECT id, appointment_id, amount_minor, state, mode, paid_at FROM payment_order WHERE appointment_id=#{appointment}")
    BillingService.Payment payment(String appointment);
    @Insert("INSERT INTO payment_order (id, appointment_id, amount_minor, state, mode, paid_at) VALUES (#{id}, #{appointmentId}, #{amountMinor}, #{state}, #{mode}, #{paidAt})")
    void insert(BillingService.Payment payment);
}
