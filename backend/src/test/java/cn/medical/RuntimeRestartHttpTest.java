package cn.medical;

import cn.medical.ai.*;
import cn.medical.delivery.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import static org.assertj.core.api.Assertions.*;

class RuntimeRestartHttpTest extends HttpFixture {
    @TestConfiguration static class CrashProvider {
        @Bean @Primary AiTextProvider crashProvider() {
            return new AiTextProvider() {
                public String name(){return "LOCAL_MOCK";}
                public AiService.Analysis analyze(String text,List<String> departments) { throw new AssertionError("simulated process loss after durable claim"); }
            };
        }
    }
    private ServletWebServerApplicationContext start(boolean crash) {
        var sources=new ArrayList<Class<?>>();Collections.addAll(sources,MedicalApplication.class,TestAnswers.class,BookingHttpTest.TimeControl.class,RuntimeRecoveryControl.class);
        if(crash)sources.add(CrashProvider.class);
        return (ServletWebServerApplicationContext)new SpringApplicationBuilder(sources.toArray(Class<?>[]::new))
            .run("--server.port=0","--spring.main.banner-mode=off","--medical.captcha-ttl=2m","--spring.data.redis.database=9","--medical.delivery-scanner-enabled=false","--medical.refund-scanner-enabled=false","--medical.reception-scan-delay=3600000");
    }
    @Test void pendingNotificationAndAbandonedAiClaimRecoverAcrossRealApplicationRestart() throws Exception {
        Browser patient;String taskId,patientId,consentId,holdId;String key=identifier();String text="重启恢复虚构症状3天";
        try(var first=start(true)) {
            patient=new Browser(first.getWebServer().getPort());String name=identifier();var registered=patient.register(name,"PATIENT");patientId=registered.body().path("account").path("id").asText();assertThat(patient.login(name,PASSWORD).status()).isEqualTo(200);
            var grant=patient.call("POST","/api/v1/patient/ai/consents",Map.of("accepted",true,"provider","LOCAL_MOCK","purpose","PRECONSULTATION_SUMMARY","scope","SYMPTOM_TEXT","symptomText",text));
            consentId=grant.body().path("id").asText();
            var crashed=patient.call("POST","/api/v1/patient/ai/preconsultations",Map.of("consentId",consentId,"symptomText",text,"idempotencyKey",key));assertThat(crashed.status()).isEqualTo(500);
            var stored=patient.call("GET","/api/v1/patient/ai/results",null).body().get(0);taskId=stored.path("taskId").asText();assertThat(stored.path("state").asText()).isEqualTo("RUNNING");assertThat(stored.path("attempts").asInt()).isEqualTo(1);
            first.getBean(DeliveryService.class).publish(patientId,"RESTART_PENDING",taskId,"重启后补发","restart:"+taskId);
            var admin=new Browser(first.getWebServer().getPort());assertThat(admin.login("admin-foundation-test","fictional-test-administrator-password").status()).isEqualTo(200);
            String department=admin.call("POST","/api/v1/admin/catalog/departments",Map.of("name",identifier(),"description","虚构","active",true)).body().path("id").asText();
            var doctor=new Browser(first.getWebServer().getPort());String doctorId=doctor.register(identifier(),"DOCTOR").body().path("account").path("id").asText();
            assertThat(admin.call("PUT","/api/v1/admin/catalog/doctors/"+doctorId,Map.of("displayName","虚构重启医生","title","主治医师","biography","虚构","departmentId",department,"credentialStatus","CERTIFIED")).status()).isEqualTo(200);
            var schedule=admin.call("POST","/api/v1/admin/catalog/schedules",Map.of("doctorId",doctorId,"departmentId",department,"clinicDate",LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(2).toString(),"session","AM","serviceMode","ONLINE","capacity",1,"feeMinor",1000));
            var hold=patient.call("POST","/api/v1/patient/appointments",Map.of("scheduleId",schedule.body().path("id").asText(),"source","ADVANCE","idempotencyKey",identifier()));assertThat(hold.status()).isEqualTo(201);holdId=hold.body().path("id").asText();
        }
        try(var restarted=start(false)) {
            var clock=restarted.getBean(BookingHttpTest.AdjustableClock.class);clock.now=Instant.now().plus(Duration.ofMinutes(11));
            var recovered=patient.copyCookies(restarted.getWebServer().getPort());recovered.access=patient.access;
            var admin=new Browser(restarted.getWebServer().getPort());assertThat(admin.login("admin-foundation-test","fictional-test-administrator-password").status()).isEqualTo(200);
            assertThat(admin.call("POST","/api/v1/admin/test/runtime/scan",null).status()).isEqualTo(200);
            try(var connection=restarted.getBean(javax.sql.DataSource.class).getConnection();var query=connection.prepareStatement("SELECT state FROM appointment WHERE id=?")) { query.setString(1,holdId);try(var row=query.executeQuery()){assertThat(row.next()).isTrue();assertThat(row.getString(1)).isEqualTo("EXPIRED");} }
            var results=recovered.call("GET","/api/v1/patient/ai/results",null).body();assertThat(results).hasSize(1);assertThat(results.get(0).path("state").asText()).isEqualTo("SUCCEEDED");assertThat(results.get(0).path("attempts").asInt()).isEqualTo(2);
            var duplicate=recovered.call("POST","/api/v1/patient/ai/preconsultations",Map.of("consentId",consentId,"symptomText",text,"idempotencyKey",key));assertThat(duplicate.body().path("taskId").asText()).isEqualTo(taskId);
            assertThat(admin.call("POST","/api/v1/admin/test/runtime/scan",null).status()).isEqualTo(200);
            var notifications=recovered.call("GET","/api/v1/notifications",null).body();assertThat(notifications).hasSize(2);assertThat(notifications.toString()).contains("RESTART_PENDING","AI_RESULT");
            assertThat(admin.call("GET","/api/v1/admin/medical/audit-integrity",null).body().path("valid").asBoolean()).isTrue();
        }
    }
}
