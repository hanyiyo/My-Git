package cn.medical.governance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.core.io.ByteArrayResource;
import cn.medical.clinical.ClinicalService;

@RestController
@RequestMapping("/api/v1/admin/medical")
public class ReviewController {
    private final ReviewService reviews;
    private final ReviewPolicyService policy;
    private final ClinicalService clinical;
    private final AuditService audit;
    public ReviewController(ReviewService reviews, ReviewPolicyService policy, ClinicalService clinical, AuditService audit) { this.reviews = reviews; this.policy = policy; this.clinical = clinical; this.audit = audit; }
    public record RequestInput(@NotBlank String appointmentId, @NotBlank @Size(max=1000) String reason, @NotBlank String scope) {}
    public record FlagInput(@NotBlank String reviewId, @NotBlank String appointmentId, @NotBlank String targetType, @NotBlank String targetId, @NotBlank String state, @NotBlank @Size(max=1000) String reason) {}
    public record PermissionInput(@NotBlank String identifier, boolean enabled, @NotBlank @Size(max=1000) String reason) {}
    public record ReasonInput(@NotBlank @Size(max=1000) String reason) {}
    @GetMapping("/permission") boolean permission(JwtAuthenticationToken auth) { return policy.authorized(auth.getName()); }
    @PostMapping("/permissions") @ResponseStatus(HttpStatus.NO_CONTENT) void permission(JwtAuthenticationToken auth, @Valid @RequestBody PermissionInput input) { policy.permission(auth.getName(),input.identifier(),input.enabled(),input.reason()); }
    @GetMapping("/appointments") List<cn.medical.booking.BookingService.ReviewAppointment> appointments() { return reviews.appointments(); }
    @PostMapping("/reviews") ReviewService.ReviewView request(JwtAuthenticationToken auth, @Valid @RequestBody RequestInput input) { return reviews.request(auth.getName(), input.appointmentId(), input.reason(), input.scope()); }
    @GetMapping("/reviews/{id}/content") ReviewService.ReviewContent content(JwtAuthenticationToken auth, @PathVariable String id) { return reviews.content(auth.getName(), id); }
    @PostMapping("/flags") ReviewMapper.Flag flag(JwtAuthenticationToken auth, @Valid @RequestBody FlagInput input) { return reviews.flag(auth.getName(), input.reviewId(), input.appointmentId(), input.targetType(), input.targetId(), input.state(), input.reason()); }
    @PostMapping("/reviews/{id}/revoke") @ResponseStatus(HttpStatus.NO_CONTENT) void revoke(JwtAuthenticationToken auth, @PathVariable String id, @Valid @RequestBody ReasonInput input) { reviews.revoke(auth.getName(),id,input.reason()); }
    @GetMapping("/reviews/{id}/attachments/{attachment}") ResponseEntity<ByteArrayResource> attachment(JwtAuthenticationToken auth, @PathVariable String id, @PathVariable String attachment) {
        var file = clinical.reviewDownload(auth.getName(),id,attachment);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.mediaType())).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(file.originalName()).build().toString()).body(file.resource());
    }
    @GetMapping("/audit-integrity") AuditService.Integrity integrity() { return audit.verify(); }
    @GetMapping("/audits/{objectId}") List<ReviewMapper.Audit> audits(JwtAuthenticationToken auth, @PathVariable String objectId) { return reviews.audits(auth.getName(), objectId); }
}
