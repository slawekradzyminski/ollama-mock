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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.awesome.testing.ollama.util.TokenStreamUtils;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class ChatToolsService {

    private static final Logger log = LoggerFactory.getLogger(ChatToolsService.class);

    private final OllamaMockProperties properties;
    private final ChatScenarioRepository scenarioRepository;

    public Flux<ChatResponseDto> chatToolStream(ChatRequestDto request) {
        String model = resolveModel(request.getModel());
        Flux<ChatResponseDto> conversation = scenarioRepository.findScenarioForConversation(request.getMessages())
                .map(scenario -> streamStage(model, scenario, request))
                .orElseGet(() -> streamUnsupportedPrompt(model));
        return conversation.concatWithValues(doneChunk(model))
                .concatMap(this::applyAdaptiveDelay);
    }

    public Mono<ChatResponseDto> chatToolSingle(ChatRequestDto request) {
        String model = resolveModel(request.getModel());
        return scenarioRepository.findScenarioForConversation(request.getMessages())
                .map(scenario -> determineStage(scenario, request)
                        .map(stage -> resolveSingleStageChunk(model, stage))
                        .orElseGet(() -> unhandledStageChunk(model, scenario.getPrompt())))
                .map(Mono::just)
                .orElseGet(() -> Mono.just(unsupportedPromptChunk(model)));
    }

    private String resolveModel(String requestedModel) {
        if (StringUtils.hasText(requestedModel)) {
            return requestedModel;
        }
        return properties.getDefaultModel();
    }

    private Flux<ChatResponseDto> streamStage(String model,
                                              ChatScenarioDefinition scenario,
                                              ChatRequestDto request) {
        Optional<ChatScenarioStageDefinition> stage = determineStage(scenario, request);
        if (stage.isEmpty()) {
            return streamContentTokens(model,
                    "This step for prompt \"%s\" is not configured yet. Please restart the conversation."
                            .formatted(scenario.getPrompt()));
        }
        if (stage.get().getToolCall() != null) {
            log.info("[chat-tools][call] prompt='{}' issuing {}", scenario.getPrompt(), stage.get().getToolCall().getName());
            return Flux.just(toolCallChunk(model, stage.get()));
        }
        if (StringUtils.hasText(stage.get().getResponse())) {
            return streamContentTokens(model, stage.get().getResponse());
        }
        return Flux.empty();
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

    private Flux<ChatResponseDto> streamUnsupportedPrompt(String model) {
        return streamContentTokens(model, formatSupportedPromptMessage("Sorry, only these chat tool prompts are supported:"));
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

    private Flux<ChatResponseDto> streamContentTokens(String model, String text) {
        List<String> tokens = TokenStreamUtils.tokenize(text);
        return Flux.fromIterable(tokens)
                .doOnSubscribe(sub -> log.info("[chat-tools][content] {} token(s) queued", tokens.size()))
                .doOnNext(token -> log.info("[chat-tools][content-token] {}", TokenStreamUtils.printable(token)))
                .map(token -> contentChunk(model, token));
    }

    private Mono<ChatResponseDto> applyAdaptiveDelay(ChatResponseDto chunk) {
        Duration delay;
        if (chunk.isDone()) {
            delay = Duration.ZERO;
        } else if (chunk.getMessage() != null && chunk.getMessage().getToolCalls() != null
                && !chunk.getMessage().getToolCalls().isEmpty()) {
            delay = properties.getToolCallDelay();
        } else {
            delay = properties.getTokenDelay();
        }
        if (delay.isZero() || delay.isNegative()) {
            return Mono.just(chunk);
        }
        return Mono.just(chunk).delayElement(delay);
    }

    private ChatResponseDto resolveSingleStageChunk(String model, ChatScenarioStageDefinition stage) {
        if (stage.getToolCall() != null) {
            return toolCallChunk(model, stage);
        }
        if (StringUtils.hasText(stage.getResponse())) {
            return contentChunk(model, stage.getResponse());
        }
        return contentChunk(model, "(no response configured for this stage)");
    }
}
