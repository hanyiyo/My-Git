package cn.medical.governance;

import cn.medical.booking.BookingService;
import cn.medical.clinical.ClinicalService;
import cn.medical.identity.ApiFailure;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {
    public record ReviewView(String id, String appointmentId, String scope, String state, long expiresAt) {}
    public record ReviewContent(String reviewId, String appointmentId, String scope, ClinicalService.ReviewSnapshot content, List<ReviewMapper.Flag> flags) {}
    private final ReviewMapper reviews;
    private final ClinicalService clinical;
    private final BookingService booking;
    private final ReviewPolicyService policy;
    private final AuditService audit;
    private final Clock clock;
    public ReviewService(ReviewMapper reviews, ClinicalService clinical, BookingService booking, ReviewPolicyService policy, AuditService audit, Clock clock) {
        this.reviews = reviews; this.clinical = clinical; this.booking = booking; this.policy = policy; this.audit = audit; this.clock = clock;
    }
    @Transactional
    public ReviewView request(String admin, String appointment, String reason, String scope) {
        policy.requireReviewer(admin); ReviewPolicyService.validateReason(reason);
        if (!Set.of("RECORD", "ATTACHMENTS", "ALL").contains(Objects.toString(scope, ""))) throw new ApiFailure(400, "INVALID_INPUT", "请选择正文、附件或全部范围");
        if (booking.careScope(appointment) == null) throw new ApiFailure(404, "NOT_FOUND", "问诊不存在");
        long now = clock.millis(); String id = UUID.randomUUID().toString(); long expires = now + Duration.ofMinutes(30).toMillis();
        reviews.insertReview(new ReviewMapper.ReviewCase(id, appointment, admin, reason.trim(), scope, "GRANTED", now, expires));
        audit.record(admin, "MEDICAL_REVIEW_GRANTED", "REVIEW", id, reason.trim());
        return new ReviewView(id, appointment, scope, "GRANTED", expires);
    }
    @Transactional
    public ReviewContent content(String admin, String id) {
        var review = policy.active(admin, id, null);
        var snapshot = clinical.reviewSnapshot(admin, id);
        audit.record(admin, "MEDICAL_CONTENT_VIEWED", "REVIEW", id, review.reason());
        var flags = reviews.flags(review.appointmentId()).stream().filter(f -> review.scope().equals("ALL") || (review.scope().equals("ATTACHMENTS") == f.targetType().equals("ATTACHMENT"))).toList();
        return new ReviewContent(id, review.appointmentId(), review.scope(), snapshot, flags);
    }
    @Transactional
    public ReviewMapper.Flag flag(String admin, String reviewId, String appointment, String type, String target, String state, String reason) {
        ReviewPolicyService.validateReason(reason);
        if (!Set.of("MATERIAL", "RESULT", "MESSAGE", "CORRECTION", "ATTACHMENT").contains(Objects.toString(type, "")) || !Set.of("LIMITED", "CLEARED").contains(Objects.toString(state, "")))
            throw new ApiFailure(400, "INVALID_INPUT", "问题内容标记参数无效");
        var review = policy.active(admin, reviewId, type.equals("ATTACHMENT") ? "ATTACHMENTS" : "RECORD");
        if (!review.appointmentId().equals(appointment) || !clinical.ownsReviewTarget(admin, reviewId, type, target)) throw new ApiFailure(404, "NOT_FOUND", "内容不属于此次审核授权");
        var flag = new ReviewMapper.Flag(UUID.randomUUID().toString(), appointment, type, target, admin, state, reason.trim(), clock.millis());
        reviews.insertFlag(flag); audit.record(admin, "MEDICAL_CONTENT_" + state, type, target, reason.trim()); return flag;
    }
    @Transactional
    public void revoke(String admin, String id, String reason) {
        ReviewPolicyService.validateReason(reason);
        var review = reviews.review(id, admin);
        if (review == null) throw new ApiFailure(404, "NOT_FOUND", "审核授权不存在");
        if (review.state().equals("REVOKED")) return;
        reviews.updateReviewState(id, "REVOKED"); audit.record(admin, "MEDICAL_REVIEW_REVOKED", "REVIEW", id, reason.trim());
    }
    @Transactional(readOnly = true)
    public List<ReviewMapper.Audit> audits(String admin, String id) {
        return reviews.audits(id).stream().map(e -> new ReviewMapper.Audit(e.id(),e.actorId(),e.action(),e.objectType(),e.objectId(),"[理由已留存于受控审计]",e.createdAt())).toList();
    }
    public List<BookingService.ReviewAppointment> appointments() { return booking.adminAppointments(); }
}
