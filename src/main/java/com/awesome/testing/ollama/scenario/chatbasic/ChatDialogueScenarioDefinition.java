package com.awesome.testing.ollama.scenario.chatbasic;

import java.util.List;
import java.util.Locale;
import lombok.Data;

@Data
public class ChatDialogueScenarioDefinition {

    private String prompt;
    private List<ChatDialogueChunkDefinition> chunks;

    public String normalizedPrompt() {
        return prompt == null ? "" : prompt.trim().toLowerCase(Locale.ROOT);
    }
}
