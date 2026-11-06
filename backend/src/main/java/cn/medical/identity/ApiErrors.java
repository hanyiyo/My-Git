package cn.medical.identity;

import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiErrors {
    static Map<String, Object> body(String code, String message) { return Map.of("error", Map.of("code", code, "message", message)); }
    public static void write(HttpServletResponse response, int status, String code, String message) throws java.io.IOException {
        response.setStatus(status); response.setContentType("application/json;charset=UTF-8");
        new ObjectMapper().writeValue(response.getOutputStream(), body(code, message));
    }
    @ExceptionHandler(ApiFailure.class)
    ResponseEntity<?> failure(ApiFailure failure) { return ResponseEntity.status(failure.status).body(body(failure.code, failure.getMessage())); }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<?> input(Exception error) { return ResponseEntity.badRequest().body(body("INVALID_INPUT", "请检查账号、密码和验证码输入")); }
    @ExceptionHandler(RedisConnectionFailureException.class)
    ResponseEntity<?> redis(Exception error) { return ResponseEntity.status(503).body(body("CAPTCHA_UNAVAILABLE", "验证码服务暂不可用，请稍后重试")); }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<?> dependency(Exception error) { return ResponseEntity.status(503).body(body("SERVICE_UNAVAILABLE", "服务暂不可用，请稍后重试")); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(Exception error) { return ResponseEntity.status(500).body(body("INTERNAL_ERROR", "服务暂不可用")); }
}
