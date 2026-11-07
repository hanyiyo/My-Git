package cn.medical.ai;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class LocalAiTextProvider implements AiTextProvider {
    private static final Pattern DURATION = Pattern.compile("(?:持续|已有|大约)?\\s*(\\d+\\s*(?:小时|天|周|月))");

    @Override public String name() { return "LOCAL_MOCK"; }

    @Override
    public AiService.Analysis analyze(String symptomText, List<String> departmentNames) {
        String text = symptomText.trim();
        String duration = DURATION.matcher(text).results().findFirst().map(match -> match.group(1)).orElse("未提供");
        String hint = departmentNames.stream().filter(name -> matchesDepartment(text, name)).findFirst()
            .orElseGet(() -> departmentNames.stream().findFirst().orElse("未匹配科室"));
        String risk = text.matches(".*(胸痛|呼吸困难|意识不清|自伤|大出血).*" )
            ? "出现高风险信号，请立即联系急救或人工医疗服务。" : "未识别到需要立即升级的关键词，请由医生进一步判断。";
        String questions = "症状从何时开始？是否正在加重，是否有既往病史或正在使用的药物？";
        String summary = "根据你提交的文字整理：" + text.substring(0, Math.min(text.length(), 240));
        return new AiService.Analysis(summary, duration, hint, questions, risk,
            "这是辅助整理，不是诊断、处方或医生结论。请以医生接诊结果为准。", name());
    }

    private boolean matchesDepartment(String text, String department) {
        String value = department.toLowerCase();
        return (value.contains("内") && text.matches(".*(发热|咳嗽|头痛|腹痛|乏力|感冒).*"))
            || (value.contains("外") && text.matches(".*(伤口|外伤|骨折|扭伤).*"))
            || (value.contains("妇") && text.matches(".*(月经|怀孕|妇科|产后).*"))
            || (value.contains("儿") && text.matches(".*(孩子|儿童|宝宝).*"));
    }
}
