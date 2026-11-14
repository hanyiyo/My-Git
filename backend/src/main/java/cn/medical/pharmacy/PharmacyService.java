package cn.medical.pharmacy;

import cn.medical.booking.BookingService;
import cn.medical.identity.ApiFailure;
import cn.medical.reception.ReceptionService;
import cn.medical.settings.RulesService;
import java.time.Clock;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PharmacyService {
 public record PrescriptionLineInput(String medicineId,int quantity) {}
 public record MedicineView(PharmacyMapper.Medicine medicine) {}
 public record LineView(String medicineId,String name,String spec,long feeMinor,int quantity) {}
 public record OrderView(String id,String appointmentId,String patientId,String doctorId,String state,long totalMinor,long expiresAt,int medicinePaymentHours,long createdAt,Long paidAt,List<LineView> lines) {}
 public record AdminMedicineView(String id,String name,String spec,long feeMinor,int stock,int availableStock,int heldStock,int totalStock,int threshold,boolean active,boolean hasPrescriptionHistory,long createdAt,long updatedAt) {}
 public record ConsumptionView(String id,String state,int quantity,long createdAt,Long paidAt) {}
  private final PharmacyMapper pharmacy; private final BookingService booking; private final ReceptionService reception; private final Clock clock; private final RulesService rules;
  public PharmacyService(PharmacyMapper pharmacy,BookingService booking,ReceptionService reception,Clock clock,RulesService rules){this.pharmacy=pharmacy;this.booking=booking;this.reception=reception;this.clock=clock;this.rules=rules;}
 @Transactional(readOnly=true) public List<PharmacyMapper.Medicine> listMedicines(){return pharmacy.medicines();}
 @Transactional(readOnly=true) public List<AdminMedicineView> listAdminMedicines(){return pharmacy.medicines().stream().map(this::adminView).toList();}
  @Transactional(readOnly=true) public List<OrderView> medicineHistory(String medicineId){ if(pharmacy.medicine(medicineId)==null) throw new ApiFailure(404,"NOT_FOUND","药品不存在"); return pharmacy.ordersForMedicine(medicineId).stream().map(this::view).toList(); }
 @Transactional(readOnly=true) public List<ConsumptionView> medicineConsumptionHistory(String medicineId){ if(pharmacy.medicine(medicineId)==null) throw new ApiFailure(404,"NOT_FOUND","药品不存在"); return pharmacy.consumptionHistory(medicineId).stream().map(value -> new ConsumptionView(value.id(),value.state(),value.quantity(),value.createdAt(),value.paidAt())).toList(); }
 @Transactional public AdminMedicineView adjustStock(String id,int totalStock){
   var medicine=pharmacy.lockMedicine(id); if(medicine==null) throw new ApiFailure(404,"NOT_FOUND","药品不存在");
   int held=pharmacy.held(id); if(totalStock<held) throw new ApiFailure(409,"STOCK_BELOW_HELD","总库存不能低于已锁定库存");
   var updated=new PharmacyMapper.Medicine(medicine.id(),medicine.name(),medicine.spec(),medicine.feeMinor(),totalStock-held,medicine.threshold(),medicine.active(),medicine.createdAt(),clock.millis());
   pharmacy.updateMedicine(updated); return adminView(updated);
 }
 @Transactional public PharmacyMapper.Medicine updateMedicine(String id,String name,String spec,long feeMinor,int threshold,boolean active){
   var old=pharmacy.lockMedicine(id); if(old==null) throw new ApiFailure(404,"NOT_FOUND","药品不存在");
   if(name==null||name.isBlank()||spec==null||spec.isBlank()||feeMinor<0||threshold<0) throw new ApiFailure(400,"INVALID_INPUT","药品字段无效");
   var updated=new PharmacyMapper.Medicine(id,name.trim(),spec.trim(),feeMinor,old.stock(),threshold,active,old.createdAt(),clock.millis()); pharmacy.updateMedicine(updated); return pharmacy.medicine(id);
 }
 @Transactional public PharmacyMapper.Medicine saveMedicine(String id,String name,String spec,long feeMinor,int stock,int threshold,boolean active){
   if(name==null||name.isBlank()||spec==null||spec.isBlank()||feeMinor<0||stock<0||threshold<0) throw new ApiFailure(400,"INVALID_INPUT","药品字段无效");
   long now=clock.millis(); var m=new PharmacyMapper.Medicine(id==null||id.isBlank()?UUID.randomUUID().toString():id,name.trim(),spec.trim(),feeMinor,stock,threshold,active,now,now);
   if(id==null||id.isBlank()) pharmacy.insertMedicine(m); else { var old=pharmacy.lockMedicine(id); if(old==null) throw new ApiFailure(404,"NOT_FOUND","药品不存在"); if(stock<pharmacy.held(id)) throw new ApiFailure(409,"STOCK_BELOW_HELD","库存不能低于已锁定库存"); pharmacy.updateMedicine(m); }
   return pharmacy.medicine(m.id());
 }
 @Transactional public void deleteMedicine(String id){ var m=pharmacy.lockMedicine(id); if(m==null) throw new ApiFailure(404,"NOT_FOUND","药品不存在"); if(pharmacy.history(id)>0) throw new ApiFailure(409,"PRESCRIPTION_HISTORY_EXISTS","已有处方历史的药品不可删除"); pharmacy.deleteMedicine(id); }
 @Transactional public OrderView reservePrescription(String doctor,String appointmentId,List<PrescriptionLineInput> inputs){
   if(inputs==null||inputs.isEmpty()) return null; booking.lockCareScope(appointmentId); var scope=booking.careScope(appointmentId);
   if(scope==null||!scope.doctorId().equals(doctor)) throw new ApiFailure(404,"NOT_FOUND","问诊不存在"); var queue=reception.queueForAppointment(appointmentId);
   if(queue==null||!"IN_CONSULTATION".equals(queue.state())) throw new ApiFailure(409,"ENCOUNTER_NOT_OPEN","只有接诊中的问诊可以开药");
   var existing=pharmacy.orderForAppointment(appointmentId); if(existing!=null) throw new ApiFailure(409,"PRESCRIPTION_EXISTS","本次问诊已经开具处方");
   var clean=new ArrayList<PrescriptionLineInput>(); for(var in:inputs){if(in==null||in.quantity()<=0) throw new ApiFailure(400,"INVALID_INPUT","药品数量必须为正数"); clean.add(in);}
   clean.sort(Comparator.comparing(PrescriptionLineInput::medicineId)); var lines=new ArrayList<PharmacyMapper.Line>(); long total=0;
    for(var in:clean){var m=pharmacy.lockMedicine(in.medicineId()); if(m==null||!m.active()) throw new ApiFailure(409,"MEDICINE_UNAVAILABLE","药品不可用"); if(m.stock()<in.quantity()) throw new ApiFailure(409,"INSUFFICIENT_STOCK","药品库存不足"); if(pharmacy.consumeStock(m.id(),in.quantity(),clock.millis())!=1) throw new ApiFailure(409,"INSUFFICIENT_STOCK","药品库存不足"); total=Math.addExact(total,Math.multiplyExact(m.feeMinor(),in.quantity())); lines.add(new PharmacyMapper.Line(0L, "",m.id(),m.name(),m.spec(),m.feeMinor(),in.quantity())); }
    int hours=Math.max(1,rules.current().medicinePaymentHours()); long now=clock.millis(); var order=new PharmacyMapper.Order(UUID.randomUUID().toString(),appointmentId,scope.patientId(),doctor,"PENDING_PAYMENT",total,now+hours*3600000L,hours,now,null); pharmacy.insertOrder(order); var orderLines=lines.stream().map(l->new PharmacyMapper.Line(l.id(),order.id(),l.medicineId(),l.medicineName(),l.medicineSpec(),l.feeMinor(),l.quantity())).toList(); pharmacy.insertLines(orderLines); return view(order);
 }
 @Transactional(readOnly=true) public OrderView doctorPrescription(String doctor,String appointmentId){booking.lockCareScope(appointmentId);var scope=booking.careScope(appointmentId);if(scope==null||!scope.doctorId().equals(doctor))throw new ApiFailure(404,"NOT_FOUND","问诊不存在");var o=pharmacy.orderForAppointment(appointmentId);return o==null?null:view(o);}
 @Transactional public List<OrderView> patientOrders(String patient){return pharmacy.ordersForPatient(patient).stream().map(this::view).toList();}
 @Transactional public OrderView pay(String patient,String id){var o=pharmacy.order(id);if(o==null||!o.patientId().equals(patient))throw new ApiFailure(404,"NOT_FOUND","药费订单不存在"); long now=clock.millis(); if("PAID".equals(o.state()))return view(o);if(!"PENDING_PAYMENT".equals(o.state())||o.expiresAt()<=now){expire(o);throw new ApiFailure(409,"PAYMENT_EXPIRED","药费订单已超时");} if(pharmacy.markPaid(id,now)!=1){o=pharmacy.order(id);if(o!=null&&"PAID".equals(o.state()))return view(o);throw new ApiFailure(409,"PAYMENT_CONFLICT","药费订单状态已变化");}return view(pharmacy.order(id));}
 @Transactional public void expireDue(){long now=clock.millis();for(var o:pharmacy.dueOrders(now))expire(o);}
 private void expire(PharmacyMapper.Order o){if(pharmacy.expire(o.id(),clock.millis())==1)for(var l:pharmacy.lines(o.id()))pharmacy.releaseStock(l.medicineId(),l.quantity(),clock.millis());}
 private OrderView view(PharmacyMapper.Order o){return new OrderView(o.id(),o.appointmentId(),o.patientId(),o.doctorId(),o.state(),o.totalMinor(),o.expiresAt(),o.medicinePaymentHours(),o.createdAt(),o.paidAt(),pharmacy.lines(o.id()).stream().map(l->new LineView(l.medicineId(),l.medicineName(),l.medicineSpec(),l.feeMinor(),l.quantity())).toList());}
 private AdminMedicineView adminView(PharmacyMapper.Medicine medicine){int held=pharmacy.held(medicine.id()); return new AdminMedicineView(medicine.id(),medicine.name(),medicine.spec(),medicine.feeMinor(),medicine.stock(),medicine.stock(),held,medicine.stock()+held,medicine.threshold(),medicine.active(),pharmacy.history(medicine.id())>0,medicine.createdAt(),medicine.updatedAt());}
}

