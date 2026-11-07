package cn.medical;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "medical.captcha-ttl=2m", "spring.data.redis.database=7", "medical.refund-scanner-enabled=false", "medical.reception-scan-delay=3600000"})
@Import({TestAnswers.class, BookingHttpTest.TimeControl.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkflowHttpTest extends HttpFixture {
    @LocalServerPort int port;
    @Autowired BookingHttpTest.AdjustableClock clock;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean cn.medical.clinical.ClinicalFileStore fileStore;
    Browser administrator;
    record Actor(Browser browser, String id, String identifier) {}
    record Scenario(Actor patient, Actor doctor, String departmentId, JsonNode schedule, String appointmentId) {}
    record FilePart(String name, String type, byte[] bytes) {}
    static final FilePart PDF = new FilePart("fictional.pdf", "application/pdf", "%PDF-1.7\nfictional report".getBytes(StandardCharsets.UTF_8));
    @BeforeEach @AfterEach void resetClock() { clock.now = Instant.now(); }
    Browser admin() throws Exception {
        if (administrator == null) {
            administrator = new Browser(port);
            assertThat(administrator.login("admin-foundation-test", "fictional-test-administrator-password").status()).isEqualTo(200);
        }
        return administrator;
    }
    Actor actor(String role) throws Exception {
        var browser = new Browser(port); String name = identifier();
        var registered = browser.register(name, role); assertThat(registered.status()).isEqualTo(201);
        assertThat(browser.login(name, PASSWORD).status()).isEqualTo(200);
        return new Actor(browser, registered.body().path("account").path("id").asText(), name);
    }
    Actor doctor(String department) throws Exception {
        var doctor = actor("DOCTOR");
        assertThat(admin().call("PUT", "/api/v1/admin/catalog/doctors/" + doctor.id(), Map.of(
            "displayName", "虚构工作流医生", "title", "主治医师", "biography", "虚构资料", "departmentId", department, "credentialStatus", "CERTIFIED")).status()).isEqualTo(200);
        return doctor;
    }
    Scenario scenario(String mode) throws Exception {
        String department = admin().call("POST", "/api/v1/admin/catalog/departments", Map.of("name", identifier(), "description", "虚构科室", "active", true)).body().path("id").asText();
        var doctor = doctor(department); var patient = actor("PATIENT");
        var published = admin().call("POST", "/api/v1/admin/catalog/schedules", Map.of("doctorId", doctor.id(), "departmentId", department,
            "clinicDate", LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(2).toString(), "session", "AM", "serviceMode", mode, "capacity", 3, "feeMinor", 1500));
        assertThat(published.status()).isEqualTo(201);
        var appointment = patient.browser().call("POST", "/api/v1/patient/appointments", Map.of("scheduleId", published.body().path("id").asText(), "source", "ADVANCE", "idempotencyKey", identifier()));
        assertThat(appointment.status()).isEqualTo(201); String id = appointment.body().path("id").asText();
        assertThat(patient.browser().call("POST", "/api/v1/patient/appointments/" + id + "/simulate-payment", null).status()).isEqualTo(200);
        return new Scenario(patient, doctor, department, published.body(), id);
    }
    void start(Scenario scenario) throws Exception {
        clock.now = Instant.ofEpochMilli(scenario.schedule().path("startsAt").asLong());
        assertThat(scenario.patient().browser().call("POST", patientPath(scenario) + "/check-in", null).status()).isEqualTo(200);
        assertThat(scenario.doctor().browser().call("POST", "/api/v1/doctor/reception/" + scenario.appointmentId() + "/call", null).status()).isEqualTo(200);
        assertThat(scenario.doctor().browser().call("POST", "/api/v1/doctor/reception/" + scenario.appointmentId() + "/start", null).status()).isEqualTo(200);
    }
    String patientPath(Scenario scenario) { return "/api/v1/patient/appointments/" + scenario.appointmentId(); }
    String doctorPath(Scenario scenario) { return "/api/v1/doctor/clinical/" + scenario.appointmentId(); }
    Reply materials(Browser browser, String appointment, boolean shared, String symptoms, FilePart... parts) throws Exception {
        String boundary = "Medical" + UUID.randomUUID(); var body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"symptoms\"\r\n\r\n" + symptoms + "\r\n" +
            "--" + boundary + "\r\nContent-Disposition: form-data; name=\"shared\"\r\n\r\n" + shared + "\r\n").getBytes(StandardCharsets.UTF_8));
        for (var part : parts) {
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"files\"; filename=\"" + part.name() + "\"\r\nContent-Type: " + part.type() + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(part.bytes()); body.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        String csrf = browser.call("GET", "/api/v1/auth/csrf", null).body().path("token").asText();
        var response = browser.http.send(HttpRequest.newBuilder(URI.create(browser.base + "/api/v1/patient/appointments/" + appointment + "/clinical/materials"))
            .header("Authorization", "Bearer " + browser.access).header("X-XSRF-TOKEN", csrf).header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
        return new Reply(response.statusCode(), JSON.readTree(response.body()), response.headers());
    }
    HttpResponse<byte[]> download(Browser browser, String path) throws Exception {
        return browser.http.send(HttpRequest.newBuilder(URI.create(browser.base + path)).header("Authorization", "Bearer " + browser.access).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    @Test void clinicalMessagesResultsAndCorrectionsRespectOwnershipAndReadOnlyBoundary() throws Exception {
        for (String mode : List.of("ONLINE", "OFFLINE")) {
            var scenario = scenario(mode); var patient = scenario.patient().browser(); var doctor = scenario.doctor().browser();
            var uploaded = materials(patient, scenario.appointmentId(), true, "虚构症状", PDF);
            assertThat(uploaded.status()).isEqualTo(200);
            assertThat(uploaded.body().toString()).doesNotContain("storageKey", "medical-attachments");
            String attachment = uploaded.body().path("materials").get(0).path("attachments").get(0).path("id").asText();
            assertThat(download(patient, "/api/v1/patient/clinical/attachments/" + attachment).body()).isEqualTo(PDF.bytes());
            assertThat(download(doctor, "/api/v1/doctor/clinical/attachments/" + attachment).statusCode()).isEqualTo(200);
            var foreignPatient = actor("PATIENT").browser(); var foreignDoctor = actor("DOCTOR").browser();
            assertThat(foreignPatient.call("GET", patientPath(scenario) + "/clinical", null).status()).isEqualTo(404);
            assertThat(foreignDoctor.call("GET", doctorPath(scenario), null).status()).isEqualTo(404);
            assertThat(download(foreignPatient, "/api/v1/patient/clinical/attachments/" + attachment).statusCode()).isEqualTo(404);
            assertThat(download(foreignDoctor, "/api/v1/doctor/clinical/attachments/" + attachment).statusCode()).isEqualTo(404);
            assertThat(patient.call("POST", patientPath(scenario) + "/clinical/messages", Map.of("body", "过早消息")).status()).isEqualTo(409);
            var consent = patient.call("POST", patientPath(scenario) + "/ai/consents", Map.of("accepted", true, "provider", "LOCAL_MOCK", "purpose", "PRECONSULTATION_SUMMARY", "scope", "SYMPTOM_TEXT")).body();
            start(scenario);
            assertThat(patient.call("POST", patientPath(scenario) + "/clinical/messages", Map.of("body", "患者图文消息")).status()).isEqualTo(200);
            assertThat(doctor.call("POST", doctorPath(scenario) + "/messages", Map.of("body", "医生回复")).status()).isEqualTo(200);
            assertThat(patient.call("GET", patientPath(scenario) + "/clinical", null).body().path("messages")).hasSize(2);
            assertThat(patient.call("POST", patientPath(scenario) + "/clinical/sharing/revoke", null).status()).isEqualTo(200);
            assertThat(download(doctor, "/api/v1/doctor/clinical/attachments/" + attachment).statusCode()).isEqualTo(403);
            assertThat(doctor.call("POST", doctorPath(scenario) + "/messages", Map.of("body", "撤回后回复")).body().path("materials")).isEmpty();
            assertThat(doctor.call("POST", "/api/v1/doctor/reception/" + scenario.appointmentId() + "/complete", null).status()).isEqualTo(400);
            String finishPath = mode.equals("ONLINE") ? "/api/v1/doctor/reception/" + scenario.appointmentId() + "/complete" : doctorPath(scenario) + "/result";
            var finished = doctor.call("POST", finishPath, Map.of("body", "虚构问诊结果"));
            assertThat(finished.status()).isEqualTo(200); assertThat(finished.body().path("materials")).isEmpty();
            var result = finished.body().path("result");
            assertThat(result.path("authorId").asText()).isEqualTo(scenario.doctor().id());
            assertThat(result.path("versionNo").asInt()).isEqualTo(1); assertThat(result.path("createdAt").asLong()).isEqualTo(clock.millis());
            assertThat(result.path("encounterType").asText()).isEqualTo(mode.equals("ONLINE") ? "ONLINE_CHAT" : "OFFLINE_RESULT");
            assertThat(doctor.call("POST", doctorPath(scenario) + "/result", Map.of("body", "覆盖旧结果")).status()).isEqualTo(409);
            assertThat(materials(patient, scenario.appointmentId(), true, "结束后材料", PDF).status()).isEqualTo(409);
            assertThat(patient.call("POST", patientPath(scenario) + "/ai/preconsultations", Map.of("consentId", consent.path("id").asText(), "symptomText", "结束后 AI 材料", "idempotencyKey", identifier())).status()).isEqualTo(409);
            assertThat(patient.call("POST", patientPath(scenario) + "/clinical/messages", Map.of("body", "结束后消息")).status()).isEqualTo(409);
            var corrected = doctor.call("POST", doctorPath(scenario) + "/corrections", Map.of("reason", "虚构更正原因", "body", "追加更正"));
            assertThat(corrected.status()).isEqualTo(200); assertThat(corrected.body().path("materials")).isEmpty();
            assertThat(corrected.body().path("result")).isEqualTo(result); assertThat(corrected.body().path("corrections")).hasSize(1);
            assertThat(patient.call("GET", patientPath(scenario) + "/clinical", null).body().path("result")).isEqualTo(result);
            clock.now = Instant.now();
        }
    }
    @Test void concurrentCompletionSerializesTheReadOnlyBoundary() throws Exception {
        var scenario = scenario("ONLINE"); start(scenario);
        var ready = new java.util.concurrent.CountDownLatch(2); var go = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> { ready.countDown(); go.await(); return scenario.doctor().browser().call("POST", doctorPath(scenario) + "/result", Map.of("body", "结果一")); });
            var second = executor.submit(() -> { ready.countDown(); go.await(); return scenario.doctor().browser().call("POST", doctorPath(scenario) + "/result", Map.of("body", "结果二")); });
            assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); go.countDown();
            var replies = List.of(first.get(20, java.util.concurrent.TimeUnit.SECONDS), second.get(20, java.util.concurrent.TimeUnit.SECONDS));
            assertThat(replies.stream().map(Reply::status)).containsExactlyInAnyOrder(200, 409);
            var winner = replies.stream().filter(reply -> reply.status() == 200).findFirst().orElseThrow().body().path("result");
            assertThat(scenario.patient().browser().call("GET", patientPath(scenario) + "/clinical", null).body().path("result")).isEqualTo(winner);
            assertThat(scenario.doctor().browser().call("POST", doctorPath(scenario) + "/messages", Map.of("body", "结束后回复")).status()).isEqualTo(409);
        }
    }

    @Test void partialUploadFailureRemovesPrivateFilesAndRollsBackMetadata() throws Exception {
        var scenario = scenario("ONLINE");
        var firstKey = new java.util.concurrent.atomic.AtomicReference<String>(); var calls = new java.util.concurrent.atomic.AtomicInteger();
        org.mockito.Mockito.doAnswer(invocation -> {
            if (calls.incrementAndGet() == 2) throw new java.io.IOException("simulated private storage failure");
            firstKey.set(invocation.getArgument(0)); return invocation.callRealMethod();
        }).when(fileStore).write(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(byte[].class));
        try {
            assertThat(materials(scenario.patient().browser(), scenario.appointmentId(), true, "虚构故障材料", PDF, PDF).status()).isEqualTo(503);
            assertThat(firstKey.get()).isNotNull();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> fileStore.read(firstKey.get())).isInstanceOf(java.nio.file.NoSuchFileException.class);
            assertThat(scenario.patient().browser().call("GET", patientPath(scenario) + "/clinical", null).body().path("materials")).isEmpty();
        } finally { org.mockito.Mockito.doCallRealMethod().when(fileStore).write(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(byte[].class)); }
    }

    @Test void materialUploadChecksAuthorizationContentCountAndTenMegabyteBoundary() throws Exception {
        var scenario = scenario("ONLINE"); var patient = scenario.patient().browser(); String id = scenario.appointmentId();
        assertThat(materials(patient, id, false, "未授权", PDF).status()).isEqualTo(400);
        assertThat(materials(patient, id, true, "格式不符", new FilePart("wrong.png", "image/png", PDF.bytes())).status()).isEqualTo(400);
        assertThat(materials(patient, id, true, "超出数量", PDF, PDF, PDF, PDF, PDF, PDF).status()).isEqualTo(400);
        if (System.getProperty("os.name").toLowerCase().contains("windows"))
            assertThat(materials(patient, id, true, "非法名称", new FilePart("report:bad.pdf", "application/pdf", PDF.bytes())).status()).isEqualTo(400);
        byte[] maximum = Arrays.copyOf(PDF.bytes(), 10 * 1024 * 1024);
        assertThat(materials(patient, id, true, "合法大小上限", new FilePart("maximum.pdf", "application/pdf", maximum)).status()).isEqualTo(200);
        var view = patient.call("GET", patientPath(scenario) + "/clinical", null).body();
        assertThat(view.path("materials")).hasSize(1);
        assertThat(view.path("materials").get(0).path("versionNo").asInt()).isEqualTo(1);
        assertThat(materials(patient, id, true, "超过大小上限", new FilePart("oversized.pdf", "application/pdf", Arrays.copyOf(maximum, maximum.length + 1))).status()).isEqualTo(413);
    }
}
