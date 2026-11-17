package cn.medical.governance;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import cn.medical.booking.BookingService;
import cn.medical.clinical.ClinicalService;
import cn.medical.identity.ApiFailure;
import java.time.*;
import org.junit.jupiter.api.Test;

class ReviewServiceTest {
    @Test void grantRequiresIndependentPermissionAndReasonAndRecordsExpiry() {
        var reviews = mock(ReviewMapper.class); var clinical = mock(ClinicalService.class); var booking = mock(BookingService.class);
        var policy = mock(ReviewPolicyService.class); var audit = mock(AuditService.class);
        when(booking.careScope("appointment")).thenReturn(new BookingService.CareScope("appointment","patient","doctor","ONLINE","COMPLETED"));
        var service = new ReviewService(reviews,clinical,booking,policy,audit,Clock.fixed(Instant.ofEpochMilli(1000L),ZoneOffset.UTC));
        assertThatThrownBy(() -> service.request("admin","appointment"," ","RECORD")).isInstanceOf(ApiFailure.class);
        var result = service.request("admin","appointment","处理争议内容","RECORD");
        assertThat(result.state()).isEqualTo("GRANTED"); assertThat(result.expiresAt()).isEqualTo(1000L+30*60000L);
        verify(audit).record(eq("admin"),eq("MEDICAL_REVIEW_GRANTED"),eq("REVIEW"),eq(result.id()),eq("处理争议内容"));
        doThrow(new ApiFailure(403,"REVIEW_PERMISSION_REQUIRED","需授权")).when(policy).requireReviewer("ordinary");
        assertThatThrownBy(() -> service.request("ordinary","appointment","理由","ALL")).isInstanceOf(ApiFailure.class);
        verify(reviews,times(1)).insertReview(any());
    }
    @Test void clearingRestrictionUsesLatestDecisionEvenAtSameClockInstant() {
        var reviews = mock(ReviewMapper.class);
        var policy = new ReviewPolicyService(reviews,mock(cn.medical.identity.IdentityService.class),mock(AuditService.class),Clock.systemUTC());
        when(reviews.latestFlag("a","MATERIAL","m")).thenReturn(new ReviewMapper.Flag("f","a","MATERIAL","m","admin","LIMITED","限制",1));
        assertThat(policy.display("a","MATERIAL","m","原文")).isEqualTo(ReviewPolicyService.LIMITED);
        when(reviews.latestFlag("a","MATERIAL","m")).thenReturn(new ReviewMapper.Flag("g","a","MATERIAL","m","admin","CLEARED","解除",1));
        assertThat(policy.display("a","MATERIAL","m","原文")).isEqualTo("原文");
    }
}
