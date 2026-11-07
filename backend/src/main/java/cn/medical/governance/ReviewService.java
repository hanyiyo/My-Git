package cn.medical.governance;

import cn.medical.clinical.ClinicalMapper;
import cn.medical.identity.ApiFailure;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {
    public record MaterialContent(String id, int versionNo, String body, boolean shared, List<String> attachments) {}
    public record ResultContent(String id, int versionNo, String body, String encounterType, long createdAt) {}
    public record ReviewContent(String reviewId, String appointmentId, String scope, List<MaterialContent> materials, List<ResultContent> results, List<ReviewMapper.Flag> flags) {}
    public record ReviewView(String id, String appointmentId, String scope, String state, long expiresAt) {}

    private final ReviewMapper reviews;
    private final ClinicalMapper clinical;
    private final Clock clock;
    public ReviewService(ReviewMapper reviews, ClinicalMapper clinical, Clock clock) { this.reviews = reviews; this.clinical = clinical; this.clock = clock; }

    @Transactional
    public ReviewView request(String adminId, String appointmentId, String reason, String scope) {
        if (reason == null || reason.isBlank() || reason.length() > 1000 || !Set.of("RECORD", "ATTACHMENTS", "ALL").contains(scope))
            throw new ApiFailure(400, "INVALID_INPUT", "审核理由和访问范围不能为空");
        if (clinical.appointment(appointmentId) == null) throw new ApiFailure(404, "NOT_FOUND", "问诊不存在");
        long now = clock.millis(); String id = UUID.randomUUID().toString();
        reviews.insertReview(new ReviewMapper.ReviewCase(id, appointmentId, adminId, reason.trim(), scope, "GRANTED", now, now + Duration.ofMinutes(30).toMillis()));
        audit(adminId, "MEDICAL_REVIEW_GRANTED", "REVIEW", id, reason);
        return new ReviewView(id, appointmentId, scope, "GRANTED", now + Duration.ofMinutes(30).toMillis());
    }

    @Transactional(readOnly = true)
    public ReviewContent content(String adminId, String reviewId) {
        var review = requireActiveReview(adminId, reviewId);
        var flags = reviews.flags(review.appointmentId());
        var materials = clinical.materials(review.appointmentId()).stream().map(material -> new MaterialContent(material.id(), material.versionNo(),
            limited(flags, "MATERIAL", material.id()) ? "[内容已限制展示]" : material.symptomText(), material.shared(),
            clinical.attachments(material.id()).stream().map(ClinicalMapper.Attachment::originalName).toList())).toList();
        var results = clinical.results(review.appointmentId()).stream().map(result -> new ResultContent(result.id(), result.versionNo(),
            limited(flags, "RESULT", result.id()) ? "[内容已限制展示]" : result.body(), result.encounterType(), result.createdAt())).toList();
        audit(adminId, "MEDICAL_CONTENT_VIEWED", "REVIEW", reviewId, review.reason());
        return new ReviewContent(review.id(), review.appointmentId(), review.scope(), materials, results, flags);
    }

    @Transactional
    public ReviewMapper.Flag flag(String adminId, String appointmentId, String targetType, String targetId, String state, String reason) {
        if (!Set.of("MATERIAL", "RESULT").contains(targetType) || !Set.of("LIMITED", "CLEARED").contains(state) || reason == null || reason.isBlank())
            throw new ApiFailure(400, "INVALID_INPUT", "问题内容标记参数无效");
        if (clinical.appointment(appointmentId) == null) throw new ApiFailure(404, "NOT_FOUND", "问诊不存在");
        var value = new ReviewMapper.Flag(UUID.randomUUID().toString(), appointmentId, targetType, targetId, adminId, state, reason.trim(), clock.millis());
        reviews.insertFlag(value); audit(adminId, "MEDICAL_CONTENT_FLAGGED", targetType, targetId, reason); return value;
    }

    @Transactional(readOnly = true)
    public List<ReviewMapper.Audit> audits(String adminId, String objectId) { return reviews.audits(objectId); }

    private ReviewMapper.ReviewCase requireActiveReview(String adminId, String id) {
        var review = reviews.review(id, adminId);
        if (review == null) throw new ApiFailure(404, "NOT_FOUND", "审核授权不存在");
        if (!review.state().equals("GRANTED") || review.expiresAt() <= clock.millis()) {
            reviews.updateReviewState(id, "EXPIRED"); throw new ApiFailure(403, "REVIEW_EXPIRED", "审核授权已撤回或过期");
        }
        return review;
    }
    private boolean limited(List<ReviewMapper.Flag> flags, String type, String id) { return flags.stream().anyMatch(flag -> flag.targetType().equals(type) && flag.targetId().equals(id) && flag.state().equals("LIMITED")); }
    private void audit(String actor, String action, String type, String id, String reason) { reviews.audit(UUID.randomUUID().toString(), actor, action, type, id, reason == null ? "" : reason, clock.millis()); }
}
