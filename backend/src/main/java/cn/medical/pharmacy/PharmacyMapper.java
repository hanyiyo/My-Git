package cn.medical.pharmacy;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PharmacyMapper {
 record Medicine(String id,String name,String spec,long feeMinor,int stock,int threshold,boolean active,long createdAt,long updatedAt) {}
 record Order(String id,String appointmentId,String patientId,String doctorId,String state,long totalMinor,long expiresAt,int medicinePaymentHours,long createdAt,Long paidAt) {}
 record Line(long id,String orderId,String medicineId,String medicineName,String medicineSpec,long feeMinor,int quantity) {}
 @Select("SELECT id,name,spec,fee_minor,stock,threshold,active,created_at,updated_at FROM pharmacy_medicine ORDER BY active DESC,name,id") List<Medicine> medicines();
 @Select("SELECT id,name,spec,fee_minor,stock,threshold,active,created_at,updated_at FROM pharmacy_medicine WHERE id=#{id}") Medicine medicine(String id);
 @Select("SELECT id,name,spec,fee_minor,stock,threshold,active,created_at,updated_at FROM pharmacy_medicine WHERE id=#{id} FOR UPDATE") Medicine lockMedicine(String id);
 @Insert("INSERT INTO pharmacy_medicine(id,name,spec,fee_minor,stock,threshold,active,created_at,updated_at) VALUES(#{id},#{name},#{spec},#{feeMinor},#{stock},#{threshold},#{active},#{createdAt},#{updatedAt})") void insertMedicine(Medicine m);
 @Update("UPDATE pharmacy_medicine SET name=#{name},spec=#{spec},fee_minor=#{feeMinor},stock=#{stock},threshold=#{threshold},active=#{active},updated_at=#{updatedAt} WHERE id=#{id}") int updateMedicine(Medicine m);
 @Select("SELECT id,appointment_id,patient_id,doctor_id,state,total_minor,expires_at,medicine_payment_hours,created_at,paid_at FROM pharmacy_order WHERE id=#{id}") Order order(String id);
 @Select("SELECT id,appointment_id,patient_id,doctor_id,state,total_minor,expires_at,medicine_payment_hours,created_at,paid_at FROM pharmacy_order WHERE appointment_id=#{id}") Order orderForAppointment(String id);
 @Select("SELECT id,appointment_id,patient_id,doctor_id,state,total_minor,expires_at,medicine_payment_hours,created_at,paid_at FROM pharmacy_order WHERE patient_id=#{patient} ORDER BY created_at DESC") List<Order> ordersForPatient(String patient);
 @Select("SELECT id,appointment_id,patient_id,doctor_id,state,total_minor,expires_at,medicine_payment_hours,created_at,paid_at FROM pharmacy_order WHERE state='PENDING_PAYMENT' AND expires_at<=#{now} ORDER BY expires_at,id LIMIT 100 FOR UPDATE") List<Order> dueOrders(long now);
 @Insert("INSERT INTO pharmacy_order(id,appointment_id,patient_id,doctor_id,state,total_minor,expires_at,medicine_payment_hours,created_at) VALUES(#{id},#{appointmentId},#{patientId},#{doctorId},'PENDING_PAYMENT',#{totalMinor},#{expiresAt},#{medicinePaymentHours},#{createdAt})") void insertOrder(Order o);
 @Insert("<script>INSERT INTO pharmacy_order_line(order_id,medicine_id,medicine_name,medicine_spec,fee_minor,quantity) VALUES <foreach collection='lines' item='l' separator=','>(#{l.orderId},#{l.medicineId},#{l.medicineName},#{l.medicineSpec},#{l.feeMinor},#{l.quantity})</foreach></script>") void insertLines(@Param("lines") List<Line> lines);
 @Select("SELECT id,order_id,medicine_id,medicine_name,medicine_spec,fee_minor,quantity FROM pharmacy_order_line WHERE order_id=#{orderId} ORDER BY id") List<Line> lines(String orderId);
 @Select("SELECT COALESCE(SUM(l.quantity),0) FROM pharmacy_order_line l JOIN pharmacy_order o ON o.id=l.order_id WHERE l.medicine_id=#{id} AND o.state='PENDING_PAYMENT'") int held(String id);
 @Select("SELECT COUNT(*) FROM pharmacy_order_line WHERE medicine_id=#{id}") int history(String id);
 @Update("UPDATE pharmacy_order SET state='PAID',paid_at=#{at} WHERE id=#{id} AND state='PENDING_PAYMENT' AND expires_at>#{at}") int markPaid(@Param("id") String id,@Param("at") long at);
 @Update("UPDATE pharmacy_order SET state='EXPIRED' WHERE id=#{id} AND state='PENDING_PAYMENT' AND expires_at<=#{at}") int expire(@Param("id") String id,@Param("at") long at);
 @Update("UPDATE pharmacy_medicine SET stock=stock+#{quantity},updated_at=#{at} WHERE id=#{id}") int releaseStock(@Param("id") String id,@Param("quantity") int quantity,@Param("at") long at);
 @Update("UPDATE pharmacy_medicine SET stock=stock-#{quantity},updated_at=#{at} WHERE id=#{id} AND stock>=#{quantity}") int consumeStock(@Param("id") String id,@Param("quantity") int quantity,@Param("at") long at);
}

