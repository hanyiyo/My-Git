package cn.medical.governance;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import cn.medical.booking.BookingMapper;
import cn.medical.booking.BookingService;
import cn.medical.identity.ApiFailure;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class GovernanceServiceTest {
    @Test
    void ordinaryDoctorCannotSubmitSuspensionWithinTwentyFourHours() {
        var governance = mock(GovernanceMapper.class);
        var catalog = mock(cn.medical.catalog.CatalogService.class);
        var schedule = new cn.medical.catalog.CatalogMapper.Schedule("schedule", "doctor", "department", LocalDate.of(2026, 11, 7), "AM", "ONLINE", 100_000L, 200_000L, "Asia/Shanghai", 3, 1000, "PUBLISHED", 3);
        when(catalog.governanceSchedule("schedule")).thenReturn(schedule);
        var service = new GovernanceService(governance, mock(BookingService.class), catalog, mock(cn.medical.delivery.DeliveryService.class),
            Clock.fixed(Instant.ofEpochMilli(100_000L - 23 * 60 * 60 * 1000L), ZoneOffset.UTC), mock(org.springframework.transaction.PlatformTransactionManager.class), mock(AuditService.class));

        assertThatThrownBy(() -> service.submit("doctor", "schedule", "临时停诊"))
            .isInstanceOfSatisfying(ApiFailure.class, failure -> org.assertj.core.api.Assertions.assertThat(failure.code).isEqualTo("EMERGENCY_REQUIRED"));
        verify(governance, never()).pauseSchedule(anyString());
    }
}
