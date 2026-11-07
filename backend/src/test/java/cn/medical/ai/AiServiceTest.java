package cn.medical.ai;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import cn.medical.catalog.CatalogService;
import cn.medical.clinical.ClinicalService;
import cn.medical.booking.BookingService;
import cn.medical.identity.ApiFailure;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AiServiceTest {
    @Test
    void symptomAnalysisRequiresAnAcceptedConsent() {
        var ai = mock(AiMapper.class);
        var clinical = mock(ClinicalService.class);
        var booking = mock(BookingService.class);
        var catalog = mock(CatalogService.class);
        var provider = mock(AiTextProvider.class);
        when(booking.careScope("appointment")).thenReturn(new BookingService.CareScope(
            "appointment", "patient", "doctor", "ONLINE", "CONFIRMED"));
        when(ai.consent("consent", "appointment", "patient")).thenReturn(new AiMapper.Consent(
            "consent", "appointment", "patient", "LOCAL_MOCK", "PRECONSULTATION_SUMMARY", "SYMPTOM_TEXT", false, 1L, null, null));
        var service = new AiService(ai, clinical, booking, catalog, provider, new ObjectMapper(),
            Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC), mock(org.springframework.transaction.PlatformTransactionManager.class));

        assertThatThrownBy(() -> service.analyze("patient", "appointment",
            new AiService.AnalyzeInput("consent", "咳嗽", "request-1")))
            .isInstanceOfSatisfying(ApiFailure.class, failure -> org.assertj.core.api.Assertions.assertThat(failure.code)
                .isEqualTo("AI_CONSENT_REQUIRED"));
        verifyNoInteractions(clinical);
        verifyNoInteractions(provider);
    }
}
