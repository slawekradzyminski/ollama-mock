package com.awesome.testing.ollama.service;

import com.awesome.testing.ollama.config.OllamaMockProperties;
import com.awesome.testing.ollama.dto.ChatMessageDto;
import com.awesome.testing.ollama.dto.ChatRequestDto;
import com.awesome.testing.ollama.dto.ChatResponseDto;
import com.awesome.testing.ollama.scenario.chatbasic.ChatDialogueScenarioDefinition;
import com.awesome.testing.ollama.scenario.chatbasic.ChatDialogueScenarioRepository;
import com.awesome.testing.ollama.util.TokenStreamUtils;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final OllamaMockProperties properties;
    private final ChatDialogueScenarioRepository scenarioRepository;

    public Flux<ChatResponseDto> chatStream(ChatRequestDto request) {
        String model = resolveModel(request.getModel());
        boolean thinkingEnabled = Boolean.TRUE.equals(request.getThink());
        Flux<ChatResponseDto> conversation = scenarioRepository.findScenario(request.getMessages())
                .map(scenario -> streamScenario(model, scenario, thinkingEnabled))
                .orElseGet(() -> streamUnsupportedPrompt(model));
        return conversation.concatWithValues(doneChunk(model))
                .concatMap(this::applyTokenDelay);
    }

    public Mono<ChatResponseDto> chatSingle(ChatRequestDto request) {
        String model = resolveModel(request.getModel());
        boolean thinkingEnabled = Boolean.TRUE.equals(request.getThink());
        return scenarioRepository.findScenario(request.getMessages())
                .map(scenario -> Mono.just(aggregateScenario(model, scenario, thinkingEnabled)))
                .orElseGet(() -> Mono.just(unsupportedPrompt(model)));
    }

    private String resolveModel(String requestedModel) {
        if (StringUtils.hasText(requestedModel)) {
            return requestedModel;
        }
        return properties.getDefaultModel();
    }

    private Flux<ChatResponseDto> streamScenario(String model,
                                                 ChatDialogueScenarioDefinition scenario,
                                                 boolean thinkingEnabled) {
        log.info("[chat-stream] prompt='{}' think={} model={}", scenario.getPrompt(), thinkingEnabled, model);
        return Flux.fromIterable(scenario.getChunks())
                .concatMap(chunk -> Flux.concat(
                        thinkingEnabled && StringUtils.hasText(chunk.getThinking())
                                ? streamThinkingTokens(model, chunk.getThinking())
                                : Flux.empty(),
                        StringUtils.hasText(chunk.getResponse())
                                ? streamContentTokens(model, chunk.getResponse())
                                : Flux.empty()
                ));
    }

    private Flux<ChatResponseDto> streamUnsupportedPrompt(String model) {
        return streamContentTokens(model, formatSupportedPromptMessage("Sorry, only these chat prompts are supported:"));
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

    private Flux<ChatResponseDto> streamThinkingTokens(String model, String text) {
        List<String> tokens = TokenStreamUtils.tokenize(text);
        return Flux.fromIterable(tokens)
                .doOnSubscribe(sub -> log.info("[chat-stream][thinking] {} token(s) queued", tokens.size()))
                .doOnNext(token -> log.info("[chat-stream][thinking-token] {}", TokenStreamUtils.printable(token)))
                .map(token -> thinkingChunk(model, token));
    }

    private Flux<ChatResponseDto> streamContentTokens(String model, String text) {
        List<String> tokens = TokenStreamUtils.tokenize(text);
        return Flux.fromIterable(tokens)
                .doOnSubscribe(sub -> log.info("[chat-stream][content] {} token(s) queued", tokens.size()))
                .doOnNext(token -> log.info("[chat-stream][content-token] {}", TokenStreamUtils.printable(token)))
                .map(token -> contentChunk(model, token));
    }

    private Mono<ChatResponseDto> applyTokenDelay(ChatResponseDto chunk) {
        Duration delay = chunk.isDone() ? Duration.ZERO : properties.getTokenDelay();
        if (delay.isZero() || delay.isNegative()) {
            return Mono.just(chunk);
        }
        return Mono.just(chunk).delayElement(delay);
    }

    private ChatResponseDto aggregateScenario(String model,
                                              ChatDialogueScenarioDefinition scenario,
                                              boolean thinkingEnabled) {
        StringBuilder thinking = new StringBuilder();
        StringBuilder content = new StringBuilder();
        scenario.getChunks().forEach(chunk -> {
            if (thinkingEnabled && StringUtils.hasText(chunk.getThinking())) {
                appendSection(thinking, chunk.getThinking());
            }
            if (StringUtils.hasText(chunk.getResponse())) {
                appendSection(content, chunk.getResponse());
            }
        });
        ChatMessageDto.ChatMessageDtoBuilder builder = ChatMessageDto.builder()
                .role("assistant");
        if (thinkingEnabled && thinking.length() > 0) {
            builder.thinking(thinking.toString());
        }
        if (content.length() > 0) {
            builder.content(content.toString());
        }
        return chunk(model, builder.build());
    }

    private void appendSection(StringBuilder builder, String addition) {
        if (builder.length() > 0) {
            builder.append("\n\n");
        }
        builder.append(addition);
    }
}
