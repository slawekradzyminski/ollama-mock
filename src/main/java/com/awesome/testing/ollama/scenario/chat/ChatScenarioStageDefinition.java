package com.awesome.testing.ollama.scenario.chat;

import lombok.Data;

@Data
public class ChatScenarioStageDefinition {

    private String trigger;
    private String toolName;
    private String thinking;
    private String response;
    /** When set, the answer is built from the tool results instead of {@link #response}. */
    private ChatScenarioGroundedAnswer grounded;
    private ChatScenarioToolCallDefinition toolCall;

    public ChatScenarioTrigger getTriggerEnum() {
        return ChatScenarioTrigger.from(trigger);
    }

    public String getToolNameNormalized() {
        return toolName == null ? null : toolName.trim().toLowerCase();
    }
}
