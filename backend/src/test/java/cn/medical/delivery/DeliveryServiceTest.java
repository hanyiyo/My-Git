package cn.medical.delivery;

import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DeliveryServiceTest {
    @Test
    void publishUsesDedupeKeyAndMarksThePersistedEventSent() {
        var mapper = mock(DeliveryMapper.class);
        when(mapper.byDedupe("appointment-confirmed:1")).thenReturn(new DeliveryMapper.Notification(7L, "patient", "APPOINTMENT_CONFIRMED", "1", "已确认", "appointment-confirmed:1", "PENDING", 0, null, 1_000L, 1_000L, null));
        var service = new DeliveryService(mapper, Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC));

        service.publish("patient", "APPOINTMENT_CONFIRMED", "1", "已确认", "appointment-confirmed:1");

        verify(mapper).delivery(7L, "SENT", 1, null, 1_000L);
    }
}
