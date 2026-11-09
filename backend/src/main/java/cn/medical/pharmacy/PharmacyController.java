package cn.medical.pharmacy;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1") public class PharmacyController {
 private final PharmacyService service; public PharmacyController(PharmacyService service){this.service=service;}
 public record MedicineInput(@NotBlank String name,@NotBlank String spec,@Min(0) long feeMinor,@Min(0) int stock,@Min(0) int threshold,boolean active){}
 public record PrescriptionInput(@NotEmpty List<Line> lines){} public record Line(@NotBlank String medicineId,@Min(1) int quantity){}
 @GetMapping("/admin/pharmacy/medicines") List<PharmacyMapper.Medicine> medicines(){return service.listMedicines();}`r`n @GetMapping("/doctor/pharmacy/medicines") List<PharmacyMapper.Medicine> doctorMedicines(){return service.listMedicines().stream().filter(PharmacyMapper.Medicine::active).toList();}
 @PostMapping("/admin/pharmacy/medicines") @ResponseStatus(HttpStatus.CREATED) PharmacyMapper.Medicine create(@Valid @RequestBody MedicineInput i){return service.saveMedicine(null,i.name(),i.spec(),i.feeMinor(),i.stock(),i.threshold(),i.active());}
 @PutMapping("/admin/pharmacy/medicines/{id}") PharmacyMapper.Medicine update(@PathVariable String id,@Valid @RequestBody MedicineInput i){return service.saveMedicine(id,i.name(),i.spec(),i.feeMinor(),i.stock(),i.threshold(),i.active());}
 @DeleteMapping("/admin/pharmacy/medicines/{id}") void delete(@PathVariable String id){service.deleteMedicine(id);}
 @GetMapping("/doctor/pharmacy/appointments/{id}/prescriptions") PharmacyService.OrderView doctor(JwtAuthenticationToken a,@PathVariable String id){return service.doctorPrescription(a.getName(),id);}
 @PostMapping("/doctor/pharmacy/appointments/{id}/prescriptions") PharmacyService.OrderView prescribe(JwtAuthenticationToken a,@PathVariable String id,@Valid @RequestBody PrescriptionInput i){return service.reservePrescription(a.getName(),id,i.lines().stream().map(x->new PharmacyService.PrescriptionLineInput(x.medicineId(),x.quantity())).toList());}
 @GetMapping("/patient/pharmacy/orders") List<PharmacyService.OrderView> orders(JwtAuthenticationToken a){return service.patientOrders(a.getName());}
 @PostMapping("/patient/pharmacy/orders/{id}/pay") PharmacyService.OrderView pay(JwtAuthenticationToken a,@PathVariable String id){return service.pay(a.getName(),id);}
}

