package cn.medical.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AiController {
    private final AiService ai;
    public AiController(AiService ai) { this.ai = ai; }

    public record ConsentInput(@NotNull Boolean accepted, @NotBlank @Size(max=40) String provider,
        @NotBlank @Size(max=60) String purpose, @NotBlank @Size(max=120) String scope) {}
    public record AnalyzeInput(@NotBlank @Size(max=36) String consentId, @NotBlank @Size(max=10000) String symptomText,
        @NotBlank @Size(max=80) String idempotencyKey) {}

    @PostMapping("/patient/appointments/{id}/ai/consents")
    AiService.ConsentView consent(JwtAuthenticationToken auth, @PathVariable String id, @Valid @RequestBody ConsentInput input) {
        return ai.consent(auth.getName(), id, new AiService.ConsentInput(input.accepted(), input.provider(), input.purpose(), input.scope()));
    }
    @PostMapping("/patient/appointments/{id}/ai/preconsultations")
    AiService.ResultView analyze(JwtAuthenticationToken auth, @PathVariable String id, @Valid @RequestBody AnalyzeInput input) {
        return ai.analyze(auth.getName(), id, new AiService.AnalyzeInput(input.consentId(), input.symptomText(), input.idempotencyKey()));
    }
    @GetMapping("/patient/appointments/{id}/ai/results")
    List<AiService.ResultView> patientResults(JwtAuthenticationToken auth, @PathVariable String id) {
        return ai.patientResults(auth.getName(), id);
    }
    @GetMapping("/doctor/clinical/{id}/ai")
    List<AiService.ResultView> doctorResults(JwtAuthenticationToken auth, @PathVariable String id) {
        return ai.doctorResults(auth.getName(), id);
    }
    @GetMapping("/assistant/business")
    AiService.BusinessAnswer business(@RequestParam String question) { return ai.business(question); }
}
