package cn.medical.reception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import cn.medical.identity.ApiFailure;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ReceptionServiceTest {
    private static final LocalDate DATE = LocalDate.of(2026, 11, 7);

    @Test
    void restoreIsRejectedBeforeTheClinicStarts() {
        var mapper = mock(ReceptionMapper.class);
        var appointment = appointment(1_000L, 2_000L);
        when(mapper.patientAppointment("appointment", "patient")).thenReturn(appointment);
        when(mapper.lockedQueue("appointment")).thenReturn(entry("MISSED", 0));

        var service = new ReceptionService(mapper, Clock.fixed(Instant.ofEpochMilli(999L), ZoneOffset.UTC));

        assertThatThrownBy(() -> service.restore("patient", "appointment"))
            .isInstanceOfSatisfying(ApiFailure.class, failure -> assertThat(failure.code).isEqualTo("SESSION_ENDED"));
        verify(mapper, never()).restore(anyString(), anyLong());
    }

    @Test
    void completionCanFinishAfterTheClinicEndsOnceConsultationStarted() {
        var mapper = mock(ReceptionMapper.class);
        var inConsultation = entry("IN_CONSULTATION", 0);
        var completed = entry("COMPLETED", 0);
        when(mapper.queue("appointment")).thenReturn(inConsultation, completed);
        when(mapper.appointment("appointment")).thenReturn(appointment(1_000L, 2_000L));
        when(mapper.complete("appointment", 3_000L)).thenReturn(1);

        var service = new ReceptionService(mapper, Clock.fixed(Instant.ofEpochMilli(3_000L), ZoneOffset.UTC));

        var result = service.complete("doctor", "appointment");

        assertThat(result.state()).isEqualTo("COMPLETED");
        verify(mapper).complete("appointment", 3_000L);
    }

    @Test
    void doctorCanProcessCheckedInPatientDuringCheckInWindow() {
        var mapper = mock(ReceptionMapper.class);
        var waiting = entry("WAITING", 0);
        var called = entry("CALLED", 0);
        when(mapper.queue("appointment")).thenReturn(waiting, called);
        when(mapper.appointment("appointment")).thenReturn(appointment(1_000L, 2_000L));
        when(mapper.nextWaiting("schedule")).thenReturn(waiting);
        when(mapper.call("appointment", 1_000L - 29 * 60_000L)).thenReturn(1);

        var service = new ReceptionService(mapper, Clock.fixed(Instant.ofEpochMilli(1_000L - 29 * 60_000L), ZoneOffset.UTC));

        assertThat(service.call("doctor", "appointment").state()).isEqualTo("CALLED");
        verify(mapper).call("appointment", 1_000L - 29 * 60_000L);
    }

    private static ReceptionMapper.Appointment appointment(long startsAt, long endsAt) {
        return new ReceptionMapper.Appointment("appointment", "patient", "schedule", "doctor", DATE,
            "AM", "ONLINE", "ADVANCE", startsAt, endsAt, "CONFIRMED");
    }

    private static ReceptionMapper.QueueEntry entry(String state, int restorationCount) {
        return new ReceptionMapper.QueueEntry(1L, "appointment", "schedule", "doctor", "patient", "patient",
            DATE, "AM", "ONLINE", "ADVANCE", 1, 1_000L, 1_000L, restorationCount, state,
            null, null, null, null);
    }
}
