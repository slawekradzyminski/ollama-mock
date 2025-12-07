package com.awesome.testing.ollama.scenario.generate;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GenerateScenarioRepository {

    private static final String SCENARIO_PATH = "scenarios/generate-scenarios.json";

    private final Map<String, GenerateScenarioDefinition> promptIndex = new LinkedHashMap<>();
    private final List<GenerateScenarioDefinition> definitions;

    public GenerateScenarioRepository(ObjectMapper objectMapper) {
        this.definitions = load(objectMapper);
        definitions.forEach(def -> promptIndex.put(def.normalizedPrompt(), def));
        log.info("Loaded {} generate scenario(s) from {}", definitions.size(), SCENARIO_PATH);
    }

    public Optional<GenerateScenarioDefinition> findByPrompt(String prompt) {
        if (prompt == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(promptIndex.get(normalize(prompt)));
    }

    public List<String> supportedPrompts() {
        return definitions.stream()
                .map(GenerateScenarioDefinition::getPrompt)
                .toList();
    }

    private List<GenerateScenarioDefinition> load(ObjectMapper objectMapper) {
        Resource resource = new ClassPathResource(SCENARIO_PATH);
        if (!resource.exists()) {
            log.warn("Generate scenario file {} not found, defaulting to empty list", SCENARIO_PATH);
            return Collections.emptyList();
        }
        try {
            GenerateScenarioWrapper wrapper = objectMapper.readValue(resource.getInputStream(), GenerateScenarioWrapper.class);
            return Optional.ofNullable(wrapper.getScenarios()).orElse(Collections.emptyList());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read generate scenario definitions", e);
        }
    }

    private String normalize(String prompt) {
        return prompt.trim().toLowerCase(Locale.ROOT);
    }

    @lombok.Data
    private static class GenerateScenarioWrapper {
        private List<GenerateScenarioDefinition> scenarios;
    }
}
