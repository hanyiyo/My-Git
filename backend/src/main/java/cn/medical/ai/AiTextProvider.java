package cn.medical.ai;

import java.util.List;

public interface AiTextProvider {
    String name();
    default String modelVersion() { return "local-rules-1"; }
    default String promptVersion() { return "text-summary-1"; }
    AiService.Analysis analyze(String symptomText, List<String> departmentNames);
}
