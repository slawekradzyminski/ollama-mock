package com.awesome.testing.ollama.scenario.chatbasic;

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
public class ChatDialogueScenarioRepository {

    private static final String SCENARIO_PATH = "scenarios/chat-dialog-scenarios.json";

    private final Map<String, ChatDialogueScenarioDefinition> index = new LinkedHashMap<>();
    private final List<ChatDialogueScenarioDefinition> definitions;

    public ChatDialogueScenarioRepository(ObjectMapper objectMapper) {
        this.definitions = load(objectMapper);
        definitions.forEach(def -> index.put(def.normalizedPrompt(), def));
        log.info("Loaded {} chat dialogue scenario(s) from {}", definitions.size(), SCENARIO_PATH);
    }

    public Optional<ChatDialogueScenarioDefinition> findScenario(List<ChatMessageDto> messages) {
        if (CollectionUtils.isEmpty(messages)) {
            return Optional.empty();
        }
        return messages.stream()
                .filter(msg -> "user".equalsIgnoreCase(msg.getRole()))
                .map(ChatMessageDto::getContent)
                .filter(StringUtils::hasText)
                .map(this::findByPrompt)
                .flatMap(Optional::stream)
                .findFirst();
    }

    public List<String> supportedPrompts() {
        return definitions.stream()
                .map(ChatDialogueScenarioDefinition::getPrompt)
                .toList();
    }

    private Optional<ChatDialogueScenarioDefinition> findByPrompt(String content) {
        return Optional.ofNullable(index.get(normalize(content)));
    }

    private List<ChatDialogueScenarioDefinition> load(ObjectMapper mapper) {
        Resource resource = new ClassPathResource(SCENARIO_PATH);
        if (!resource.exists()) {
            log.warn("Chat dialogue scenario file {} missing, returning empty list", SCENARIO_PATH);
            return Collections.emptyList();
        }
        try {
            ChatDialogueWrapper wrapper = mapper.readValue(resource.getInputStream(), ChatDialogueWrapper.class);
            return Optional.ofNullable(wrapper.getScenarios()).orElse(Collections.emptyList());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read chat dialogue scenarios", e);
        }
    }

    private String normalize(String prompt) {
        return prompt.trim().toLowerCase(Locale.ROOT);
    }

    @lombok.Data
    private static class ChatDialogueWrapper {
        private List<ChatDialogueScenarioDefinition> scenarios;
    }
}
