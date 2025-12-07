package com.awesome.testing.ollama.scenario.chat;

import com.awesome.testing.ollama.dto.ChatMessageDto;
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
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class ChatScenarioRepository {

    private static final String SCENARIO_PATH = "scenarios/chat-scenarios.json";

    private final Map<String, ChatScenarioDefinition> promptIndex = new LinkedHashMap<>();
    private final List<ChatScenarioDefinition> definitions;

    public ChatScenarioRepository(ObjectMapper objectMapper) {
        this.definitions = loadScenarios(objectMapper);
        definitions.forEach(def -> promptIndex.put(def.normalizedPrompt(), def));
        log.info("Loaded {} chat scenario(s) from {}", definitions.size(), SCENARIO_PATH);
    }

    public Optional<ChatScenarioDefinition> findByPrompt(String prompt) {
        if (!StringUtils.hasText(prompt)) {
            return Optional.empty();
        }
        return Optional.ofNullable(promptIndex.get(normalize(prompt)));
    }

    public Optional<ChatScenarioDefinition> findScenarioForConversation(List<ChatMessageDto> messages) {
        if (CollectionUtils.isEmpty(messages)) {
            return Optional.empty();
        }
        return messages.stream()
                .filter(msg -> "user".equalsIgnoreCase(msg.getRole()))
                .map(ChatMessageDto::getContent)
                .map(this::findByPrompt)
                .flatMap(Optional::stream)
                .findFirst();
    }

    public List<String> supportedPrompts() {
        return definitions.stream()
                .map(ChatScenarioDefinition::getPrompt)
                .toList();
    }

    private List<ChatScenarioDefinition> loadScenarios(ObjectMapper objectMapper) {
        Resource resource = new ClassPathResource(SCENARIO_PATH);
        if (!resource.exists()) {
            log.warn("Chat scenario file {} not found, falling back to empty list", SCENARIO_PATH);
            return Collections.emptyList();
        }
        try {
            ChatScenarioWrapper wrapper = objectMapper.readValue(resource.getInputStream(), ChatScenarioWrapper.class);
            return Optional.ofNullable(wrapper.getScenarios()).orElse(Collections.emptyList());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read chat scenario definitions", e);
        }
    }

    private String normalize(String prompt) {
        return prompt.trim().toLowerCase(Locale.ROOT);
    }

    @lombok.Data
    private static class ChatScenarioWrapper {
        private List<ChatScenarioDefinition> scenarios;
    }
}
