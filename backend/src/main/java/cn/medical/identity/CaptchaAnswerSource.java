package cn.medical.identity;

@FunctionalInterface
public interface CaptchaAnswerSource {
    String next();
}
