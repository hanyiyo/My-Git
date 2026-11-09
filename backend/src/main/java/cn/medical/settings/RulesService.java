package cn.medical.settings;

import cn.medical.identity.ApiFailure;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.*;
import java.time.Clock;
import java.time.LocalTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RulesService {
    public record Rules(
        @NotNull @Pattern(regexp="(?:[01][0-9]|2[0-3]):[0-5][0-9]") String amStart,
        @NotNull @Pattern(regexp="(?:[01][0-9]|2[0-3]):[0-5][0-9]") String amEnd,
        @NotNull @Pattern(regexp="(?:[01][0-9]|2[0-3]):[0-5][0-9]") String pmStart,
        @NotNull @Pattern(regexp="(?:[01][0-9]|2[0-3]):[0-5][0-9]") String pmEnd,
        @Min(0) @Max(1440) int checkInBeforeMinutes,
        @Min(0) @Max(1440) int checkInCutoffMinutes,
        @Min(1) @Max(240) int missedWaitMinutes,
        @Min(1) @Max(365) int approvalMaxDays,
        @Min(1) @Max(8760) int approvalCutoffHours,
        @Min(1) @Max(720) int medicinePaymentHours,
        @Min(0) @Max(100000000) long ordinaryFeeMinor,
        @Min(0) @Max(100000000) long expertFeeMinor) {}
    private final RulesMapper mapper;
    private final ObjectMapper json;
    private final Clock clock;
    public RulesService(RulesMapper mapper, ObjectMapper json, Clock clock) { this.mapper=mapper; this.json=json; this.clock=clock; }
    public static Rules defaults() { return new Rules("08:00","12:00","14:00","18:00",30,15,5,7,24,24,1000,2000); }
    @Transactional(readOnly=true)
    public Rules current() {
        String body=mapper.body();
        if(body==null) return defaults();
        try { return json.readValue(body,Rules.class); }
        catch(JsonProcessingException e) { throw new IllegalStateException("Saved hospital rules are invalid",e); }
    }
    @Transactional
    public Rules save(Rules rules) {
        LocalTime a=LocalTime.parse(rules.amStart()),b=LocalTime.parse(rules.amEnd()),c=LocalTime.parse(rules.pmStart()),d=LocalTime.parse(rules.pmEnd());
        if(!a.isBefore(b)||b.isAfter(c)||!c.isBefore(d)
            ||rules.checkInCutoffMinutes()>=Math.min(java.time.Duration.between(a,b).toMinutes(),java.time.Duration.between(c,d).toMinutes()))
            throw new ApiFailure(400,"INVALID_RULES","上午下午必须有序且不重叠，签到截止必须早于门诊结束且晚于开始");
        try { mapper.save(json.writeValueAsString(rules),clock.millis()); }
        catch(JsonProcessingException e) { throw new IllegalStateException("Cannot serialize hospital rules",e); }
        return rules;
    }
}
