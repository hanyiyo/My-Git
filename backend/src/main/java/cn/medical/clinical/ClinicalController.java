package cn.medical.clinical;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class ClinicalController {
    private final ClinicalService clinical;
    public ClinicalController(ClinicalService clinical) { this.clinical = clinical; }
    public record MessageInput(@NotBlank @Size(max=10000) String body) {}
    public record ResultInput(@NotBlank @Size(max=20000) String body) {}
    public record CorrectionInput(@NotBlank @Size(max=500) String reason, @NotBlank @Size(max=20000) String body) {}

    @GetMapping("/patient/appointments/{id}/clinical")
    ClinicalService.ClinicalView patientView(JwtAuthenticationToken auth, @PathVariable String id) { return clinical.patientView(auth.getName(), id); }
    @PostMapping(value="/patient/appointments/{id}/clinical/materials", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    ClinicalService.ClinicalView materials(JwtAuthenticationToken auth, @PathVariable String id,
        @RequestParam(defaultValue="") String symptoms, @RequestParam(defaultValue="false") boolean shared,
        @RequestPart(name="files", required=false) MultipartFile[] files) { return clinical.addMaterials(auth.getName(), id, symptoms, shared, files); }
    @PostMapping("/patient/appointments/{id}/clinical/messages")
    ClinicalService.ClinicalView patientMessage(JwtAuthenticationToken auth, @PathVariable String id, @Valid @RequestBody MessageInput input) { return clinical.message(auth.getName(), id, "PATIENT", input.body()); }
    @GetMapping("/patient/clinical/attachments/{id}")
    ResponseEntity<ByteArrayResource> patientAttachment(JwtAuthenticationToken auth, @PathVariable String id) { return attachment(clinical.download(auth.getName(), "PATIENT", id)); }

    @GetMapping("/doctor/clinical/{id}")
    ClinicalService.ClinicalView doctorView(JwtAuthenticationToken auth, @PathVariable String id) { return clinical.doctorView(auth.getName(), id); }
    @PostMapping("/doctor/clinical/{id}/messages")
    ClinicalService.ClinicalView doctorMessage(JwtAuthenticationToken auth, @PathVariable String id, @Valid @RequestBody MessageInput input) { return clinical.message(auth.getName(), id, "DOCTOR", input.body()); }
    @PostMapping("/doctor/clinical/{id}/result")
    ClinicalService.ClinicalView result(JwtAuthenticationToken auth, @PathVariable String id, @Valid @RequestBody ResultInput input) { return clinical.submitResult(auth.getName(), id, input.body()); }
    @PostMapping("/doctor/clinical/{id}/corrections")
    ClinicalService.ClinicalView correction(JwtAuthenticationToken auth, @PathVariable String id, @Valid @RequestBody CorrectionInput input) { return clinical.correction(auth.getName(), id, input.reason(), input.body()); }
    @GetMapping("/doctor/clinical/attachments/{id}")
    ResponseEntity<ByteArrayResource> doctorAttachment(JwtAuthenticationToken auth, @PathVariable String id) { return attachment(clinical.download(auth.getName(), "DOCTOR", id)); }

    private ResponseEntity<ByteArrayResource> attachment(ClinicalService.Download download) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(download.mediaType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(download.originalName()).build().toString())
            .body(download.resource());
    }
}
