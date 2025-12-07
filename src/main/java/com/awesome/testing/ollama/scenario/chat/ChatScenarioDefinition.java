package com.awesome.testing.ollama.scenario.chat;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.Data;
import org.springframework.util.StringUtils;

@Data
public class ChatScenarioDefinition {

    private String name;
    private String prompt;
    private List<ChatScenarioStageDefinition> stages;

    public Optional<ChatScenarioStageDefinition> stageForUserPrompt() {
        return stages.stream()
                .filter(stage -> ChatScenarioTrigger.USER.equals(stage.getTriggerEnum()))
                .findFirst();
    }

    public Optional<ChatScenarioStageDefinition> stageForTool(String toolName) {
        if (!StringUtils.hasText(toolName)) {
            return Optional.empty();
        }
        String normalized = toolName.toLowerCase(Locale.ROOT);
        return stages.stream()
                .filter(stage -> ChatScenarioTrigger.TOOL.equals(stage.getTriggerEnum()))
                .filter(stage -> normalized.equals(stage.getToolNameNormalized()))
                .findFirst();
    }

    public String normalizedPrompt() {
        return prompt == null ? "" : prompt.trim().toLowerCase(Locale.ROOT);
    }
}
