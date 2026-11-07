package cn.medical.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class LocalAiTextProviderTest {
    private final LocalAiTextProvider provider = new LocalAiTextProvider();

    @Test
    void extractsDurationRiskAndSafetyBoundaryWithoutDiagnosis() {
        var result = provider.analyze("咳嗽已经 3 天，今天出现胸痛", List.of("内科", "外科"));

        assertThat(result.duration()).isEqualTo("3 天");
        assertThat(result.departmentHint()).isEqualTo("内科");
        assertThat(result.riskAlert()).contains("急救");
        assertThat(result.safetyNotice()).contains("不是诊断、处方");
        assertThat(result.provider()).isEqualTo("LOCAL_MOCK");
    }

    @Test
    void usesConfiguredDepartmentFactsAsRecommendationHints() {
        var result = provider.analyze("脚踝扭伤", List.of("内科", "外科"));

        assertThat(result.departmentHint()).isEqualTo("外科");
        assertThat(result.followUpQuestions()).contains("既往病史");
    }

    @Test void unmatchedSymptomsDoNotSelectAnArbitraryDepartment() {
        assertThat(provider.analyze("皮肤瘙痒持续3天", List.of("内科", "外科")).departmentHint()).contains("未匹配科室");
    }
}
