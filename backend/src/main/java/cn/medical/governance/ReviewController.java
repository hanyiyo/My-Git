package cn.medical.governance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/medical")
public class ReviewController {
    private final ReviewService reviews;
    public ReviewController(ReviewService reviews) { this.reviews = reviews; }
    public record RequestInput(@NotBlank String appointmentId, @NotBlank @Size(max=1000) String reason, @NotBlank String scope) {}
    public record FlagInput(@NotBlank String appointmentId, @NotBlank String targetType, @NotBlank String targetId, @NotBlank String state, @NotBlank @Size(max=1000) String reason) {}
    @PostMapping("/reviews") ReviewService.ReviewView request(JwtAuthenticationToken auth, @Valid @RequestBody RequestInput input) { return reviews.request(auth.getName(), input.appointmentId(), input.reason(), input.scope()); }
    @GetMapping("/reviews/{id}/content") ReviewService.ReviewContent content(JwtAuthenticationToken auth, @PathVariable String id) { return reviews.content(auth.getName(), id); }
    @PostMapping("/flags") ReviewMapper.Flag flag(JwtAuthenticationToken auth, @Valid @RequestBody FlagInput input) { return reviews.flag(auth.getName(), input.appointmentId(), input.targetType(), input.targetId(), input.state(), input.reason()); }
    @GetMapping("/audits/{objectId}") List<ReviewMapper.Audit> audits(JwtAuthenticationToken auth, @PathVariable String objectId) { return reviews.audits(auth.getName(), objectId); }
}
