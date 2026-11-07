package cn.medical.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import cn.medical.clinical.ClinicalMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ReviewServiceTest {
    @Test
    void reviewGrantRequiresReasonAndRecordsAnExpiringAuthorization() {
        var reviews = mock(ReviewMapper.class);
        var clinical = mock(ClinicalMapper.class);
        when(clinical.appointment("appointment")).thenReturn(new ClinicalMapper.Appointment("appointment", "patient", "doctor", "ONLINE", "COMPLETED"));
        var service = new ReviewService(reviews, clinical, Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC));

        var result = service.request("admin", "appointment", "处理争议内容", "RECORD");

        assertThat(result.state()).isEqualTo("GRANTED");
        assertThat(result.expiresAt()).isEqualTo(1_000L + 30 * 60_000L);
        verify(reviews).insertReview(any(ReviewMapper.ReviewCase.class));
        verify(reviews).audit(anyString(), eq("admin"), eq("MEDICAL_REVIEW_GRANTED"), eq("REVIEW"), anyString(), eq("处理争议内容"), eq(1_000L));
    }
}
