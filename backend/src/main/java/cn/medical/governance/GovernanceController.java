package cn.medical.governance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class GovernanceController {
    private final GovernanceService governance;
    public GovernanceController(GovernanceService governance) { this.governance = governance; }
    public record SubmitInput(@NotBlank String scheduleId, @NotBlank @Size(max=1000) String reason) {}
    public record HeadInput(@NotBlank String plan, String substituteDoctorId, @NotBlank @Size(max=1000) String reason) {}
    public record DecisionInput(boolean accepted, @NotBlank @Size(max=500) String reason) {}
    public record AdminInput(boolean approve, @NotBlank @Size(max=1000) String reason) {}
    public record EmergencyInput(@NotBlank @Size(max=1000) String reason) {}
    public record EmergencySubstitutionInput(@NotNull java.util.Map<String, Boolean> patientChoices, @NotBlank @Size(max=1000) String reason) {}
    @GetMapping("/admin/catalog/suspensions/{id}/impacts") List<GovernanceService.Impact> impacts(@PathVariable String id) { return governance.impacts(id); }
    @PostMapping("/admin/catalog/suspensions/{id}/emergency-substitution") GovernanceService.RequestView emergencySubstitution(JwtAuthenticationToken auth, @PathVariable String id, @Valid @RequestBody EmergencySubstitutionInput input) {
        return governance.emergencySubstitution(auth.getName(), id, input.patientChoices(), input.reason());
    }

    @PostMapping("/doctor/catalog/suspensions")
    ResponseEntity<GovernanceService.RequestView> submit(JwtAuthenticationToken auth, @Valid @RequestBody SubmitInput input) {
        return ResponseEntity.status(201).body(governance.submit(auth.getName(), input.scheduleId(), input.reason()));
    }
    @GetMapping("/doctor/catalog/head/suspensions") List<GovernanceService.RequestView> headInbox(JwtAuthenticationToken auth) { return governance.headInbox(auth.getName()); }
    @GetMapping("/doctor/catalog/suspensions") List<GovernanceService.RequestView> own(JwtAuthenticationToken auth) { return governance.ownRequests(auth.getName()); }
    @PostMapping("/admin/catalog/suspensions/{id}/plan") GovernanceService.RequestView adminPlan(JwtAuthenticationToken auth, @PathVariable String id, @Valid @RequestBody HeadInput input) {
        return governance.adminPlan(auth.getName(), id, input.plan(), input.substituteDoctorId(), input.reason());
    }
    @PostMapping("/admin/catalog/schedules/{id}/emergency-stop") GovernanceService.RequestView emergencySchedule(JwtAuthenticationToken auth, @PathVariable String id, @Valid @RequestBody EmergencyInput input) {
        return governance.emergencySchedule(auth.getName(), id, input.reason());
    }
    @PostMapping("/doctor/catalog/head/suspensions/{id}/review") GovernanceService.RequestView headReview(JwtAuthenticationToken auth, @PathVariable String id, @Valid @RequestBody HeadInput input) {
        return governance.headReview(auth.getName(), id, input.plan(), input.substituteDoctorId(), input.reason());
    }
    @PostMapping("/doctor/catalog/substitutions/{id}/respond") GovernanceService.RequestView substituteRespond(JwtAuthenticationToken auth, @PathVariable String id, @Valid @RequestBody DecisionInput input) {
        return governance.substituteRespond(auth.getName(), id, input.accepted(), input.reason());
    }
    @GetMapping("/doctor/catalog/substitutions") List<GovernanceService.RequestView> substituteInbox(JwtAuthenticationToken auth) { return governance.substituteInbox(auth.getName()); }
    @GetMapping("/admin/catalog/suspensions") List<GovernanceService.RequestView> adminInbox() { return governance.adminInbox(); }
    @PostMapping("/admin/catalog/suspensions/{id}/review") GovernanceService.RequestView adminReview(JwtAuthenticationToken auth, @PathVariable String id, @Valid @RequestBody AdminInput input) {
        return governance.adminReview(auth.getName(), id, input.approve(), input.reason());
    }
    @PostMapping("/admin/catalog/suspensions/{id}/emergency-stop") GovernanceService.RequestView emergencyStop(JwtAuthenticationToken auth, @PathVariable String id, @Valid @RequestBody EmergencyInput input) {
        return governance.emergencyStop(auth.getName(), id, input.reason());
    }
    @PostMapping("/admin/catalog/substitutions/expire") int expire(JwtAuthenticationToken auth) { return governance.expireChoices(auth.getName()); }
    @PutMapping("/admin/catalog/departments/{departmentId}/head/{doctorId}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) void assignHead(JwtAuthenticationToken auth, @PathVariable String departmentId, @PathVariable String doctorId) {
        governance.assignHead(auth.getName(), departmentId, doctorId);
    }
    @GetMapping("/patient/substitution-choices") List<GovernanceService.ChoiceView> choices(JwtAuthenticationToken auth) { return governance.choices(auth.getName()); }
    @PostMapping("/patient/substitution-choices/{id}") GovernanceService.ChoiceView choose(JwtAuthenticationToken auth, @PathVariable String id, @Valid @RequestBody DecisionInput input) {
        return governance.choose(auth.getName(), id, input.accepted(), input.reason());
    }
}
