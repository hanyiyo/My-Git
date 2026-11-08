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
        @NotBlank @Size(max=60) String purpose, @NotBlank @Size(max=120) String scope, @Size(max=10000) String symptomText) {}
    public record AnalyzeInput(@NotBlank @Size(max=36) String consentId, @NotBlank @Size(max=10000) String symptomText,
        @NotBlank @Size(max=80) String idempotencyKey) {}
    public record ShareInput(@NotNull Boolean shared) {}
    @PostMapping("/patient/appointments/{id}/ai/drafts/{taskId}/share")
    AiService.ResultView share(JwtAuthenticationToken auth, @PathVariable String id, @PathVariable String taskId, @Valid @RequestBody ShareInput input) {
        return ai.shareDraft(auth.getName(), id, taskId, input.shared());
    }

    @PostMapping("/patient/appointments/{id}/ai/consents")
    AiService.ConsentView consent(JwtAuthenticationToken auth, @PathVariable String id, @Valid @RequestBody ConsentInput input) {
        return ai.consent(auth.getName(), id, new AiService.ConsentInput(input.accepted(), input.provider(), input.purpose(), input.scope(), input.symptomText()));
    }
    @PostMapping("/patient/appointments/{id}/ai/preconsultations")
    AiService.ResultView analyze(JwtAuthenticationToken auth, @PathVariable String id, @Valid @RequestBody AnalyzeInput input) {
        return ai.analyze(auth.getName(), id, new AiService.AnalyzeInput(input.consentId(), input.symptomText(), input.idempotencyKey()));
    }
    @GetMapping("/patient/appointments/{id}/ai/results")
    List<AiService.ResultView> patientResults(JwtAuthenticationToken auth, @PathVariable String id) {
        return ai.patientResults(auth.getName(), id);
    }
    @PostMapping("/patient/appointments/{id}/ai/consents/{consentId}/revoke")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    void revoke(JwtAuthenticationToken auth, @PathVariable String id, @PathVariable String consentId) {
        ai.revoke(auth.getName(), id, consentId);
    }
    @GetMapping("/admin/ai/calls")
    List<AiMapper.CallMetadata> calls() { return ai.callMetadata(); }
    @PostMapping("/patient/ai/consents")
    AiService.ConsentView draftConsent(JwtAuthenticationToken auth, @Valid @RequestBody ConsentInput input) {
        return ai.consent(auth.getName(), null, new AiService.ConsentInput(input.accepted(), input.provider(), input.purpose(), input.scope(), input.symptomText()));
    }
    @PostMapping("/patient/ai/preconsultations")
    AiService.ResultView draftAnalyze(JwtAuthenticationToken auth, @Valid @RequestBody AnalyzeInput input) {
        return ai.analyze(auth.getName(), null, new AiService.AnalyzeInput(input.consentId(), input.symptomText(), input.idempotencyKey()));
    }
    @GetMapping("/patient/ai/results")
    List<AiService.ResultView> drafts(JwtAuthenticationToken auth) { return ai.patientResults(auth.getName(), null); }
    @PostMapping("/patient/ai/consents/{consentId}/revoke")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    void revokeDraft(JwtAuthenticationToken auth, @PathVariable String consentId) { ai.revoke(auth.getName(), null, consentId); }
    @GetMapping("/doctor/clinical/{id}/ai")
    List<AiService.ResultView> doctorResults(JwtAuthenticationToken auth, @PathVariable String id) {
        return ai.doctorResults(auth.getName(), id);
    }
    @GetMapping("/assistant/business")
    AiService.BusinessAnswer business(@RequestParam String question) { return ai.business(question); }
}
