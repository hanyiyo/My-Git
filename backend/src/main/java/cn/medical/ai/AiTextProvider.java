package cn.medical.ai;

import java.util.List;

public interface AiTextProvider {
    String name();
    AiService.Analysis analyze(String symptomText, List<String> departmentNames);
}
