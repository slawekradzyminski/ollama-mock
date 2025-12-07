package com.awesome.testing.ollama.service;

import com.awesome.testing.ollama.config.OllamaMockProperties;
import com.awesome.testing.ollama.dto.ChatMessageDto;
import com.awesome.testing.ollama.dto.ChatRequestDto;
import com.awesome.testing.ollama.dto.ChatResponseDto;
import com.awesome.testing.ollama.scenario.chatbasic.ChatDialogueScenarioDefinition;
import com.awesome.testing.ollama.scenario.chatbasic.ChatDialogueScenarioRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final Duration STREAM_DELAY = Duration.ofMillis(150);

    private final OllamaMockProperties properties;
    private final ChatDialogueScenarioRepository scenarioRepository;

    public Flux<ChatResponseDto> chatStream(ChatRequestDto request) {
        String model = resolveModel(request.getModel());
        boolean thinkingEnabled = Boolean.TRUE.equals(request.getThink());
        Flux<ChatResponseDto> convo = scenarioRepository.findScenario(request.getMessages())
                .map(scenario -> Flux.fromIterable(buildChunks(model, scenario, thinkingEnabled)))
                .orElseGet(() -> Flux.just(unsupportedPrompt(model)));
        return convo.concatWithValues(doneChunk(model))
                .delayElements(STREAM_DELAY);
    }

    public Mono<ChatResponseDto> chatSingle(ChatRequestDto request) {
        return chatStream(request)
                .filter(resp -> resp.getMessage() != null && !resp.isDone())
                .last();
    }

    private String resolveModel(String requestedModel) {
        if (StringUtils.hasText(requestedModel)) {
            return requestedModel;
        }
        return properties.getDefaultModel();
    }

    private List<ChatResponseDto> buildChunks(String model,
                                              ChatDialogueScenarioDefinition scenario,
                                              boolean thinkingEnabled) {
        List<ChatResponseDto> outputs = new ArrayList<>();
        scenario.getChunks().forEach(chunk -> {
            if (thinkingEnabled && StringUtils.hasText(chunk.getThinking())) {
                outputs.add(thinkingChunk(model, chunk.getThinking()));
            }
            if (StringUtils.hasText(chunk.getResponse())) {
                outputs.add(contentChunk(model, chunk.getResponse()));
            }
        });
        return outputs;
    }

    private ChatResponseDto unsupportedPrompt(String model) {
        String message = formatSupportedPromptMessage("Sorry, only these chat prompts are supported:");
        return contentChunk(model, message);
    }

    private String formatSupportedPromptMessage(String prefix) {
        List<String> prompts = scenarioRepository.supportedPrompts();
        if (prompts.isEmpty()) {
            return prefix + " (no chat prompts configured)";
        }
        String joined = prompts.stream()
                .map(prompt -> "- " + prompt)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        return prefix + "\n" + joined;
    }

    private ChatResponseDto thinkingChunk(String model, String content) {
        ChatMessageDto message = ChatMessageDto.builder()
                .role("assistant")
                .thinking(content)
                .build();
        return chunk(model, message);
    }

    private ChatResponseDto contentChunk(String model, String content) {
        ChatMessageDto message = ChatMessageDto.builder()
                .role("assistant")
                .content(content)
                .build();
        return chunk(model, message);
    }

    private ChatResponseDto doneChunk(String model) {
        return ChatResponseDto.builder()
                .model(model)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC).toString())
                .done(true)
                .build();
    }

    private ChatResponseDto chunk(String model, ChatMessageDto message) {
        return ChatResponseDto.builder()
                .model(model)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC).toString())
                .message(message)
                .done(false)
                .build();
    }
}
