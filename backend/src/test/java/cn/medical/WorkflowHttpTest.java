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
@Import({TestAnswers.class, BookingHttpTest.TimeControl.class,RuntimeRecoveryControl.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkflowHttpTest extends HttpFixture {
    @LocalServerPort int port;
    @Autowired BookingHttpTest.AdjustableClock clock;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired org.springframework.core.env.Environment environment;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean cn.medical.clinical.ClinicalFileStore fileStore;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean cn.medical.ai.LocalAiTextProvider aiProvider;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean cn.medical.booking.BookingService bookingService;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean cn.medical.delivery.SseTransport sseTransport;
    @Autowired cn.medical.delivery.DeliveryService deliveryService;
    @Autowired cn.medical.delivery.RuntimeTaskMapper runtimeTasks;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired cn.medical.delivery.DeliveryMapper deliveryMapper;
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
            var consent = patient.call("POST", patientPath(scenario) + "/ai/consents", Map.of("accepted", true, "provider", "LOCAL_MOCK", "purpose", "PRECONSULTATION_SUMMARY", "scope", "SYMPTOM_TEXT", "symptomText", "结束后 AI 材料")).body();
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

    JsonNode consent(Scenario scenario, boolean accepted, String provider, String text) throws Exception {
        var reply = scenario.patient().browser().call("POST", patientPath(scenario) + "/ai/consents", Map.of(
            "accepted", accepted, "provider", provider, "purpose", "PRECONSULTATION_SUMMARY", "scope", "SYMPTOM_TEXT", "symptomText", text));
        assertThat(reply.status()).isEqualTo(200); return reply.body();
    }
    Reply analyze(Scenario scenario, String consent, String text, String key) throws Exception {
        return scenario.patient().browser().call("POST", patientPath(scenario) + "/ai/preconsultations", Map.of("consentId", consent, "symptomText", text, "idempotencyKey", key));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void aiIdempotencyKeyBindsExactTextAndConsent(boolean appointmentBound) throws Exception {
        var scenario = appointmentBound ? scenario("ONLINE") : null;
        var patient = appointmentBound ? scenario.patient().browser() : actor("PATIENT").browser();
        String root = "/api/v1/patient" + (appointmentBound ? "/appointments/" + scenario.appointmentId() : "") + "/ai";
        String text = "虚构咳嗽持续3天", key = identifier();
        var grant = patient.call("POST", root + "/consents", Map.of("accepted", true, "provider", "LOCAL_MOCK",
            "purpose", "PRECONSULTATION_SUMMARY", "scope", "SYMPTOM_TEXT", "symptomText", text));
        assertThat(grant.status()).isEqualTo(200);
        String consentId = grant.body().path("id").asText();
        var command = Map.of("consentId", consentId, "symptomText", text, "idempotencyKey", key);
        var first = patient.call("POST", root + "/preconsultations", command);
        assertThat(first.status()).isEqualTo(200);
        assertThat(first.body().path("state").asText()).isEqualTo("SUCCEEDED");
        var replay = patient.call("POST", root + "/preconsultations", command);
        assertThat(replay.status()).isEqualTo(200);
        assertThat(replay.body()).isEqualTo(first.body());
        var originalResults = patient.call("GET", root + "/results", null);
        assertThat(originalResults.status()).isEqualTo(200);
        JsonNode originalMaterials = appointmentBound
            ? patient.call("GET", patientPath(scenario) + "/clinical", null).body().path("materials") : null;
        var secondGrant = patient.call("POST", root + "/consents", Map.of("accepted", true, "provider", "LOCAL_MOCK",
            "purpose", "PRECONSULTATION_SUMMARY", "scope", "SYMPTOM_TEXT", "symptomText", text));
        assertThat(secondGrant.status()).isEqualTo(200);
        for (var changed : List.of(
            Map.of("consentId", consentId, "symptomText", text + "新增症状", "idempotencyKey", key),
            Map.of("consentId", consentId, "symptomText", text + " ", "idempotencyKey", key),
            Map.of("consentId", secondGrant.body().path("id").asText(), "symptomText", text, "idempotencyKey", key))) {
            var conflict = patient.call("POST", root + "/preconsultations", changed);
            assertThat(conflict.status()).isEqualTo(409);
            assertThat(conflict.body().path("error").path("code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
        }
        assertThat(patient.call("GET", root + "/results", null).body()).isEqualTo(originalResults.body());
        if (appointmentBound)
            assertThat(patient.call("GET", patientPath(scenario) + "/clinical", null).body().path("materials")).isEqualTo(originalMaterials);
        org.mockito.Mockito.verify(aiProvider, org.mockito.Mockito.times(1)).analyze(
            org.mockito.ArgumentMatchers.eq(text), org.mockito.ArgumentMatchers.anyList());
        assertThat(patient.call("POST", root + "/preconsultations", command).body()).isEqualTo(first.body());
    }
    @Test void aiAuthorizationBindsOneTextAndRefusalDoesNotBlockIndependentBooking() throws Exception {
        var s = scenario("ONLINE"); var patient = s.patient().browser(); String text = "虚构咳嗽持续3天";
        assertThat(analyze(s, UUID.randomUUID().toString(), text, identifier()).status()).isEqualTo(403);
        var denied = consent(s, false, "LOCAL_MOCK", "");
        assertThat(analyze(s, denied.path("id").asText(), text, identifier()).status()).isEqualTo(403);
        var allowed = consent(s, true, "LOCAL_MOCK", text); String consentId = allowed.path("id").asText();
        assertThat(analyze(s, consentId, text + "新增内容", identifier()).status()).isEqualTo(403);
        var stranger = actor("PATIENT").browser();
        assertThat(stranger.call("POST", patientPath(s) + "/ai/preconsultations", Map.of("consentId", consentId, "symptomText", text, "idempotencyKey", identifier())).status()).isEqualTo(404);
        assertThat(patient.call("POST", patientPath(s) + "/ai/consents", Map.of("accepted", true, "provider", "LOCAL_MOCK", "purpose", "DIAGNOSIS", "scope", "ALL_RECORDS", "symptomText", text)).status()).isEqualTo(400);
        assertThat(patient.call("POST", patientPath(s) + "/ai/consents/" + consentId + "/revoke", null).status()).isEqualTo(204);
        assertThat(analyze(s, consentId, text, identifier()).status()).isEqualTo(403);
        assertThat(patient.call("GET", patientPath(s) + "/clinical", null).body().path("materials")).isEmpty();
        assertThat(patient.call("GET", patientPath(s) + "/ai/results", null).body()).isEmpty();
        assertThat(patient.call("POST", patientPath(s) + "/cancel", null).status()).isEqualTo(200);
        var next = patient.call("POST", "/api/v1/patient/appointments", Map.of("scheduleId", s.schedule().path("id").asText(), "source", "ADVANCE", "idempotencyKey", identifier()));
        assertThat(next.status()).isEqualTo(201);
        assertThat(patient.call("POST", "/api/v1/patient/appointments/" + next.body().path("id").asText() + "/simulate-payment", null).status()).isEqualTo(200);
    }
    @Test void prebookingPreconsultationNeedsNoAppointmentAndSharingIsExplicit() throws Exception {
        var setup = scenario("ONLINE"); var patient = actor("PATIENT").browser(); String text = "虚构咳嗽持续3天";
        assertThat(patient.call("GET", "/api/v1/patient/appointments", null).body()).isEmpty();
        var refused = patient.call("POST", "/api/v1/patient/ai/consents", Map.of("accepted", false, "provider", "LOCAL_MOCK", "purpose", "PRECONSULTATION_SUMMARY", "scope", "SYMPTOM_TEXT"));
        assertThat(refused.status()).isEqualTo(200);
        assertThat(patient.call("POST", "/api/v1/patient/ai/preconsultations", Map.of("consentId", refused.body().path("id").asText(), "symptomText", text, "idempotencyKey", identifier())).status()).isEqualTo(403);
        var grant = patient.call("POST", "/api/v1/patient/ai/consents", Map.of("accepted", true, "provider", "LOCAL_MOCK", "purpose", "PRECONSULTATION_SUMMARY", "scope", "SYMPTOM_TEXT", "symptomText", text));
        assertThat(grant.status()).isEqualTo(200);
        var result = patient.call("POST", "/api/v1/patient/ai/preconsultations", Map.of("consentId", grant.body().path("id").asText(), "symptomText", text, "idempotencyKey", identifier()));
        assertThat(result.status()).isEqualTo(200); assertThat(result.body().path("state").asText()).isEqualTo("SUCCEEDED");
        assertThat(patient.call("GET", "/api/v1/patient/appointments", null).body()).isEmpty();
        assertThat(patient.call("GET", "/api/v1/patient/ai/results", null).body()).hasSize(1);
        assertThat(setup.patient().browser().call("GET", "/api/v1/patient/ai/results", null).body()).isEmpty();
        var registered = patient.call("POST", "/api/v1/patient/appointments", Map.of("scheduleId", setup.schedule().path("id").asText(), "source", "ADVANCE", "idempotencyKey", identifier()));
        assertThat(registered.status()).isEqualTo(201); String appointmentId = registered.body().path("id").asText();
        assertThat(patient.call("POST", "/api/v1/patient/appointments/" + appointmentId + "/simulate-payment", null).status()).isEqualTo(200);
        String sharePath = "/api/v1/patient/appointments/" + appointmentId + "/ai/drafts/" + result.body().path("taskId").asText() + "/share";
        assertThat(patient.call("POST", sharePath, Map.of("shared", false)).status()).isEqualTo(400);
        assertThat(setup.patient().browser().call("POST", sharePath, Map.of("shared", true)).status()).isEqualTo(404);
        assertThat(setup.doctor().browser().call("GET", "/api/v1/doctor/clinical/" + appointmentId + "/ai", null).body()).isEmpty();
        var shared = patient.call("POST", sharePath, Map.of("shared", true)); assertThat(shared.status()).isEqualTo(200);
        assertThat(patient.call("POST", sharePath, Map.of("shared", true)).body()).isEqualTo(shared.body());
        var doctorResult = setup.doctor().browser().call("GET", "/api/v1/doctor/clinical/" + appointmentId + "/ai", null).body();
        assertThat(doctorResult).hasSize(1); assertThat(doctorResult.get(0).path("analysis")).isEqualTo(result.body().path("analysis"));
        assertThat(patient.call("POST", "/api/v1/patient/appointments/" + appointmentId + "/clinical/sharing/revoke", null).status()).isEqualTo(200);
        assertThat(setup.doctor().browser().call("GET", "/api/v1/doctor/clinical/" + appointmentId + "/ai", null).body()).isEmpty();
    }
    @Test void populatedVersion15DatabaseUpgradesWithCorrectAiMaterialProvenance() throws Exception {
        var s = scenario("ONLINE");
        assertThat(materials(s.patient().browser(), s.appointmentId(), true, "第一版材料").status()).isEqualTo(200);
        String text = "第二版虚构咳嗽持续3天";
        var grant = consent(s, true, "LOCAL_MOCK", text);
        var result = analyze(s, grant.path("id").asText(), text, identifier());
        assertThat(result.status()).isEqualTo(200); assertThat(result.body().path("materialVersion").asInt()).isEqualTo(2);
        String database = "medical_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try (var source = dataSource.getConnection(); var statement = source.createStatement()) {
            String sourceDatabase = source.getCatalog();
            String url = environment.getRequiredProperty("spring.datasource.url").replace("/" + sourceDatabase, "/" + database);
            String user = environment.getRequiredProperty("spring.datasource.username");
            String password = environment.getProperty("spring.datasource.password", "");
            statement.execute("CREATE DATABASE `" + database + "`");
            try {
                org.flywaydb.core.Flyway.configure().dataSource(url, user, password).target("15").load().migrate();
                try (var old = java.sql.DriverManager.getConnection(url, user, password); var copy = old.createStatement()) {
                    var tables = new ArrayList<String>();
                    try (var names = old.getMetaData().getTables(database, null, "%", new String[]{"TABLE"})) {
                        while (names.next()) if (!names.getString("TABLE_NAME").equals("flyway_schema_history")) tables.add(names.getString("TABLE_NAME"));
                    }
                    copy.execute("SET FOREIGN_KEY_CHECKS=0");
                    for (String table : tables) {
                        var columns = new ArrayList<String>();
                        try (var fields = old.getMetaData().getColumns(database, null, table, "%")) {
                            while (fields.next()) columns.add("`" + fields.getString("COLUMN_NAME") + "`");
                        }
                        String names = String.join(",", columns);
                        // Version 15 could not store the longer suspension state; retain a valid historical fixture.
                        String selection = table.equals("schedule") ? names.replace("`state`", "CASE WHEN LENGTH(state)>16 THEN 'PUBLISHED' ELSE state END") : names;
                        String filter = Set.of("ai_consent", "ai_task", "ai_result").contains(table) ? " WHERE appointment_id IS NOT NULL"
                            : table.equals("ai_call_log") ? " WHERE task_id IN (SELECT id FROM `" + sourceDatabase + "`.ai_task WHERE appointment_id IS NOT NULL)" : "";
                        copy.executeUpdate("DELETE FROM `" + database + "`.`" + table + "`");
                        copy.executeUpdate("INSERT INTO `" + database + "`.`" + table + "` (" + names + ") SELECT " + selection + " FROM `" + sourceDatabase + "`.`" + table + "`" + filter);
                    }
                    copy.execute("SET FOREIGN_KEY_CHECKS=1");
                }
                org.flywaydb.core.Flyway.configure().dataSource(url, user, password).load().migrate();
                try (var upgraded = java.sql.DriverManager.getConnection(url, user, password);
                    var query = upgraded.prepareStatement("SELECT material_version,symptom_text FROM ai_task WHERE id=?")) {
                    query.setString(1, result.body().path("taskId").asText());
                    try (var row = query.executeQuery()) {
                        assertThat(row.next()).isTrue(); assertThat(row.getInt(1)).isEqualTo(2); assertThat(row.getString(2)).isEqualTo(text);
                    }
                }
            } finally { statement.execute("DROP DATABASE `" + database + "`"); }
        }
    }
    @Test void aiResultsUseRealRecommendationsKeepVersionsAndRespectRevokedSharing() throws Exception {
        var s = scenario("ONLINE"); String text = "虚构咳嗽持续3天\n胸痛"; String key = identifier();
        assertThat(admin().call("PUT", "/api/v1/admin/catalog/departments/" + s.departmentId(), Map.of("name", "AI内科-" + identifier(), "description", "虚构内科", "active", true)).status()).isEqualTo(200);
        var consent = consent(s, true, "LOCAL_MOCK", text); String consentId = consent.path("id").asText();
        var first = analyze(s, consentId, text, key); assertThat(first.status()).isEqualTo(200);
        assertThat(first.body().path("state").asText()).isEqualTo("SUCCEEDED");
        assertThat(first.body().path("analysis").path("duration").asText()).isEqualTo("3天");
        assertThat(first.body().path("analysis").path("riskAlert").asText()).contains("急救");
        assertThat(first.body().path("analysis").path("safetyNotice").asText()).contains("不是诊断、处方");
        assertThat(first.body().path("modelVersion").asText()).isEqualTo("local-rules-1");
        assertThat(first.body().path("promptVersion").asText()).isEqualTo("text-summary-1");
        assertThat(first.body().path("materialId").asText()).isNotBlank();
        assertThat(first.body().path("consentId").asText()).isEqualTo(consentId);
        assertThat(analyze(s, consentId, text, key).body()).isEqualTo(first.body());
        assertThat(analyze(s, consentId, text, identifier()).status()).isEqualTo(409);
        var recommendations = first.body().path("recommendations");
        assertThat(recommendations).isNotEmpty();
        for (var recommendation : recommendations) {
            var schedules = s.patient().browser().call("GET", "/api/v1/catalog/schedules?doctorId=" + recommendation.path("doctorId").asText(), null).body();
            assertThat(java.util.stream.StreamSupport.stream(schedules.spliterator(), false).anyMatch(schedule -> schedule.path("id").equals(recommendation.path("scheduleId")) && schedule.path("advanceAvailable").asBoolean())).isTrue();
        }
        assertThat(s.doctor().browser().call("GET", doctorPath(s) + "/ai", null).body()).hasSize(1);
        assertThat(materials(s.patient().browser(), s.appointmentId(), true, "症状变更", new FilePart[0]).status()).isEqualTo(200);
        assertThat(s.doctor().browser().call("GET", doctorPath(s) + "/ai", null).body().get(0).path("stale").asBoolean()).isTrue();
        var secondConsent = consent(s, true, "LOCAL_MOCK", "症状变更");
        var second = analyze(s, secondConsent.path("id").asText(), "症状变更", identifier());
        assertThat(second.status()).isEqualTo(200); assertThat(second.body().path("versionNo").asInt()).isEqualTo(2);
        var history = s.patient().browser().call("GET", patientPath(s) + "/ai/results", null).body();
        assertThat(history).hasSize(2);
        assertThat(java.util.stream.StreamSupport.stream(history.spliterator(), false).filter(item -> item.path("taskId").equals(first.body().path("taskId"))).findFirst().orElseThrow().path("analysis")).isEqualTo(first.body().path("analysis"));
        assertThat(s.patient().browser().call("POST", patientPath(s) + "/clinical/sharing/revoke", null).status()).isEqualTo(200);
        assertThat(s.doctor().browser().call("GET", doctorPath(s) + "/ai", null).body()).isEmpty();
        assertThat(s.patient().browser().call("GET", patientPath(s) + "/ai/results", null).body()).hasSize(2);
    }
    @Test void aiFailureRemainsVisibleWithBoundedMetadataAndBusinessSymptomsStayLocal() throws Exception {
        var s = scenario("ONLINE"); String text = "虚构隐私文本";
        var consent = consent(s, true, "EXTERNAL_TEXT", text);
        var failed = analyze(s, consent.path("id").asText(), text, identifier());
        assertThat(failed.status()).isEqualTo(200);
        assertThat(failed.body().path("state").asText()).isEqualTo("FAILED");
        assertThat(failed.body().path("attempts").asInt()).isEqualTo(3);
        assertThat(failed.body().path("provider").asText()).isEqualTo("EXTERNAL_TEXT");
        assertThat(failed.body().path("analysis").isNull()).isTrue();
        org.mockito.Mockito.verify(aiProvider, org.mockito.Mockito.never()).analyze(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList());
        assertThat(s.patient().browser().call("GET", patientPath(s) + "/ai/results", null).body().get(0)).isEqualTo(failed.body());
        var metadata = admin().call("GET", "/api/v1/admin/ai/calls", null);
        assertThat(metadata.status()).isEqualTo(200); assertThat(metadata.body().toString()).doesNotContain(text, "symptomText", "body");
        assertThat(java.util.stream.StreamSupport.stream(metadata.body().spliterator(), false).filter(item -> item.path("taskId").equals(failed.body().path("taskId"))).count()).isEqualTo(3);
        assertThat(s.patient().browser().call("GET", "/api/v1/admin/ai/calls", null).status()).isEqualTo(403);
        assertThat(s.doctor().browser().call("GET", "/api/v1/admin/ai/calls", null).status()).isEqualTo(403);
        var hospital = admin().call("GET", "/api/v1/catalog/hospital", null).body();
        var address = admin().call("GET", "/api/v1/assistant/business?question=" + java.net.URLEncoder.encode("医院地址在哪里", StandardCharsets.UTF_8), null).body();
        assertThat(address.path("answer").asText()).contains(hospital.path("address").asText());
        var symptom = admin().call("GET", "/api/v1/assistant/business?question=" + java.net.URLEncoder.encode("我胸痛，医院地址在哪", StandardCharsets.UTF_8), null).body();
        assertThat(symptom.path("mode").asText()).isEqualTo("PRECONSULTATION_REQUIRED"); assertThat(symptom.path("answer").asText()).contains("急救");
    }
    String submitSuspension(Scenario s) throws Exception {
        var reply = s.doctor().browser().call("POST", "/api/v1/doctor/catalog/suspensions", Map.of("scheduleId", s.schedule().path("id").asText(), "reason", "虚构停诊理由"));
        assertThat(reply.status()).isEqualTo(201); return reply.body().path("id").asText();
    }
    void assignHead(Scenario s, Actor head) throws Exception {
        assertThat(admin().call("PUT", "/api/v1/admin/catalog/departments/" + s.departmentId() + "/head/" + head.id(), null).status()).isEqualTo(204);
    }
    String approveSubstitution(Scenario s, Actor head, Actor substitute) throws Exception {
        assignHead(s, head); String id = submitSuspension(s);
        assertThat(head.browser().call("POST", "/api/v1/doctor/catalog/head/suspensions/" + id + "/review", Map.of("plan", "SUBSTITUTE", "substituteDoctorId", substitute.id(), "reason", "同科替诊")).status()).isEqualTo(200);
        assertThat(substitute.browser().call("POST", "/api/v1/doctor/catalog/substitutions/" + id + "/respond", Map.of("accepted", true, "reason", "同意替诊")).status()).isEqualTo(200);
        var review = admin().call("POST", "/api/v1/admin/catalog/suspensions/" + id + "/review", Map.of("approve", true, "reason", "复核资质科室和时间"));
        assertThat(review.status()).isEqualTo(200); return id;
    }
    @Test void suspensionPausesUnpaidHoldsAndStopCancelsPaidOrdersWithAudit() throws Exception {
        var s = scenario("ONLINE"); var head = doctor(s.departmentId()); assignHead(s, head);
        var unpaid = actor("PATIENT").browser(); String scheduleId = s.schedule().path("id").asText();
        var hold = unpaid.call("POST", "/api/v1/patient/appointments", Map.of("scheduleId", scheduleId, "source", "ADVANCE", "idempotencyKey", identifier()));
        assertThat(hold.status()).isEqualTo(201); String holdId = hold.body().path("id").asText();
        String id = submitSuspension(s);
        assertThat(unpaid.call("GET", "/api/v1/patient/appointments/" + holdId, null).body().path("state").asText()).isEqualTo("CANCELLED");
        assertThat(s.patient().browser().call("GET", patientPath(s), null).body().path("state").asText()).isEqualTo("CONFIRMED");
        var extra = actor("PATIENT").browser();
        assertThat(extra.call("POST", "/api/v1/patient/appointments", Map.of("scheduleId", scheduleId, "source", "ADVANCE", "idempotencyKey", identifier())).status()).isEqualTo(409);
        var late = unpaid.call("POST", "/api/v1/patient/appointments/" + holdId + "/simulate-payment", null);
        assertThat(late.status()).isEqualTo(200); assertThat(late.body().path("appointment").path("state").asText()).isEqualTo("CANCELLED");
        assertThat(head.browser().call("POST", "/api/v1/doctor/catalog/head/suspensions/" + id + "/review", Map.of("plan", "STOP", "reason", "同意停诊")).status()).isEqualTo(200);
        assertThat(admin().call("POST", "/api/v1/admin/catalog/suspensions/" + id + "/review", Map.of("approve", true, "reason", "复核停诊")).status()).isEqualTo(200);
        assertThat(s.patient().browser().call("GET", patientPath(s), null).body().path("state").asText()).isEqualTo("CANCELLED");
        var financial = s.patient().browser().call("GET", patientPath(s) + "/billing", null);
        assertThat(financial.status()).isEqualTo(200); assertThat(financial.body().path("refund").path("reason").asText()).isEqualTo("CLINIC_SUSPENSION");
        assertThat(admin().call("POST", "/api/v1/admin/catalog/suspensions/" + id + "/review", Map.of("approve", true, "reason", "重复")).status()).isEqualTo(409);
        assertThat(admin().call("GET", "/api/v1/admin/medical/audits/" + scheduleId, null).body()).isNotEmpty();
        var stopped=deliveryMapper.byDedupe("clinic-suspended:"+id+":"+s.appointmentId());assertThat(stopped).isNotNull();assertThat(stopped.eventType()).isEqualTo("CLINIC_SUSPENDED");
        assertThat(deliveryMapper.byDedupe("appointment-cancelled:"+s.appointmentId()+":CLINIC_SUSPENSION")).isNotNull();
    }
    @Test void substitutionRequiresPatientChoiceRetainsSlotAndRevokesOriginalDoctor() throws Exception {
        var s = scenario("ONLINE"); var head = doctor(s.departmentId()); var substitute = doctor(s.departmentId());
        assertThat(materials(s.patient().browser(), s.appointmentId(), true, "替诊共享材料").status()).isEqualTo(200);
        approveSubstitution(s, head, substitute);
        assertThat(s.patient().browser().call("GET","/api/v1/notifications/unread",null).body().asInt()).isGreaterThanOrEqualTo(2);
        deliveryService.retry();assertThat(s.patient().browser().call("GET","/api/v1/notifications",null).body().toString()).contains("SUBSTITUTION_PENDING");
        assertThat(substitute.browser().call("GET","/api/v1/notifications",null).body().toString()).contains("SUBSTITUTE_INVITED");
        assertThat(substitute.browser().call("POST", "/api/v1/doctor/catalog/suspensions", Map.of("scheduleId", s.schedule().path("id").asText(), "reason", "禁止叠加变更")).status()).isEqualTo(409);
        var ordersBefore = s.patient().browser().call("GET", "/api/v1/patient/appointments", null).body().get(0);
        assertThat(ordersBefore.path("substitutionPending").asBoolean()).isTrue();
        assertThat(ordersBefore.path("doctorName").asText()).isEqualTo("虚构工作流医生");
        var choices = s.patient().browser().call("GET", "/api/v1/patient/substitution-choices", null).body(); assertThat(choices).hasSize(1);
        var choice = choices.get(0); assertThat(choice.path("deadlineAt").asLong()).isEqualTo(clock.millis() + Duration.ofHours(2).toMillis());
        assertThat(s.patient().browser().call("GET", patientPath(s), null).body().path("state").asText()).isEqualTo("CONFIRMED");
        assertThat(s.doctor().browser().call("GET", doctorPath(s), null).status()).isEqualTo(403);
        assertThat(substitute.browser().call("GET", doctorPath(s), null).status()).isEqualTo(404);
        String path = "/api/v1/patient/substitution-choices/" + choice.path("id").asText();
        assertThat(actor("PATIENT").browser().call("POST", path, Map.of("accepted", true, "reason", "越权")).status()).isEqualTo(404);
        var accepted = s.patient().browser().call("POST", path, Map.of("accepted", true, "reason", "同意新医生")); assertThat(accepted.status()).isEqualTo(200);
        assertThat(s.patient().browser().call("POST", path, Map.of("accepted", true, "reason", "重复同意")).body()).isEqualTo(accepted.body());
        assertThat(s.doctor().browser().call("GET", doctorPath(s), null).status()).isEqualTo(404);
        assertThat(substitute.browser().call("GET", doctorPath(s), null).body().path("materials")).hasSize(1);
        var updated = admin().call("GET", "/api/v1/admin/catalog/schedules", null).body();
        assertThat(java.util.stream.StreamSupport.stream(updated.spliterator(), false).filter(item -> item.path("id").equals(s.schedule().path("id"))).findFirst().orElseThrow().path("doctorId").asText()).isEqualTo(substitute.id());
        clock.now = Instant.ofEpochMilli(s.schedule().path("startsAt").asLong());
        assertThat(s.patient().browser().call("POST", patientPath(s) + "/check-in", null).status()).isEqualTo(200);
        assertThat(substitute.browser().call("POST", "/api/v1/doctor/reception/" + s.appointmentId() + "/call", null).status()).isEqualTo(200);
    }
    @Test void substitutionRefusalRecusalFinalConflictAndDeadlinesAreEnforced() throws Exception {
        var s = scenario("ONLINE"); var head = doctor(s.departmentId()); var substitute = doctor(s.departmentId()); assignHead(s, head);
        String id = submitSuspension(s);
        String reviewPath = "/api/v1/doctor/catalog/head/suspensions/" + id + "/review";
        assertThat(head.browser().call("POST", reviewPath, Map.of("plan", "SUBSTITUTE", "substituteDoctorId", s.doctor().id(), "reason", "不能自替")).status()).isEqualTo(409);
        assertThat(head.browser().call("POST", reviewPath, Map.of("plan", "SUBSTITUTE", "substituteDoctorId", substitute.id(), "reason", "邀请替诊")).status()).isEqualTo(200);
        deliveryService.retry();
        assertThat(substitute.browser().call("GET", "/api/v1/notifications", null).body().toString()).contains("SUBSTITUTE_INVITED");
        assertThat(substitute.browser().call("POST", "/api/v1/doctor/catalog/substitutions/" + id + "/respond", Map.of("accepted", false, "reason", "无法出诊")).body().path("state").asText()).isEqualTo("HEAD_REVIEW");
        assertThat(head.browser().call("POST", reviewPath, Map.of("plan", "SUBSTITUTE", "substituteDoctorId", substitute.id(), "reason", "重新邀请")).status()).isEqualTo(200);
        assertThat(substitute.browser().call("POST", "/api/v1/doctor/catalog/substitutions/" + id + "/respond", Map.of("accepted", true, "reason", "可以出诊")).status()).isEqualTo(200);
        var conflict = admin().call("POST", "/api/v1/admin/catalog/schedules", Map.of("doctorId", substitute.id(), "departmentId", s.departmentId(), "clinicDate", s.schedule().path("clinicDate").asText(), "session", "AM", "serviceMode", "OFFLINE", "capacity", 1, "feeMinor", 1500));
        assertThat(conflict.status()).isEqualTo(201);
        assertThat(admin().call("POST", "/api/v1/admin/catalog/suspensions/" + id + "/review", Map.of("approve", true, "reason", "重新检查冲突")).status()).isEqualTo(409);
        var other = scenario("OFFLINE"); assignHead(other, other.doctor());
        String own = submitSuspension(other);
        assertThat(other.doctor().browser().call("POST", "/api/v1/doctor/catalog/head/suspensions/" + own + "/review", Map.of("plan", "STOP", "reason", "自审禁止")).status()).isEqualTo(403);
        assertThat(admin().call("POST", "/api/v1/admin/catalog/suspensions/" + own + "/review", Map.of("approve", true, "reason", "管理员回避审核")).status()).isEqualTo(200);
        var expiry = scenario("ONLINE"); var head2 = doctor(expiry.departmentId()); var substitute2 = doctor(expiry.departmentId()); approveSubstitution(expiry, head2, substitute2);
        var choice = expiry.patient().browser().call("GET", "/api/v1/patient/substitution-choices", null).body().get(0);
        clock.now = Instant.ofEpochMilli(choice.path("deadlineAt").asLong());
        var expired = expiry.patient().browser().call("POST", "/api/v1/patient/substitution-choices/" + choice.path("id").asText(), Map.of("accepted", true, "reason", "超时同意"));
        assertThat(expired.body().path("state").asText()).isEqualTo("EXPIRED");
        assertThat(expiry.patient().browser().call("GET", patientPath(expiry), null).body().path("state").asText()).isEqualTo("CANCELLED");
    }
    @Test void emergencyStopCanBeAppliedWithoutAnOrdinaryRequestAndDoesNotRepeat() throws Exception {
        var s = scenario("ONLINE"); clock.now = Instant.ofEpochMilli(s.schedule().path("startsAt").asLong()).minus(Duration.ofHours(23));
        assertThat(s.doctor().browser().call("POST", "/api/v1/doctor/catalog/suspensions", Map.of("scheduleId", s.schedule().path("id").asText(), "reason", "不足24小时")).status()).isEqualTo(409);
        var stopped = admin().call("POST", "/api/v1/admin/catalog/schedules/" + s.schedule().path("id").asText() + "/emergency-stop", Map.of("reason", "医院紧急处置"));
        assertThat(stopped.status()).isEqualTo(200); assertThat(stopped.body().path("state").asText()).isEqualTo("EMERGENCY_STOP");
        assertThat(admin().call("POST", "/api/v1/admin/catalog/suspensions/" + stopped.body().path("id").asText() + "/emergency-stop", Map.of("reason", "重复")).status()).isEqualTo(409);
        assertThat(s.patient().browser().call("GET", patientPath(s), null).body().path("state").asText()).isEqualTo("CANCELLED");
    }
    @Test void lateSubstitutionRequiresExplicitRecordedPatientChoicesAndTwentyFourHourBoundaryIsInclusive() throws Exception {
        var s = scenario("ONLINE"); var head = doctor(s.departmentId()); var substitute = doctor(s.departmentId()); assignHead(s, head);
        clock.now = Instant.ofEpochMilli(s.schedule().path("startsAt").asLong()).minus(Duration.ofHours(24));
        String id = submitSuspension(s);
        assertThat(head.browser().call("POST", "/api/v1/doctor/catalog/head/suspensions/" + id + "/review", Map.of("plan", "SUBSTITUTE", "substituteDoctorId", substitute.id(), "reason", "替诊")).status()).isEqualTo(200);
        assertThat(substitute.browser().call("POST", "/api/v1/doctor/catalog/substitutions/" + id + "/respond", Map.of("accepted", true, "reason", "同意")).status()).isEqualTo(200);
        clock.now = Instant.ofEpochMilli(s.schedule().path("startsAt").asLong()).minus(Duration.ofMinutes(90));
        assertThat(admin().call("POST", "/api/v1/admin/catalog/suspensions/" + id + "/review", Map.of("approve", true, "reason", "临近开诊")).status()).isEqualTo(409);
        String path = "/api/v1/admin/catalog/suspensions/" + id + "/emergency-substitution";
        assertThat(admin().call("POST", path, Map.of("patientChoices", Map.of(), "reason", "尚未联系患者")).status()).isEqualTo(400);
        assertThat(s.patient().browser().call("GET", patientPath(s), null).body().path("state").asText()).isEqualTo("CONFIRMED");
        var applied = admin().call("POST", path, Map.of("patientChoices", Map.of(s.appointmentId(), true), "reason", "已电话取得虚构患者明确同意"));
        assertThat(applied.status()).isEqualTo(200); assertThat(applied.body().path("state").asText()).isEqualTo("APPROVED_SUBSTITUTE");
        assertThat(substitute.browser().call("GET", doctorPath(s), null).status()).isEqualTo(200);
        assertThat(s.doctor().browser().call("GET", doctorPath(s), null).status()).isEqualTo(404);
    }
    @Test void emergencySubstitutionDoesNotTransferAnAlreadyCheckedInPatient() throws Exception {
        var s = scenario("ONLINE"); var head = doctor(s.departmentId()); var substitute = doctor(s.departmentId()); assignHead(s, head);
        String id = submitSuspension(s);
        assertThat(head.browser().call("POST", "/api/v1/doctor/catalog/head/suspensions/" + id + "/review", Map.of("plan", "SUBSTITUTE", "substituteDoctorId", substitute.id(), "reason", "替诊")).status()).isEqualTo(200);
        assertThat(substitute.browser().call("POST", "/api/v1/doctor/catalog/substitutions/" + id + "/respond", Map.of("accepted", true, "reason", "同意")).status()).isEqualTo(200);
        clock.now = Instant.ofEpochMilli(s.schedule().path("startsAt").asLong()).minus(Duration.ofMinutes(20));
        assertThat(s.patient().browser().call("POST", patientPath(s) + "/check-in", null).status()).isEqualTo(200);
        var response = admin().call("POST", "/api/v1/admin/catalog/suspensions/" + id + "/emergency-substitution", Map.of("patientChoices", Map.of(s.appointmentId(), true), "reason", "患者已进入候诊"));
        assertThat(response.status()).isEqualTo(409); assertThat(response.body().path("error").path("code").asText()).isEqualTo("ENCOUNTER_STARTED");
        assertThat(s.doctor().browser().call("GET", doctorPath(s), null).status()).isEqualTo(200);
        assertThat(substitute.browser().call("GET", doctorPath(s), null).status()).isEqualTo(404);
    }
    Actor reviewer() throws Exception {
        String name = identifier();
        var created = admin().call("POST","/api/v1/admin/accounts",Map.of("identifier",name,"password",PASSWORD,"role","ADMIN"));
        assertThat(created.status()).isEqualTo(201);
        var browser = new Browser(port); assertThat(browser.login(name,PASSWORD).status()).isEqualTo(200);
        assertThat(admin().call("POST","/api/v1/admin/medical/permissions",Map.of("identifier",name,"enabled",true,"reason","独立指派虚构审核人员")).status()).isEqualTo(204);
        return new Actor(browser,created.body().path("account").path("id").asText(),name);
    }
    String review(Actor actor, Scenario s, String scope) throws Exception {
        var grant = actor.browser().call("POST","/api/v1/admin/medical/reviews",Map.of("appointmentId",s.appointmentId(),"scope",scope,"reason","处理虚构争议内容"));
        assertThat(grant.status()).isEqualTo(200); return grant.body().path("id").asText();
    }
    Reply flag(Actor actor, String reviewId, Scenario s, String type, String target, String state) throws Exception {
        return actor.browser().call("POST","/api/v1/admin/medical/flags",Map.of("reviewId",reviewId,"appointmentId",s.appointmentId(),"targetType",type,"targetId",target,"state",state,"reason","虚构内容复核"));
    }
    @Test void medicalReviewRequiresIndependentPermissionAndSeparatesRecordAndAttachmentScopes() throws Exception {
        var s = scenario("ONLINE"); var uploaded = materials(s.patient().browser(),s.appointmentId(),true,"保留原始医疗正文",PDF).body();
        String attachment = uploaded.path("materials").get(0).path("attachments").get(0).path("id").asText();
        var request = Map.of("appointmentId",s.appointmentId(),"reason","虚构审核理由","scope","ALL");
        assertThat(admin().call("POST","/api/v1/admin/medical/reviews",request).status()).isEqualTo(403);
        assertThat(admin().call("POST","/api/v1/admin/medical/permissions",Map.of("identifier","admin-foundation-test","enabled",true,"reason","禁止自授")).status()).isEqualTo(403);
        assertThat(s.patient().browser().call("GET","/api/v1/admin/medical/appointments",null).status()).isEqualTo(403);
        assertThat(s.doctor().browser().call("POST","/api/v1/admin/medical/reviews",request).status()).isEqualTo(403);
        var reviewer = reviewer(); String record = review(reviewer,s,"RECORD"); String attachmentOnly = review(reviewer,s,"ATTACHMENTS");
        String base = "/api/v1/admin/medical/reviews/";
        var recordView = reviewer.browser().call("GET",base+record+"/content",null);
        assertThat(recordView.status()).isEqualTo(200); assertThat(recordView.body().toString()).contains("保留原始医疗正文").doesNotContain("fictional.pdf","storageKey");
        assertThat(download(reviewer.browser(),base+record+"/attachments/"+attachment).statusCode()).isEqualTo(403);
        var attachmentView = reviewer.browser().call("GET",base+attachmentOnly+"/content",null);
        assertThat(attachmentView.body().toString()).contains("fictional.pdf").doesNotContain("保留原始医疗正文","storageKey");
        assertThat(download(reviewer.browser(),base+attachmentOnly+"/attachments/"+attachment).body()).isEqualTo(PDF.bytes());
        assertThat(flag(reviewer,attachmentOnly,s,"MATERIAL",uploaded.path("materials").get(0).path("id").asText(),"LIMITED").status()).isEqualTo(403);
        var other = reviewer(); assertThat(other.browser().call("GET",base+record+"/content",null).status()).isEqualTo(404);
        var foreign = scenario("ONLINE"); var otherUpload = materials(foreign.patient().browser(),foreign.appointmentId(),true,"外人材料",PDF).body();
        assertThat(flag(reviewer,record,s,"MATERIAL",otherUpload.path("materials").get(0).path("id").asText(),"LIMITED").status()).isEqualTo(404);
        assertThat(download(reviewer.browser(),base+attachmentOnly+"/attachments/"+otherUpload.path("materials").get(0).path("attachments").get(0).path("id").asText()).statusCode()).isEqualTo(404);
        assertThat(reviewer.browser().call("POST",base+record+"/revoke",Map.of("reason","审核完成")).status()).isEqualTo(204);
        assertThat(reviewer.browser().call("GET",base+record+"/content",null).status()).isEqualTo(403);
        assertThat(admin().call("POST","/api/v1/admin/medical/permissions",Map.of("identifier",reviewer.identifier(),"enabled",false,"reason","撤回独立权限")).status()).isEqualTo(204);
        assertThat(download(reviewer.browser(),base+attachmentOnly+"/attachments/"+attachment).statusCode()).isEqualTo(403);
        assertThat(reviewer.browser().call("POST","/api/v1/admin/medical/reviews",request).status()).isEqualTo(403);
    }
    @Test void medicalRestrictionsApplyToAllClinicalReadersAndAiDerivativesAndCanBeCleared() throws Exception {
        var s = scenario("ONLINE"); String text = "受限材料派生的虚构咳嗽3天";
        var generated = analyze(s,consent(s,true,"LOCAL_MOCK",text).path("id").asText(),text,identifier()).body();
        var uploaded = materials(s.patient().browser(),s.appointmentId(),true,"另一份受限材料",PDF).body();
        String material = uploaded.path("materials").get(0).path("id").asText(), attachment = uploaded.path("materials").get(0).path("attachments").get(0).path("id").asText();
        start(s);
        assertThat(s.patient().browser().call("POST",patientPath(s)+"/clinical/messages",Map.of("body","受限消息原文")).status()).isEqualTo(200);
        var completed = s.doctor().browser().call("POST",doctorPath(s)+"/result",Map.of("body","受限结果原文")).body();
        var corrected = s.doctor().browser().call("POST",doctorPath(s)+"/corrections",Map.of("reason","受限更正理由","body","受限更正原文")).body();
        var reviewer = reviewer(); String review = review(reviewer,s,"ALL");
        assertThat(flag(reviewer,review,s,"MATERIAL",generated.path("materialId").asText(),"LIMITED").status()).isEqualTo(200);
        assertThat(s.doctor().browser().call("GET",doctorPath(s)+"/ai",null).body().toString()).doesNotContain(text).contains("内容已限制展示");
        var aiResults = s.patient().browser().call("GET",patientPath(s)+"/ai/results",null).body().toString();
        assertThat(aiResults).doesNotContain(text).contains("内容已限制展示");
        assertThat(flag(reviewer,review,s,"ATTACHMENT",attachment,"LIMITED").status()).isEqualTo(200);
        assertThat(download(s.patient().browser(),"/api/v1/patient/clinical/attachments/"+attachment).statusCode()).isEqualTo(403);
        assertThat(download(s.doctor().browser(),"/api/v1/doctor/clinical/attachments/"+attachment).statusCode()).isEqualTo(403);
        for (var target : List.of(new String[]{"MATERIAL",material},new String[]{"MESSAGE",completed.path("messages").get(0).path("id").asText()},new String[]{"RESULT",completed.path("result").path("id").asText()},new String[]{"CORRECTION",corrected.path("corrections").get(0).path("id").asText()}))
            assertThat(flag(reviewer,review,s,target[0],target[1],"LIMITED").status()).isEqualTo(200);
        for (Browser reader : List.of(s.patient().browser(),s.doctor().browser())) {
            String path = reader == s.patient().browser() ? patientPath(s)+"/clinical" : doctorPath(s);
            assertThat(reader.call("GET",path,null).body().toString()).doesNotContain("另一份受限材料","受限消息原文","受限结果原文","受限更正原文","受限更正理由");
        }
        var originals = reviewer.browser().call("GET","/api/v1/admin/medical/reviews/"+review+"/content",null).body().toString();
        assertThat(originals).contains("另一份受限材料","受限消息原文","受限结果原文","受限更正原文");
        assertThat(flag(reviewer,review,s,"MATERIAL",material,"CLEARED").status()).isEqualTo(200);
        assertThat(flag(reviewer,review,s,"ATTACHMENT",attachment,"CLEARED").status()).isEqualTo(200);
        assertThat(s.patient().browser().call("GET",patientPath(s)+"/clinical",null).body().toString()).contains("另一份受限材料");
        assertThat(download(s.patient().browser(),"/api/v1/patient/clinical/attachments/"+attachment).statusCode()).isEqualTo(200);
        assertThat(admin().call("GET","/api/v1/admin/medical/audits/"+completed.path("result").path("id").asText(),null).body().toString()).doesNotContain("受限更正理由","受限更正原文");
        var head = doctor(s.departmentId()); assignHead(s,head);
        assertThat(head.browser().call("GET",doctorPath(s),null).status()).isEqualTo(404);
        assertThat(s.patient().browser().call("POST",patientPath(s)+"/clinical/sharing/revoke",null).status()).isEqualTo(200);
        assertThat(download(s.doctor().browser(),"/api/v1/doctor/clinical/attachments/"+attachment).statusCode()).isEqualTo(403);
    }
    @Test void medicalReviewExpiresAtThirtyMinutesAndAuditDetectsChangedContentAndMissingTail() throws Exception {
        var s = scenario("ONLINE"); var reviewer = reviewer(); String id = review(reviewer,s,"ALL");
        clock.now = clock.instant().plus(Duration.ofMinutes(30));
        assertThat(reviewer.browser().call("GET","/api/v1/admin/medical/reviews/"+id+"/content",null).status()).isEqualTo(403);
        assertThat(admin().call("GET","/api/v1/admin/medical/audit-integrity",null).body().path("valid").asBoolean()).isTrue();
        var events = admin().call("GET","/api/v1/admin/medical/audits/"+id,null).body();
        assertThat(events).hasSize(1); String event = events.get(0).path("id").asText(), original = "处理虚构争议内容";
        try (var connection = dataSource.getConnection()) {
            try (var update = connection.prepareStatement("UPDATE audit_event SET reason=? WHERE id=?")) {
                update.setString(1,"模拟数据库篡改");update.setString(2,event);update.executeUpdate();
                try { assertThat(admin().call("GET","/api/v1/admin/medical/audit-integrity",null).body().path("valid").asBoolean()).isFalse(); }
                finally { update.setString(1,original);update.executeUpdate(); }
            }
            try (var query = connection.createStatement(); var row = query.executeQuery("SELECT sequence_no,event_id,previous_signature,signature FROM audit_signature ORDER BY sequence_no DESC LIMIT 1")) {
                assertThat(row.next()).isTrue(); long sequence = row.getLong(1); String eventId = row.getString(2), previous = row.getString(3), signature = row.getString(4);
                try (var delete = connection.prepareStatement("DELETE FROM audit_signature WHERE sequence_no=?")) { delete.setLong(1,sequence); delete.executeUpdate(); }
                try {
                    assertThat(admin().call("GET","/api/v1/admin/medical/audit-integrity",null).body().path("valid").asBoolean()).isFalse();
                    try (var rollback = connection.prepareStatement("UPDATE audit_chain_head SET sequence_no=?,signature=? WHERE id=1")) { rollback.setLong(1,sequence-1);rollback.setString(2,previous);rollback.executeUpdate(); }
                    // Even rolling the database head back cannot erase the separate filesystem checkpoint.
                    assertThat(admin().call("GET","/api/v1/admin/medical/audit-integrity",null).body().path("valid").asBoolean()).isFalse();
                    for (int attempt=0;attempt<2;attempt++) {
                        var blocked = reviewer.browser().call("POST","/api/v1/admin/medical/reviews",Map.of("appointmentId",s.appointmentId(),"scope","RECORD","reason","普通写入不能覆盖损坏的历史"));
                        assertThat(blocked.status()).isEqualTo(503); assertThat(blocked.body().path("error").path("code").asText()).isEqualTo("AUDIT_INTEGRITY_FAILED");
                    }
                    assertThat(admin().call("GET","/api/v1/admin/medical/audit-integrity",null).body().path("valid").asBoolean()).isFalse();
                }
                finally { try (var restore = connection.prepareStatement("INSERT INTO audit_signature (sequence_no,event_id,previous_signature,signature) VALUES (?,?,?,?)")) {
                    restore.setLong(1,sequence);restore.setString(2,eventId);restore.setString(3,previous);restore.setString(4,signature);restore.executeUpdate();
                    try (var restoreHead = connection.prepareStatement("UPDATE audit_chain_head SET sequence_no=?,signature=? WHERE id=1")) { restoreHead.setLong(1,sequence);restoreHead.setString(2,signature);restoreHead.executeUpdate(); }
                } }
            }
        }
        assertThat(admin().call("GET","/api/v1/admin/medical/audit-integrity",null).body().path("valid").asBoolean()).isTrue();
    }
    @Test void sharingRevocationSerializesWithAnAuthorizedAttachmentDownload() throws Exception {
        var s = scenario("ONLINE"); var upload = materials(s.patient().browser(),s.appointmentId(),true,"并发撤回材料",PDF).body();
        String attachment = upload.path("materials").get(0).path("attachments").get(0).path("id").asText();
        var entered = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1);
        org.mockito.Mockito.doAnswer(invocation -> { entered.countDown(); if (!release.await(10,java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("test timeout"); return invocation.callRealMethod(); }).when(fileStore).read(org.mockito.ArgumentMatchers.anyString());
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var reading = executor.submit(() -> download(s.doctor().browser(),"/api/v1/doctor/clinical/attachments/"+attachment));
            assertThat(entered.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var revoking = executor.submit(() -> s.patient().browser().call("POST",patientPath(s)+"/clinical/sharing/revoke",null));
            try {
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> revoking.get(300,java.util.concurrent.TimeUnit.MILLISECONDS)).isInstanceOf(java.util.concurrent.TimeoutException.class);
            } finally { release.countDown(); }
            assertThat(reading.get().statusCode()).isEqualTo(200); assertThat(revoking.get().status()).isEqualTo(200);
            assertThat(download(s.doctor().browser(),"/api/v1/doctor/clinical/attachments/"+attachment).statusCode()).isEqualTo(403);
        } finally { release.countDown(); org.mockito.Mockito.reset(fileStore); }
    }
    @Test void concurrentReviewGrantsAppendAnIntactAuditChainWithoutSnapshotFalseAlarms() throws Exception {
        var s = scenario("ONLINE"); var reviewer = reviewer(); var start = new java.util.concurrent.CountDownLatch(1);
        var snapshots = new java.util.concurrent.CountDownLatch(4);
        org.mockito.Mockito.doAnswer(invocation -> { var result = invocation.callRealMethod(); snapshots.countDown(); if (!snapshots.await(10,java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("snapshot barrier timeout"); return result; }).when(bookingService).careScope(s.appointmentId());
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<java.util.concurrent.Future<Reply>>();
            for (int index=0;index<4;index++) futures.add(executor.submit(() -> { start.await(); return reviewer.browser().call("POST","/api/v1/admin/medical/reviews",Map.of("appointmentId",s.appointmentId(),"scope","RECORD","reason","并发访问授权")); }));
            start.countDown(); for (var future : futures) assertThat(future.get().status()).isEqualTo(200);
        } finally { org.mockito.Mockito.reset(bookingService); }
        assertThat(admin().call("GET","/api/v1/admin/medical/audit-integrity",null).body().path("valid").asBoolean()).isTrue();
    }
    @Test void notificationsPersistBeforeDeliveryAndRollbackDoesNotEmit() throws Exception {
        var s=scenario("ONLINE");deliveryService.retry();String key="transaction:"+identifier();
        var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        try(var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var writer=executor.submit(() -> new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                deliveryService.publish(s.patient().id(),"TRANSACTION_TEST",s.appointmentId(),"未提交不得投递",key);entered.countDown();
                try { if(!release.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("timeout"); } catch(InterruptedException error) { throw new RuntimeException(error); }
                status.setRollbackOnly();
            }));
            try { assertThat(entered.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();deliveryService.retry();assertThat(deliveryMapper.byDedupe(key)).isNull(); }
            finally { release.countDown(); }
            writer.get();
        }
        assertThat(deliveryMapper.byDedupe(key)).isNull();
        var notification=deliveryMapper.byDedupe("appointment-confirmed:"+s.appointmentId());
        assertThat(notification.state()).isEqualTo("SENT");assertThat(notification.cursorNo()).isNotNull();
        assertThat(s.doctor().browser().call("GET","/api/v1/notifications",null).body().toString()).contains("APPOINTMENT_CONFIRMED");
    }
    @Test void confirmedCheckInAndCallNotificationsFollowBusinessFactsWithoutDuplicateCheckIn() throws Exception {
        var s=scenario("ONLINE");clock.now=Instant.ofEpochMilli(s.schedule().path("startsAt").asLong());
        for(int repeat=0;repeat<2;repeat++)assertThat(s.patient().browser().call("POST",patientPath(s)+"/check-in",null).status()).isEqualTo(200);
        assertThat(s.doctor().browser().call("POST","/api/v1/doctor/reception/"+s.appointmentId()+"/call",null).status()).isEqualTo(200);
        deliveryService.retry();deliveryService.retry();
        var patient=s.patient().browser().call("GET","/api/v1/notifications",null).body();
        assertThat(patient).hasSize(3);assertThat(patient.toString()).contains("APPOINTMENT_CONFIRMED","CHECK_IN","CALLED");
        var doctor=s.doctor().browser().call("GET","/api/v1/notifications",null).body();assertThat(doctor).hasSize(2);assertThat(doctor.toString()).contains("APPOINTMENT_CONFIRMED","CHECK_IN");
        for(var event:patient) { assertThat(event.path("state").asText()).isEqualTo("SENT");assertThat(event.path("attempts").asInt()).isEqualTo(1); }
    }
    @Test void deliveryFailureRetriesAreBoundedAndDoNotRollbackConfirmedAppointment() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalStateException("simulated stream failure")).when(sseTransport).deliver(org.mockito.ArgumentMatchers.any());
        try {
            var s=scenario("ONLINE");String key="appointment-confirmed:"+s.appointmentId();
            assertThat(s.patient().browser().call("GET",patientPath(s),null).body().path("state").asText()).isEqualTo("CONFIRMED");
            for(int attempt=1;attempt<=3;attempt++) {
                deliveryService.retry();var event=deliveryMapper.byDedupe(key);
                assertThat(event.state()).isEqualTo("FAILED");assertThat(event.attempts()).isEqualTo(attempt);assertThat(event.lastError()).isEqualTo("SSE_DELIVERY_FAILED");
                clock.now=clock.instant().plusSeconds(60);
            }
            org.mockito.Mockito.reset(sseTransport);deliveryService.retry();assertThat(deliveryMapper.byDedupe(key).attempts()).isEqualTo(3);
            var visible=s.patient().browser().call("GET","/api/v1/notifications",null);assertThat(visible.body().toString()).contains(s.appointmentId());
            long id=deliveryMapper.byDedupe(key).id();
            assertThat(s.patient().browser().call("POST","/api/v1/notifications/"+id+"/read",null).status()).isEqualTo(204);
            assertThat(s.patient().browser().call("POST","/api/v1/notifications/"+id+"/read",null).status()).isEqualTo(204);
            assertThat(actor("PATIENT").browser().call("POST","/api/v1/notifications/"+id+"/read",null).status()).isEqualTo(404);
        } finally { org.mockito.Mockito.reset(sseTransport); }
    }
    @Test void notificationCursorPagesAndAuthenticatedSseReplayCatchUpMoreThanOneHundred() throws Exception {
        while(deliveryService.retry()>0) { }
        var patient=actor("PATIENT");
        for(int index=0;index<105;index++)deliveryService.publish(patient.id(),"CATCHUP",patient.id(),"虚构补齐"+index,"catchup:"+patient.id()+":"+index);
        deliveryService.retry();deliveryService.retry();
        var first=patient.browser().call("GET","/api/v1/notifications?limit=100",null).body();assertThat(first).hasSize(100);
        long cursor=first.get(99).path("cursorNo").asLong();
        var last=patient.browser().call("GET","/api/v1/notifications?after="+cursor+"&limit=100",null).body();assertThat(last).hasSize(5);
        var request=HttpRequest.newBuilder(URI.create(patient.browser().base+"/api/v1/notifications/stream")).header("Authorization","Bearer "+patient.browser().access).header("Last-Event-ID",Long.toString(cursor)).GET().build();
        var response=patient.browser().http.send(request,HttpResponse.BodyHandlers.ofInputStream());assertThat(response.statusCode()).isEqualTo(200);
        try(var reader=new java.io.BufferedReader(new java.io.InputStreamReader(response.body(),StandardCharsets.UTF_8))) {
            var ids=new ArrayList<Long>();String line;
            while(ids.size()<5 && (line=reader.readLine())!=null)if(line.startsWith("id:"))ids.add(Long.parseLong(line.substring(3).trim()));
            assertThat(ids).containsExactlyElementsOf(java.util.stream.StreamSupport.stream(last.spliterator(),false).map(n -> n.path("cursorNo").asLong()).toList());
        }
        assertThat(patient.browser().call("GET","/api/v1/notifications?after=-1",null).status()).isEqualTo(400);
    }
    @Test void laterDeliveryWorkerDrainsEarlierCursorBeforeItsOwnEvent() throws Exception {
        while(deliveryService.retry()>0) { }
        var patient=actor("PATIENT");
        var request=HttpRequest.newBuilder(URI.create(patient.browser().base+"/api/v1/notifications/stream")).header("Authorization","Bearer "+patient.browser().access).GET().build();
        var response=patient.browser().http.send(request,HttpResponse.BodyHandlers.ofInputStream());assertThat(response.statusCode()).isEqualTo(200);
        var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        String firstKey="ordered-first:"+patient.id(),secondKey="ordered-second:"+patient.id();
        deliveryService.publish(patient.id(),"ORDERED",patient.id(),"第一条",firstKey);deliveryService.publish(patient.id(),"ORDERED",patient.id(),"第二条",secondKey);
        org.mockito.Mockito.doAnswer(invocation -> {
            cn.medical.delivery.DeliveryMapper.Notification event=invocation.getArgument(0);
            if(event.dedupeKey().equals(firstKey)) { entered.countDown();if(!release.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("delivery barrier timeout"); }
            return invocation.callRealMethod();
        }).when(sseTransport).deliver(org.mockito.ArgumentMatchers.any());
        try(var reader=new java.io.BufferedReader(new java.io.InputStreamReader(response.body(),StandardCharsets.UTF_8));var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var first=executor.submit(() -> deliveryService.retry());
            try {
                assertThat(entered.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();deliveryService.retry();
                var ids=new ArrayList<Long>();String line;
                while(ids.size()<2 && (line=reader.readLine())!=null)if(line.startsWith("id:"))ids.add(Long.parseLong(line.substring(3).trim()));
                assertThat(ids).containsExactly(deliveryMapper.byDedupe(firstKey).cursorNo(),deliveryMapper.byDedupe(secondKey).cursorNo());
            } finally { release.countDown(); }
            first.get();
        } finally { release.countDown();org.mockito.Mockito.reset(sseTransport); }
    }
    @Test void durableHoldAndSubstitutionTasksExpireOnceAndReminderHonorsWindow() throws Exception {
        var s=scenario("ONLINE");var head=doctor(s.departmentId());var substitute=doctor(s.departmentId());assignHead(s,head);
        approveSubstitution(s,head,substitute);
        var choice=s.patient().browser().call("GET","/api/v1/patient/substitution-choices",null).body().get(0);
        clock.now=Instant.ofEpochMilli(choice.path("deadlineAt").asLong());
        assertThat(admin().call("POST","/api/v1/admin/test/runtime/scan",null).status()).isEqualTo(200);
        assertThat(s.patient().browser().call("GET",patientPath(s),null).body().path("state").asText()).isEqualTo("CANCELLED");
        assertThat(s.patient().browser().call("GET",patientPath(s)+"/billing",null).body().path("refund").path("state").asText()).isEqualTo("REFUND_PENDING");
        assertThat(admin().call("POST","/api/v1/admin/test/runtime/scan",null).status()).isEqualTo(200);
        clock.now=Instant.now();var pending=scenario("ONLINE");var other=actor("PATIENT");
        var hold=other.browser().call("POST","/api/v1/patient/appointments",Map.of("scheduleId",pending.schedule().path("id").asText(),"source","ADVANCE","idempotencyKey",identifier())).body();
        clock.now=Instant.ofEpochMilli(hold.path("holdExpiresAt").asLong());admin().call("POST","/api/v1/admin/test/runtime/scan",null);
        try(var connection=dataSource.getConnection();var query=connection.prepareStatement("SELECT state FROM appointment WHERE id=?")) { query.setString(1,hold.path("id").asText());try(var row=query.executeQuery()){assertThat(row.next()).isTrue();assertThat(row.getString(1)).isEqualTo("EXPIRED");} }
        clock.now=Instant.ofEpochMilli(pending.schedule().path("startsAt").asLong()).minus(Duration.ofMinutes(30));admin().call("POST","/api/v1/admin/test/runtime/scan",null);
        var reminder=deliveryMapper.byDedupe("check-in-reminder:"+pending.appointmentId());assertThat(reminder).isNotNull();
        admin().call("POST","/api/v1/admin/test/runtime/scan",null);assertThat(deliveryMapper.byDedupe("check-in-reminder:"+pending.appointmentId()).id()).isEqualTo(reminder.id());
    }
    @Test void expiredAiLeaseIsRecoveredAndDelayedOldWorkerCannotCreateAnotherResult() throws Exception {
        var s=scenario("ONLINE");String text="租约恢复虚构咳嗽3天";String consentId=consent(s,true,"LOCAL_MOCK",text).path("id").asText();
        var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);var count=new java.util.concurrent.atomic.AtomicInteger();
        org.mockito.Mockito.doAnswer(invocation -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            if(count.incrementAndGet()==1) { entered.countDown();if(!release.await(15,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("provider timeout"); }
            return invocation.callRealMethod();
        }).when(aiProvider).analyze(org.mockito.ArgumentMatchers.eq(text),org.mockito.ArgumentMatchers.anyList());
        try(var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var first=executor.submit(() -> analyze(s,consentId,text,identifier()));
            try {
                assertThat(entered.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();clock.now=clock.instant().plusSeconds(31);
                assertThat(admin().call("POST","/api/v1/admin/test/runtime/scan",null).status()).isEqualTo(200);
            } finally { release.countDown(); }
            var result=first.get();assertThat(result.status()).isEqualTo(200);assertThat(result.body().path("state").asText()).isEqualTo("SUCCEEDED");assertThat(result.body().path("attempts").asInt()).isEqualTo(2);
            assertThat(s.patient().browser().call("GET",patientPath(s)+"/ai/results",null).body()).hasSize(1);
            assertThat(count.get()).isEqualTo(2);
        } finally { release.countDown();org.mockito.Mockito.reset(aiProvider); }
    }
    @Test void abandonedAiLeaseExhaustionAndRevocationPublishTerminalNotificationsOnce() throws Exception {
        for(boolean revoke:List.of(false,true)) {
            var s=scenario("ONLINE");String text="虚构终态症状"+identifier();String consentId=consent(s,true,"LOCAL_MOCK",text).path("id").asText();
            org.mockito.Mockito.doThrow(new AssertionError("simulated worker loss")).when(aiProvider).analyze(org.mockito.ArgumentMatchers.eq(text),org.mockito.ArgumentMatchers.anyList());
            try { assertThat(analyze(s,consentId,text,identifier()).status()).isEqualTo(500); }
            finally { org.mockito.Mockito.reset(aiProvider); }
            var task=s.patient().browser().call("GET",patientPath(s)+"/ai/results",null).body().get(0);String id=task.path("taskId").asText();
            if(revoke)assertThat(s.patient().browser().call("POST",patientPath(s)+"/ai/consents/"+consentId+"/revoke",null).status()).isEqualTo(204);
            try(var connection=dataSource.getConnection();var query=connection.prepareStatement("UPDATE ai_task SET attempts=3,lease_expires_at=? WHERE id=?")) {
                query.setLong(1,clock.millis()-1);query.setString(2,id);assertThat(query.executeUpdate()).isEqualTo(1);
            }
            assertThat(admin().call("POST","/api/v1/admin/test/runtime/scan",null).status()).isEqualTo(200);
            var terminal=s.patient().browser().call("GET",patientPath(s)+"/ai/results",null).body().get(0);
            assertThat(terminal.path("state").asText()).isEqualTo(revoke?"CANCELLED":"FAILED");assertThat(terminal.path("attempts").asInt()).isEqualTo(3);
            var event=deliveryMapper.byDedupe("ai-result:"+id);assertThat(event).isNotNull();
            admin().call("POST","/api/v1/admin/test/runtime/scan",null);assertThat(deliveryMapper.byDedupe("ai-result:"+id).id()).isEqualTo(event.id());
        }
    }
    @Test void durableTaskErrorsRespectBackoffAndStopAfterThreeAttempts() throws Exception {
        String object=UUID.randomUUID().toString(),id="retry:"+object;
        runtimeTasks.insert(id,"HOLD_EXPIRY",object,clock.millis());
        org.mockito.Mockito.doThrow(new IllegalStateException("simulated business failure")).when(bookingService).expireHold(object);
        try {
            for(int attempt=1;attempt<=3;attempt++) {
                assertThat(admin().call("POST","/api/v1/admin/test/runtime/scan",null).status()).isEqualTo(200);
                var task=runtimeTasks.lock(id);assertThat(task.attempts()).isEqualTo(attempt);assertThat(task.state()).isEqualTo(attempt==3?"FAILED":"PENDING");
                assertThat(task.lastError()).isEqualTo("TASK_EXECUTION_FAILED");
                admin().call("POST","/api/v1/admin/test/runtime/scan",null);assertThat(runtimeTasks.lock(id).attempts()).isEqualTo(attempt);
                clock.now=clock.instant().plusSeconds(60);
            }
            admin().call("POST","/api/v1/admin/test/runtime/scan",null);assertThat(runtimeTasks.lock(id).attempts()).isEqualTo(3);
            org.mockito.Mockito.verify(bookingService,org.mockito.Mockito.times(3)).expireHold(object);
        } finally { org.mockito.Mockito.reset(bookingService); }
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
