package cn.medical;

import cn.medical.identity.CaptchaAnswerSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestAnswers {
    // Randomness is the test seam; HTTP payloads and real Redis never expose the answer.
    @Bean @Primary CaptchaAnswerSource knownAnswer() { return () -> "AB7K"; }
}
