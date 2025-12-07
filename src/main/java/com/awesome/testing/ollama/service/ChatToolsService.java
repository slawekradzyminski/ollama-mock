package com.awesome.testing.ollama.service;

import com.awesome.testing.ollama.config.OllamaMockProperties;
import com.awesome.testing.ollama.dto.ChatMessageDto;
import com.awesome.testing.ollama.dto.ChatRequestDto;
import com.awesome.testing.ollama.dto.ChatResponseDto;
import com.awesome.testing.ollama.dto.ToolCallDto;
import com.awesome.testing.ollama.dto.ToolCallFunctionDto;
import com.awesome.testing.ollama.scenario.chat.ChatScenarioDefinition;
import com.awesome.testing.ollama.scenario.chat.ChatScenarioRepository;
import com.awesome.testing.ollama.scenario.chat.ChatScenarioStageDefinition;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ChatToolsService {

    private static final Duration STREAM_DELAY = Duration.ofMillis(150);

    private final OllamaMockProperties properties;
    private final ChatScenarioRepository scenarioRepository;

    public Flux<ChatResponseDto> chatToolStream(ChatRequestDto request) {
        String model = resolveModel(request.getModel());
        Flux<ChatResponseDto> convo = scenarioRepository.findScenarioForConversation(request.getMessages())
                .map(scenario -> Flux.fromIterable(buildStageChunks(model, scenario, request)))
                .orElseGet(() -> Flux.just(unsupportedPromptChunk(model)));
        return convo.concatWithValues(doneChunk(model))
                .delayElements(STREAM_DELAY);
    }

    public Mono<ChatResponseDto> chatToolSingle(ChatRequestDto request) {
        return chatToolStream(request)
                .filter(resp -> resp.getMessage() != null && !resp.isDone())
                .last();
    }

    private String resolveModel(String requestedModel) {
        if (StringUtils.hasText(requestedModel)) {
            return requestedModel;
        }
        return properties.getDefaultModel();
    }

    private List<ChatResponseDto> buildStageChunks(String model,
                                                   ChatScenarioDefinition scenario,
                                                   ChatRequestDto request) {
        Optional<ChatScenarioStageDefinition> stage = determineStage(scenario, request);
        if (stage.isEmpty()) {
            return List.of(unhandledStageChunk(model, scenario.getPrompt()));
        }
        List<ChatResponseDto> outputs = new ArrayList<>();
        if (stage.get().getToolCall() != null) {
            outputs.add(toolCallChunk(model, stage.get()));
        } else if (StringUtils.hasText(stage.get().getResponse())) {
            outputs.add(contentChunk(model, stage.get().getResponse()));
        }
        return outputs;
    }

    private Optional<ChatScenarioStageDefinition> determineStage(ChatScenarioDefinition scenario,
                                                                 ChatRequestDto request) {
        ChatMessageDto latest = latestMessage(request);
        if (latest == null) {
            return Optional.empty();
        }
        if ("tool".equalsIgnoreCase(latest.getRole())) {
            return scenario.stageForTool(latest.getToolName());
        }
        if ("user".equalsIgnoreCase(latest.getRole())) {
            return scenario.stageForUserPrompt();
        }
        return Optional.empty();
    }

    private ChatMessageDto latestMessage(ChatRequestDto request) {
        List<ChatMessageDto> messages = Optional.ofNullable(request.getMessages()).orElse(List.of());
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessageDto message = messages.get(i);
            if (StringUtils.hasText(message.getRole())) {
                return message;
            }
        }
        return null;
    }

    private ChatResponseDto unsupportedPromptChunk(String model) {
        String content = formatSupportedPromptMessage("Sorry, only these chat tool prompts are supported:");
        return contentChunk(model, content);
    }

    private ChatResponseDto unhandledStageChunk(String model, String prompt) {
        String content = "This step for prompt \"%s\" is not configured yet. Please restart the conversation."
                .formatted(prompt);
        return contentChunk(model, content);
    }

    private String formatSupportedPromptMessage(String prefix) {
        List<String> prompts = scenarioRepository.supportedPrompts();
        if (prompts.isEmpty()) {
            return prefix + " (no tool prompts configured)";
        }
        String joined = prompts.stream()
                .map(name -> "- " + name)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        return prefix + "\n" + joined;
    }

    private ChatResponseDto toolCallChunk(String model, ChatScenarioStageDefinition stage) {
        ToolCallDto toolCall = ToolCallDto.builder()
                .id("toolcall-" + UUID.randomUUID())
                .function(ToolCallFunctionDto.builder()
                        .name(stage.getToolCall().getName())
                        .arguments(stage.getToolCall().getArguments())
                        .build())
                .build();

        ChatMessageDto message = ChatMessageDto.builder()
                .role("assistant")
                .toolCalls(List.of(toolCall))
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
