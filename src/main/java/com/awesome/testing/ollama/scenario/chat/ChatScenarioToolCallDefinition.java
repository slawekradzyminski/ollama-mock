package com.awesome.testing.ollama.scenario.chat;

import java.util.Map;
import lombok.Data;

@Data
public class ChatScenarioToolCallDefinition {
    private String name;
    private Map<String, Object> arguments;
}
