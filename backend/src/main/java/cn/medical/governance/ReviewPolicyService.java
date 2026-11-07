package cn.medical.governance;

import cn.medical.identity.ApiFailure;
import cn.medical.identity.IdentityService;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewPolicyService {
    public static final String LIMITED = "[内容已限制展示]";
    private final ReviewMapper reviews;
    private final IdentityService identity;
    private final AuditService audit;
    private final Clock clock;
    public ReviewPolicyService(ReviewMapper reviews, IdentityService identity, AuditService audit, Clock clock) {
        this.reviews = reviews; this.identity = identity; this.audit = audit; this.clock = clock;
    }
    @Transactional
    public void permission(String admin, String identifier, boolean enabled, String reason) {
        validateReason(reason);
        var target = identity.administrator(identifier);
        if (admin.equals(target.id()) && enabled) throw new ApiFailure(403, "SELF_AUTHORIZATION_FORBIDDEN", "医疗审核权限必须由另一位管理员独立授权");
        reviews.permission(target.id(), admin, enabled, reason.trim(), clock.millis());
        if (!enabled) reviews.revokeForReviewer(target.id());
        audit.record(admin, enabled ? "REVIEWER_AUTHORIZED" : "REVIEWER_REVOKED", "ACCOUNT", target.id(), reason.trim());
    }
    public boolean authorized(String actor) { return reviews.permissionEnabled(actor); }
    public void requireReviewer(String actor) {
        if (!authorized(actor)) throw new ApiFailure(403, "REVIEW_PERMISSION_REQUIRED", "请先由另一位管理员独立授予医疗审核权限");
    }
    public ReviewMapper.ReviewCase active(String actor, String id, String scope) {
        requireReviewer(actor);
        var review = reviews.review(id, actor);
        if (review == null) throw new ApiFailure(404, "NOT_FOUND", "审核授权不存在");
        if (!review.state().equals("GRANTED") || review.expiresAt() <= clock.millis())
            throw new ApiFailure(403, "REVIEW_EXPIRED", "审核授权已撤回或过期，请重新申请并填写理由");
        if (scope != null && !review.scope().equals("ALL") && !review.scope().equals(scope))
            throw new ApiFailure(403, "REVIEW_SCOPE_DENIED", "此次授权不包含所请求的内容范围");
        return review;
    }
    public boolean limited(String appointment, String type, String id) {
        var flag = reviews.latestFlag(appointment, type, id);
        return flag != null && flag.state().equals("LIMITED");
    }
    public String display(String appointment, String type, String id, String body) { return limited(appointment, type, id) ? LIMITED : body; }
    public void requireVisible(String appointment, String type, String id) {
        if (limited(appointment, type, id)) throw new ApiFailure(403, "CONTENT_LIMITED", "此内容已限制展示，请联系人工处理");
    }
    public static void validateReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 1000) throw new ApiFailure(400, "INVALID_INPUT", "请填写不超过1000字的操作理由");
    }
}
