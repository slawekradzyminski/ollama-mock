package com.awesome.testing.ollama.scenario.generate;

import java.util.List;
import java.util.Locale;
import lombok.Data;

@Data
public class GenerateScenarioDefinition {
    private String prompt;
    private List<GenerateScenarioChunkDefinition> chunks;

    public String normalizedPrompt() {
        return prompt == null ? "" : prompt.trim().toLowerCase(Locale.ROOT);
    }
}
