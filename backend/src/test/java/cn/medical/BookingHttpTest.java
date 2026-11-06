package cn.medical;

import java.time.*;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"medical.captcha-ttl=2m", "spring.data.redis.database=5"})
@Import({TestAnswers.class, BookingHttpTest.TimeControl.class})
class BookingHttpTest extends HttpFixture {
    @LocalServerPort int port;
    Browser administrator;
    @Autowired AdjustableClock clock;
    @BeforeEach @AfterEach void resetClock() { clock.now = Instant.now(); }
    static class AdjustableClock extends Clock {
        volatile Instant now = Instant.now();
        public ZoneId getZone() { return ZoneId.of("UTC"); }
        public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        public Instant instant() { return now; }
    }
    @TestConfiguration static class TimeControl {
        @Bean @Primary AdjustableClock adjustableClock() { return new AdjustableClock(); }
    }

    Browser patient() throws Exception {
        var browser = new Browser(port); var name = identifier();
        assertThat(browser.register(name, "PATIENT").status()).isEqualTo(201);
        assertThat(browser.login(name, PASSWORD).status()).isEqualTo(200);
        return browser;
    }
    record Published(String id, String doctorId, long startsAt, long endsAt) {}
    Browser administrator() throws Exception {
        if (administrator == null) {
            administrator = new Browser(port);
            assertThat(administrator.login("admin-foundation-test", "fictional-test-administrator-password").status()).isEqualTo(200);
        }
        return administrator;
    }
    Published published(String mode, String session, int capacity) throws Exception {
        var admin = administrator();
        var department = admin.call("POST", "/api/v1/admin/catalog/departments",
            Map.of("name", identifier(), "description", "虚构门诊", "active", true)).body().path("id").asText();
        var doctor = new Browser(port);
        var doctorId = doctor.register(identifier(), "DOCTOR").body().path("account").path("id").asText();
        assertThat(admin.call("PUT", "/api/v1/admin/catalog/doctors/" + doctorId,
            Map.of("displayName", "虚构李医生", "title", "主治医师", "biography", "演示", "departmentId", department, "credentialStatus", "CERTIFIED")).status()).isEqualTo(200);
        var date = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(2);
        var result = admin.call("POST", "/api/v1/admin/catalog/schedules", Map.of("doctorId", doctorId,
            "departmentId", department, "clinicDate", date.toString(), "session", session, "serviceMode", mode, "capacity", capacity, "feeMinor", 2000));
        assertThat(result.status()).isEqualTo(201);
        return new Published(result.body().path("id").asText(), doctorId, result.body().path("startsAt").asLong(), result.body().path("endsAt").asLong());
    }
    Map<String, String> command(Published schedule, String key) {
        return Map.of("scheduleId", schedule.id(), "source", "ADVANCE", "idempotencyKey", key);
    }
    List<Reply> concurrently(List<Browser> browsers, List<Published> schedules) throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var ready = new CountDownLatch(browsers.size()); var start = new CountDownLatch(1);
            var tasks = java.util.stream.IntStream.range(0, browsers.size()).mapToObj(index -> executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return browsers.get(index).call("POST", "/api/v1/patient/appointments", command(schedules.get(index), identifier()));
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            var replies = new ArrayList<Reply>();
            for (var task : tasks) replies.add(task.get(20, TimeUnit.SECONDS));
            return replies;
        }
    }
    @Test void sixPatientsCompetingForOneSlotCreateExactlyOneHold() throws Exception {
        var schedule = published("ONLINE", "AM", 1);
        var patients = new ArrayList<Browser>();
        for (int index = 0; index < 6; index++) patients.add(patient());
        var replies = concurrently(patients, java.util.Collections.nCopies(6, schedule));
        assertThat(replies).filteredOn(reply -> reply.status() == 201).hasSize(1);
        assertThat(replies).filteredOn(reply -> reply.status() == 409).hasSize(5);
        assertThat(replies).filteredOn(reply -> reply.status() == 409)
            .allSatisfy(reply -> assertThat(reply.body().path("error").path("code").asText()).isEqualTo("NO_SLOTS"));
        var publicSchedules = patients.get(0).call("GET", "/api/v1/catalog/schedules?doctorId=" + schedule.doctorId(), null);
        assertThat(publicSchedules.body().size()).isZero();
    }
    @Test void onePatientCompetingAcrossDoctorsAndModesHoldsOnlyOneHalfDayRegistration() throws Exception {
        var schedules = new ArrayList<Published>();
        for (int index = 0; index < 6; index++) schedules.add(published(index % 2 == 0 ? "ONLINE" : "OFFLINE", "AM", 1));
        var patient = patient(); var contexts = new ArrayList<Browser>();
        for (int index = 0; index < 6; index++) {
            var context = new Browser(port); context.access = patient.access; contexts.add(context);
        }
        var replies = concurrently(contexts, schedules);
        assertThat(replies).filteredOn(reply -> reply.status() == 201).hasSize(1);
        assertThat(replies).filteredOn(reply -> reply.status() == 409).hasSize(5);
        assertThat(replies).filteredOn(reply -> reply.status() == 409)
            .allSatisfy(reply -> assertThat(reply.body().path("error").path("code").asText()).isEqualTo("REGISTRATION_CONFLICT"));
        int available = 0;
        for (var schedule : schedules) {
            var response = patient.call("GET", "/api/v1/catalog/schedules?doctorId=" + schedule.doctorId(), null);
            if (!response.body().isEmpty()) available += response.body().get(0).path("availableSlots").asInt();
        }
        assertThat(available).isEqualTo(5);
    }
    @Test void patientCannotReadForeignOrderAndOtherRolesCannotCreatePatientRegistration() throws Exception {
        var schedule = published("ONLINE", "AM", 2);
        var owner = patient(); var stranger = patient();
        var created = owner.call("POST", "/api/v1/patient/appointments", command(schedule, identifier()));
        assertThat(created.status()).isEqualTo(201);
        String path = "/api/v1/patient/appointments/" + created.body().path("id").asText();
        assertThat(owner.call("GET", path, null).status()).isEqualTo(200);
        assertThat(stranger.call("GET", path, null).status()).isEqualTo(404);
        var doctor = new Browser(port); String name = identifier();
        assertThat(doctor.register(name, "DOCTOR").status()).isEqualTo(201);
        assertThat(doctor.login(name, PASSWORD).status()).isEqualTo(200);
        var admin = new Browser(port);
        assertThat(admin.login("admin-foundation-test", "fictional-test-administrator-password").status()).isEqualTo(200);
        for (var nonPatient : List.of(doctor, admin)) {
            assertThat(nonPatient.call("POST", "/api/v1/patient/appointments", command(schedule, identifier())).status()).isEqualTo(403);
            assertThat(nonPatient.call("GET", path, null).status()).isEqualTo(403);
        }
        assertThat(new Browser(port).call("GET", path, null).status()).isEqualTo(401);
    }
    @Test void repeatSubmissionReturnsOriginalOrderAndChangedPayloadCannotReuseKey() throws Exception {
        var first = published("ONLINE", "AM", 2); var second = published("OFFLINE", "AM", 2);
        var patient = patient(); var key = identifier();
        var created = patient.call("POST", "/api/v1/patient/appointments", command(first, key));
        assertThat(created.status()).isEqualTo(201);
        var repeated = patient.call("POST", "/api/v1/patient/appointments", command(first, key));
        assertThat(repeated.status()).isEqualTo(201);
        assertThat(repeated.body()).isEqualTo(created.body());
        var changed = patient.call("POST", "/api/v1/patient/appointments", command(second, key));
        assertThat(changed.status()).isEqualTo(409);
        assertThat(changed.body().path("error").path("code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
        var changedSource = patient.call("POST", "/api/v1/patient/appointments",
            Map.of("scheduleId", first.id(), "source", "WALK_IN", "idempotencyKey", key));
        assertThat(changedSource.status()).isEqualTo(409);
        assertThat(changedSource.body().path("error").path("code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
        var available = patient.call("GET", "/api/v1/catalog/schedules?doctorId=" + first.doctorId(), null);
        assertThat(available.body().get(0).path("availableSlots").asInt()).isEqualTo(1);
    }
    @Test void expirationAtExactDeadlineReusesSlotAndPatientGuardWithoutRevivingOldOrder() throws Exception {
        var first = published("ONLINE", "AM", 1); var other = published("OFFLINE", "AM", 1);
        var patientA = patient(); var patientB = patient(); String key = identifier();
        var old = patientA.call("POST", "/api/v1/patient/appointments", command(first, key));
        assertThat(old.status()).isEqualTo(201);
        long expiry = old.body().path("holdExpiresAt").asLong();
        clock.now = Instant.ofEpochMilli(expiry - 1);
        assertThat(patientB.call("POST", "/api/v1/patient/appointments", command(first, identifier())).status()).isEqualTo(409);
        clock.now = Instant.ofEpochMilli(expiry);
        // No scanner or owner query ran before the competing patient's submission.
        var replacement = patientB.call("POST", "/api/v1/patient/appointments", command(first, identifier()));
        assertThat(replacement.status()).isEqualTo(201);
        assertThat(replacement.body().path("slotId").asText()).isEqualTo(old.body().path("slotId").asText());
        var newOrder = patientA.call("POST", "/api/v1/patient/appointments", command(other, identifier()));
        assertThat(newOrder.status()).isEqualTo(201);
        var repeatedOld = patientA.call("POST", "/api/v1/patient/appointments", command(first, key));
        assertThat(repeatedOld.status()).isEqualTo(201);
        assertThat(repeatedOld.body().path("state").asText()).isEqualTo("EXPIRED");
        assertThat(repeatedOld.body().path("id").asText()).isEqualTo(old.body().path("id").asText());
        var queriedOld = patientA.call("GET", "/api/v1/patient/appointments/" + old.body().path("id").asText(), null);
        assertThat(queriedOld.body().path("state").asText()).isEqualTo("EXPIRED");
        assertThat(patientB.call("GET", "/api/v1/patient/appointments/" + replacement.body().path("id").asText(), null).body().path("state").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(patientA.call("POST", "/api/v1/patient/appointments", command(first, identifier())).status()).isEqualTo(409);
    }
    @Test void offlineWalkInUsesCurrentSessionAndEarlierOfTenMinutesOrCheckInCutoff() throws Exception {
        var offline = published("OFFLINE", "PM", 3); var online = published("ONLINE", "PM", 1);
        var atOpening = patient(); var at1735 = patient(); var at1740 = patient(); var rejected = patient();
        clock.now = Instant.ofEpochMilli(offline.startsAt());
        var advance = rejected.call("POST", "/api/v1/patient/appointments", command(offline, identifier()));
        assertThat(advance.status()).isEqualTo(409);
        assertThat(advance.body().path("error").path("code").asText()).isEqualTo("REGISTRATION_CLOSED");
        var opening = atOpening.call("POST", "/api/v1/patient/appointments",
            Map.of("scheduleId", offline.id(), "source", "WALK_IN", "idempotencyKey", identifier()));
        assertThat(opening.status()).isEqualTo(201);
        assertThat(opening.body().path("source").asText()).isEqualTo("WALK_IN");
        assertThat(opening.body().path("holdExpiresAt").asLong()).isEqualTo(offline.startsAt() + 600_000);
        long cutoff = offline.endsAt() - 900_000; // PM ends18:00, first check-in stops17:45.
        clock.now = Instant.ofEpochMilli(cutoff - 600_000); //17:35 ->17:45 (ten minutes).
        var tenMinutes = at1735.call("POST", "/api/v1/patient/appointments",
            Map.of("scheduleId", offline.id(), "source", "WALK_IN", "idempotencyKey", identifier()));
        assertThat(tenMinutes.status()).isEqualTo(201);
        assertThat(tenMinutes.body().path("holdExpiresAt").asLong()).isEqualTo(cutoff);
        clock.now = Instant.ofEpochMilli(cutoff - 300_000); //17:40 ->17:45 (five minutes).
        var fiveMinutes = at1740.call("POST", "/api/v1/patient/appointments",
            Map.of("scheduleId", offline.id(), "source", "WALK_IN", "idempotencyKey", identifier()));
        assertThat(fiveMinutes.status()).isEqualTo(201);
        assertThat(fiveMinutes.body().path("holdExpiresAt").asLong()).isEqualTo(cutoff);
        assertThat(rejected.call("POST", "/api/v1/patient/appointments",
            Map.of("scheduleId", online.id(), "source", "WALK_IN", "idempotencyKey", identifier())).status()).isEqualTo(409);
        clock.now = Instant.ofEpochMilli(cutoff);
        var closed = rejected.call("POST", "/api/v1/patient/appointments",
            Map.of("scheduleId", offline.id(), "source", "WALK_IN", "idempotencyKey", identifier()));
        assertThat(closed.status()).isEqualTo(409);
        assertThat(closed.body().path("error").path("code").asText()).isEqualTo("REGISTRATION_CLOSED");
        clock.now = Instant.ofEpochMilli(offline.startsAt() - 1);
        assertThat(rejected.call("POST", "/api/v1/patient/appointments",
            Map.of("scheduleId", offline.id(), "source", "WALK_IN", "idempotencyKey", identifier())).status()).isEqualTo(409);
        clock.now = Instant.ofEpochMilli(offline.endsAt() + 1);
        assertThat(rejected.call("POST", "/api/v1/patient/appointments",
            Map.of("scheduleId", offline.id(), "source", "WALK_IN", "idempotencyKey", identifier())).status()).isEqualTo(409);
    }
    @Test void simulatedPaymentConfirmsSnapshotAndCredentialOnceAndRetainsSlotAfterHoldDeadline() throws Exception {
        var schedule = published("ONLINE", "AM", 1);
        var owner = patient(); var stranger = patient();
        var created = owner.call("POST", "/api/v1/patient/appointments", command(schedule, identifier()));
        assertThat(created.status()).isEqualTo(201);
        String id = created.body().path("id").asText();
        String payPath = "/api/v1/patient/appointments/" + id + "/simulate-payment";
        assertThat(stranger.call("POST", payPath, null).status()).isEqualTo(404);
        var paid = owner.call("POST", payPath, null);
        assertThat(paid.status()).isEqualTo(200);
        assertThat(paid.body().path("appointment").path("id").asText()).isEqualTo(id);
        assertThat(paid.body().path("appointment").path("state").asText()).isEqualTo("CONFIRMED");
        assertThat(paid.body().path("appointment").path("feeMinor").asLong()).isEqualTo(2000);
        assertThat(paid.body().path("appointment").path("credential").asText()).isNotBlank();
        assertThat(paid.body().path("payment").path("id").asText()).isNotBlank();
        assertThat(paid.body().path("payment").path("appointmentId").asText()).isEqualTo(id);
        assertThat(paid.body().path("payment").path("amountMinor").asLong()).isEqualTo(2000);
        assertThat(paid.body().path("payment").path("state").asText()).isEqualTo("SUCCEEDED");
        assertThat(paid.body().path("payment").path("mode").asText()).isEqualTo("SIMULATED");
        var repeated = owner.call("POST", payPath, null);
        assertThat(repeated.status()).isEqualTo(200);
        assertThat(repeated.body()).isEqualTo(paid.body());
        clock.now = Instant.ofEpochMilli(created.body().path("holdExpiresAt").asLong() + 1);
        var queried = owner.call("GET", "/api/v1/patient/appointments/" + id, null);
        assertThat(queried.body()).isEqualTo(paid.body().path("appointment"));
        assertThat(owner.call("GET", "/api/v1/catalog/schedules?doctorId=" + schedule.doctorId(), null).body().size()).isZero();
        assertThat(stranger.call("POST", "/api/v1/patient/appointments", command(schedule, identifier())).status()).isEqualTo(409);
        assertThat(owner.call("POST", payPath, null).body()).isEqualTo(paid.body());
    }
    @Test void cancellingBeforeOpeningReleasesHoldAndCreatesOneRefundOnlyForSuccessfulPayment() throws Exception {
        var unpaidSchedule = published("ONLINE", "AM", 1);
        var replacementSchedule = published("OFFLINE", "AM", 1);
        var paidSchedule = published("ONLINE", "AM", 1);
        var startedSchedule = published("OFFLINE", "PM", 1);
        var unpaidOwner = patient(); var paidOwner = patient(); var stranger = patient(); var startedOwner = patient();
        var unpaid = unpaidOwner.call("POST", "/api/v1/patient/appointments", command(unpaidSchedule, identifier()));
        assertThat(unpaid.status()).isEqualTo(201);
        String unpaidPath = "/api/v1/patient/appointments/" + unpaid.body().path("id").asText();
        assertThat(stranger.call("POST", unpaidPath + "/cancel", null).status()).isEqualTo(404);
        var cancelledUnpaid = unpaidOwner.call("POST", unpaidPath + "/cancel", null);
        assertThat(cancelledUnpaid.status()).isEqualTo(200);
        assertThat(cancelledUnpaid.body().path("state").asText()).isEqualTo("CANCELLED");
        assertThat(unpaidOwner.call("POST", unpaidPath + "/cancel", null).body()).isEqualTo(cancelledUnpaid.body());
        var noPayment = unpaidOwner.call("GET", unpaidPath + "/billing", null);
        assertThat(noPayment.status()).isEqualTo(200);
        assertThat(noPayment.body().path("payment").isNull()).isTrue();
        assertThat(noPayment.body().path("refund").isNull()).isTrue();
        // Simulate a successful result arriving after cancellation, rather than reopening payment.
        assertThat(unpaidOwner.call("POST", unpaidPath + "/simulate-payment", null).status()).isEqualTo(200);
        var cancelledLatePayment = unpaidOwner.call("GET", unpaidPath + "/billing", null);
        assertThat(cancelledLatePayment.body().path("refund").path("reason").asText()).isEqualTo("LATE_PAYMENT");
        assertThat(unpaidOwner.call("GET", unpaidPath, null).body().path("state").asText()).isEqualTo("CANCELLED");
        assertThat(unpaidOwner.call("POST", "/api/v1/patient/appointments", command(replacementSchedule, identifier())).status()).isEqualTo(201);
        assertThat(stranger.call("POST", "/api/v1/patient/appointments", command(unpaidSchedule, identifier())).status()).isEqualTo(201);

        var paidOrder = paidOwner.call("POST", "/api/v1/patient/appointments", command(paidSchedule, identifier()));
        assertThat(paidOrder.status()).isEqualTo(201);
        String paidPath = "/api/v1/patient/appointments/" + paidOrder.body().path("id").asText();
        var paid = paidOwner.call("POST", paidPath + "/simulate-payment", null);
        assertThat(paid.status()).isEqualTo(200);
        var cancelledPaid = paidOwner.call("POST", paidPath + "/cancel", null);
        assertThat(cancelledPaid.status()).isEqualTo(200);
        assertThat(cancelledPaid.body().path("state").asText()).isEqualTo("CANCELLED");
        assertThat(cancelledPaid.body().path("credential")).isEqualTo(paid.body().path("appointment").path("credential"));
        var finances = paidOwner.call("GET", paidPath + "/billing", null);
        assertThat(finances.status()).isEqualTo(200);
        assertThat(finances.body().path("payment")).isEqualTo(paid.body().path("payment"));
        assertThat(finances.body().path("payment").path("state").asText()).isEqualTo("SUCCEEDED");
        var refund = finances.body().path("refund");
        assertThat(refund.path("id").asText()).isNotBlank();
        assertThat(refund.path("paymentId")).isEqualTo(paid.body().path("payment").path("id"));
        assertThat(refund.path("amountMinor").asLong()).isEqualTo(2000);
        assertThat(refund.path("state").asText()).isEqualTo("REFUND_PENDING");
        assertThat(refund.path("reason").asText()).isEqualTo("PATIENT_CANCELLATION");
        assertThat(paidOwner.call("POST", paidPath + "/cancel", null).body()).isEqualTo(cancelledPaid.body());
        var repeatedPayment = paidOwner.call("POST", paidPath + "/simulate-payment", null);
        assertThat(repeatedPayment.status()).isEqualTo(200);
        assertThat(repeatedPayment.body().path("appointment")).isEqualTo(cancelledPaid.body());
        assertThat(repeatedPayment.body().path("payment")).isEqualTo(paid.body().path("payment"));
        assertThat(paidOwner.call("GET", paidPath + "/billing", null).body()).isEqualTo(finances.body());
        assertThat(stranger.call("GET", paidPath + "/billing", null).status()).isEqualTo(404);
        var released = paidOwner.call("GET", "/api/v1/catalog/schedules?doctorId=" + paidSchedule.doctorId(), null);
        assertThat(released.body().get(0).path("availableSlots").asInt()).isEqualTo(1);

        var started = startedOwner.call("POST", "/api/v1/patient/appointments", command(startedSchedule, identifier()));
        assertThat(started.status()).isEqualTo(201);
        String startedPath = "/api/v1/patient/appointments/" + started.body().path("id").asText();
        assertThat(startedOwner.call("POST", startedPath + "/simulate-payment", null).status()).isEqualTo(200);
        clock.now = Instant.ofEpochMilli(startedSchedule.startsAt());
        var denied = startedOwner.call("POST", startedPath + "/cancel", null);
        assertThat(denied.status()).isEqualTo(409);
        assertThat(denied.body().path("error").path("code").asText()).isEqualTo("CANCEL_NOT_ALLOWED");
        assertThat(startedOwner.call("GET", startedPath, null).body().path("state").asText()).isEqualTo("CONFIRMED");
        assertThat(startedOwner.call("GET", startedPath + "/billing", null).body().path("refund").isNull()).isTrue();
    }
    @Test void lateSuccessfulPaymentRefundsExpiredOrderWithoutReleasingReassignedSlotOrNewPatientGuard() throws Exception {
        var originalSchedule = published("ONLINE", "AM", 1);
        var newSchedule = published("OFFLINE", "AM", 1);
        var thirdSchedule = published("ONLINE", "AM", 1);
        var patientA = patient(); var patientB = patient();
        var original = patientA.call("POST", "/api/v1/patient/appointments", command(originalSchedule, identifier()));
        assertThat(original.status()).isEqualTo(201);
        String originalPath = "/api/v1/patient/appointments/" + original.body().path("id").asText();
        clock.now = Instant.ofEpochMilli(original.body().path("holdExpiresAt").asLong());
        var reassigned = patientB.call("POST", "/api/v1/patient/appointments", command(originalSchedule, identifier()));
        assertThat(reassigned.status()).isEqualTo(201);
        assertThat(reassigned.body().path("slotId")).isEqualTo(original.body().path("slotId"));
        var newOrder = patientA.call("POST", "/api/v1/patient/appointments", command(newSchedule, identifier()));
        assertThat(newOrder.status()).isEqualTo(201);
        var late = patientA.call("POST", originalPath + "/simulate-payment", null);
        assertThat(late.status()).isEqualTo(200);
        assertThat(late.body().path("appointment").path("state").asText()).isEqualTo("EXPIRED");
        assertThat(late.body().path("appointment").path("credential").isNull()).isTrue();
        assertThat(late.body().path("payment").path("state").asText()).isEqualTo("SUCCEEDED");
        assertThat(late.body().path("payment").path("amountMinor").asLong()).isEqualTo(2000);
        var financial = patientA.call("GET", originalPath + "/billing", null);
        assertThat(financial.status()).isEqualTo(200);
        var refund = financial.body().path("refund");
        assertThat(refund.path("id").asText()).isNotBlank();
        assertThat(refund.path("paymentId")).isEqualTo(late.body().path("payment").path("id"));
        assertThat(refund.path("amountMinor").asLong()).isEqualTo(2000);
        assertThat(refund.path("state").asText()).isEqualTo("REFUND_PENDING");
        assertThat(refund.path("reason").asText()).isEqualTo("LATE_PAYMENT");
        assertThat(patientA.call("POST", originalPath + "/simulate-payment", null).body()).isEqualTo(late.body());
        assertThat(patientA.call("GET", originalPath + "/billing", null).body()).isEqualTo(financial.body());
        assertThat(patientB.call("GET", "/api/v1/patient/appointments/" + reassigned.body().path("id").asText(), null).body()).isEqualTo(reassigned.body());
        assertThat(patientA.call("GET", "/api/v1/patient/appointments/" + newOrder.body().path("id").asText(), null).body()).isEqualTo(newOrder.body());
        assertThat(patientA.call("GET", "/api/v1/catalog/schedules?doctorId=" + originalSchedule.doctorId(), null).body().size()).isZero();
        var conflict = patientA.call("POST", "/api/v1/patient/appointments", command(thirdSchedule, identifier()));
        assertThat(conflict.status()).isEqualTo(409);
        assertThat(conflict.body().path("error").path("code").asText()).isEqualTo("REGISTRATION_CONFLICT");
    }

    @Test void patientCreatesPendingPaymentAppointmentAndClaimsPublishedSlot() throws Exception {
        var admin = new Browser(port);
        assertThat(admin.login("admin-foundation-test", "fictional-test-administrator-password").status()).isEqualTo(200);
        var department = admin.call("POST", "/api/v1/admin/catalog/departments",
            Map.of("name", identifier(), "description", "虚构门诊", "active", true)).body().path("id").asText();
        var doctor = new Browser(port);
        var doctorId = doctor.register(identifier(), "DOCTOR").body().path("account").path("id").asText();
        assertThat(admin.call("PUT", "/api/v1/admin/catalog/doctors/" + doctorId,
            Map.of("displayName", "虚构李医生", "title", "主治医师", "biography", "演示", "departmentId", department, "credentialStatus", "CERTIFIED")).status()).isEqualTo(200);
        var date = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(2);
        var schedule = admin.call("POST", "/api/v1/admin/catalog/schedules", Map.of("doctorId", doctorId,
            "departmentId", department, "clinicDate", date.toString(), "session", "AM", "serviceMode", "ONLINE", "capacity", 2, "feeMinor", 2000));
        assertThat(schedule.status()).isEqualTo(201);
        var scheduleId = schedule.body().path("id").asText();
        var patient = new Browser(port); var name = identifier();
        patient.register(name, "PATIENT"); assertThat(patient.login(name, PASSWORD).status()).isEqualTo(200);
        var created = patient.call("POST", "/api/v1/patient/appointments",
            Map.of("scheduleId", scheduleId, "source", "ADVANCE", "idempotencyKey", identifier()));
        assertThat(created.status()).isEqualTo(201);
        var order = created.body();
        assertThat(order.path("id").asText()).isNotBlank();
        assertThat(order.path("state").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(order.path("scheduleId").asText()).isEqualTo(scheduleId);
        assertThat(order.path("serviceMode").asText()).isEqualTo("ONLINE");
        assertThat(order.path("clinicDate").asText()).isEqualTo(date.toString());
        assertThat(order.path("session").asText()).isEqualTo("AM");
        assertThat(order.path("feeMinor").asLong()).isEqualTo(2000);
        assertThat(order.path("holdExpiresAt").asLong()).isEqualTo(clock.millis() + 600_000);
        var queried = patient.call("GET", "/api/v1/patient/appointments/" + order.path("id").asText(), null);
        assertThat(queried.status()).isEqualTo(200);
        assertThat(queried.body()).isEqualTo(order);
        var remaining = patient.call("GET", "/api/v1/catalog/schedules?doctorId=" + doctorId, null);
        assertThat(remaining.body().get(0).path("availableSlots").asInt()).isEqualTo(1);
    }
}
