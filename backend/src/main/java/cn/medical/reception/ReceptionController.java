package cn.medical.reception;

import java.time.LocalDate;
import java.util.List;
import cn.medical.clinical.ClinicalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ReceptionController {
    private final ReceptionService reception;
    private final ClinicalService clinical;
    public ReceptionController(ReceptionService reception, ClinicalService clinical) { this.reception = reception; this.clinical = clinical; }
    public record CompletionInput(@NotBlank @Size(max=20000) String body) {}

    @PostMapping("/patient/appointments/{id}/check-in")
    ReceptionService.Summary checkIn(JwtAuthenticationToken auth, @PathVariable String id) { return reception.checkIn(auth.getName(), id); }
    @PostMapping("/patient/appointments/{id}/restore-queue")
    ReceptionService.Summary restore(JwtAuthenticationToken auth, @PathVariable String id) { return reception.restore(auth.getName(), id); }
    @GetMapping("/patient/appointments/{id}/reception")
    ReceptionService.Summary status(JwtAuthenticationToken auth, @PathVariable String id) { return reception.status(auth.getName(), id); }

    @GetMapping("/doctor/reception")
    List<ReceptionMapper.QueueEntry> queue(JwtAuthenticationToken auth, @RequestParam(required=false) String scheduleId,
        @RequestParam(required=false) LocalDate date) { return reception.doctorQueue(auth.getName(), scheduleId, date); }
    @PostMapping("/doctor/reception/{id}/call")
    ReceptionMapper.QueueEntry call(JwtAuthenticationToken auth, @PathVariable String id) { return reception.call(auth.getName(), id); }
    @PostMapping("/doctor/reception/{id}/start")
    ReceptionMapper.QueueEntry start(JwtAuthenticationToken auth, @PathVariable String id) { return reception.start(auth.getName(), id); }
    @PostMapping("/doctor/reception/{id}/miss")
    ReceptionMapper.QueueEntry miss(JwtAuthenticationToken auth, @PathVariable String id) { return reception.miss(auth.getName(), id); }
    @PostMapping("/doctor/reception/{id}/complete")
    ClinicalService.ClinicalView complete(JwtAuthenticationToken auth, @PathVariable String id, @Valid @RequestBody CompletionInput input) { return clinical.submitResult(auth.getName(), id, input.body()); }
}
