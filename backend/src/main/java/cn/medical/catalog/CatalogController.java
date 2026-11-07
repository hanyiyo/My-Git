package cn.medical.catalog;

import java.util.List;
import java.time.LocalDate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {
    private final CatalogService catalog;
    public CatalogController(CatalogService catalog) { this.catalog = catalog; }
    public record HospitalInput(@NotBlank @Size(max=120) String name, @NotBlank @Size(max=300) String address, @NotNull @Size(max=2000) String description) {}
    public record DepartmentInput(@NotBlank @Size(max=120) String name, @NotNull @Size(max=2000) String description, @NotNull Boolean active) {}
    public record DoctorInput(@NotBlank @Size(max=120) String displayName, @NotBlank @Size(max=120) String title,
        @NotNull @Size(max=2000) String biography, @NotBlank String departmentId, @NotBlank String credentialStatus) {}
    public record ScheduleInput(@NotBlank String doctorId, @NotBlank String departmentId, @NotNull LocalDate clinicDate,
        @NotBlank String session, @NotBlank String serviceMode, @NotNull @Min(1) @Max(500) Integer capacity, @NotNull @Min(0) @Max(100000000) Long feeMinor) {}
    @GetMapping("/catalog/hospital") CatalogMapper.Hospital hospital() { return catalog.hospital(); }
    @PutMapping("/admin/catalog/hospital") CatalogMapper.Hospital hospital(@Valid @RequestBody HospitalInput input) {
        return catalog.saveHospital(input.name(), input.address(), input.description());
    }
    @GetMapping("/catalog/departments") List<CatalogMapper.Department> departments() { return catalog.departments(false); }
    @GetMapping("/admin/catalog/departments") List<CatalogMapper.Department> managedDepartments() { return catalog.departments(true); }
    @GetMapping("/catalog/doctors") List<CatalogService.DoctorView> doctors(@RequestParam(required=false) String departmentId) { return catalog.doctors(false, departmentId); }
    @GetMapping("/admin/catalog/doctors") List<CatalogService.DoctorView> managedDoctors() { return catalog.doctors(true, null); }
    @GetMapping("/doctor/catalog/profile") CatalogService.DoctorView ownDoctor(JwtAuthenticationToken authentication) { return catalog.ownDoctor(authentication.getName()); }
    @PutMapping("/admin/catalog/doctors/{id}") CatalogService.DoctorView doctor(@PathVariable String id, @Valid @RequestBody DoctorInput input) {
        return catalog.saveDoctor(id, input.displayName(), input.title(), input.biography(), input.departmentId(), input.credentialStatus());
    }
    @PostMapping("/admin/catalog/schedules") ResponseEntity<CatalogService.ScheduleView> publish(@Valid @RequestBody ScheduleInput input) {
        return ResponseEntity.status(201).body(catalog.publish(input.doctorId(), input.departmentId(), input.clinicDate(), input.session(), input.serviceMode(), input.capacity(), input.feeMinor()));
    }
    @GetMapping("/catalog/schedules") List<CatalogService.ScheduleView> schedules(@RequestParam(required=false) String doctorId,
            @RequestParam(required=false) String departmentId, @RequestParam(required=false) LocalDate date, @RequestParam(required=false) String mode) {
        return catalog.schedules(false, doctorId, departmentId, date, mode);
    }
    @GetMapping("/admin/catalog/schedules") List<CatalogService.ScheduleView> managedSchedules() { return catalog.schedules(true, null, null, null, null); }
    @GetMapping("/doctor/catalog/schedules") List<CatalogService.ScheduleView> ownSchedules(JwtAuthenticationToken authentication) {
        return catalog.schedules(true, authentication.getName(), null, null, null);
    }
    @PostMapping("/admin/catalog/departments") ResponseEntity<CatalogMapper.Department> createDepartment(@Valid @RequestBody DepartmentInput input) {
        return ResponseEntity.status(201).body(catalog.saveDepartment(null, input.name(), input.description(), input.active()));
    }
    @PutMapping("/admin/catalog/departments/{id}") CatalogMapper.Department updateDepartment(@PathVariable String id, @Valid @RequestBody DepartmentInput input) {
        return catalog.saveDepartment(id, input.name(), input.description(), input.active());
    }
}
