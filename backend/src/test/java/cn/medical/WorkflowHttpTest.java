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
    @Autowired javax.sql.DataSource dataSource;
    @Autowired org.springframework.core.env.Environment environment;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean cn.medical.clinical.ClinicalFileStore fileStore;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean cn.medical.ai.LocalAiTextProvider aiProvider;
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
                        String filter = Set.of("ai_consent", "ai_task", "ai_result").contains(table) ? " WHERE appointment_id IS NOT NULL"
                            : table.equals("ai_call_log") ? " WHERE task_id IN (SELECT id FROM `" + sourceDatabase + "`.ai_task WHERE appointment_id IS NOT NULL)" : "";
                        copy.executeUpdate("DELETE FROM `" + database + "`.`" + table + "`");
                        copy.executeUpdate("INSERT INTO `" + database + "`.`" + table + "` (" + names + ") SELECT " + names + " FROM `" + sourceDatabase + "`.`" + table + "`" + filter);
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
