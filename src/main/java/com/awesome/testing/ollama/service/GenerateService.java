package com.awesome.testing.ollama.service;

import com.awesome.testing.ollama.config.OllamaMockProperties;
import com.awesome.testing.ollama.dto.GenerateResponseDto;
import com.awesome.testing.ollama.dto.StreamedRequestDto;
import com.awesome.testing.ollama.scenario.generate.GenerateScenarioDefinition;
import com.awesome.testing.ollama.scenario.generate.GenerateScenarioRepository;
import com.awesome.testing.ollama.util.TokenStreamUtils;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
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
public class GenerateService {

    private static final Logger log = LoggerFactory.getLogger(GenerateService.class);

    private final OllamaMockProperties properties;
    private final GenerateScenarioRepository scenarioRepository;

    public Flux<GenerateResponseDto> generateStream(StreamedRequestDto request) {
        String model = resolveModel(request.getModel());
        boolean thinkingEnabled = Boolean.TRUE.equals(request.getThink());
        Flux<GenerateResponseDto> stream = scenarioRepository.findByPrompt(request.getPrompt())
                .map(scenario -> streamScenario(model, scenario, thinkingEnabled))
                .orElseGet(() -> streamUnsupportedPrompt(model));
        return stream.concatWithValues(doneChunk(model))
                .concatMap(this::applyTokenDelay);
    }

    public Mono<GenerateResponseDto> generateSingle(StreamedRequestDto request) {
        String model = resolveModel(request.getModel());
        return scenarioRepository.findByPrompt(request.getPrompt())
                .map(scenario -> Mono.just(selectSingleChunk(model, scenario)))
                .orElseGet(() -> Mono.just(unsupportedPromptChunk(model, true)));
    }

    private String resolveModel(String requestedModel) {
        if (StringUtils.hasText(requestedModel)) {
            return requestedModel;
        }
        return properties.getDefaultModel();
    }

    private Flux<GenerateResponseDto> streamScenario(String model,
                                                     GenerateScenarioDefinition scenario,
                                                     boolean thinkingEnabled) {
        log.info("[generate-stream] prompt='{}' think={} model={}", scenario.getPrompt(), thinkingEnabled, model);
        return Flux.fromIterable(scenario.getChunks())
                .concatMap(chunk -> Flux.concat(
                        thinkingEnabled && StringUtils.hasText(chunk.getThinking())
                                ? streamThinkingTokens(model, chunk.getThinking())
                                : Flux.empty(),
                        StringUtils.hasText(chunk.getResponse())
                                ? streamResponseTokens(model, chunk.getResponse())
                                : Flux.empty()
                ));
    }

    private GenerateResponseDto selectSingleChunk(String model, GenerateScenarioDefinition scenario) {
        List<com.awesome.testing.ollama.scenario.generate.GenerateScenarioChunkDefinition> chunks =
                new ArrayList<>(scenario.getChunks());
        Collections.reverse(chunks);
        for (var chunk : chunks) {
            if (StringUtils.hasText(chunk.getResponse())) {
                return responseChunk(model, chunk.getResponse(), true);
            }
        }
        return responseChunk(
                model,
                "This scenario does not have a final response configured yet.",
                true);
    }

    private GenerateResponseDto unsupportedPromptChunk(String model, boolean done) {
        String message = formatSupportedPromptMessage("Sorry, only these prompts are supported for this endpoint:");
        return responseChunk(model, message, done);
    }

    private Flux<GenerateResponseDto> streamUnsupportedPrompt(String model) {
        return streamResponseTokens(model,
                formatSupportedPromptMessage("Sorry, only these prompts are supported for this endpoint:"));
    }

    private String formatSupportedPromptMessage(String prefix) {
        List<String> prompts = scenarioRepository.supportedPrompts();
        if (prompts.isEmpty()) {
            return prefix + " (no scenarios configured)";
        }
        String joined = prompts.stream()
                .map(entry -> "- " + entry)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        return prefix + "\n" + joined;
    }

    private GenerateResponseDto thinkingChunk(String model, String thought) {
        return GenerateResponseDto.builder()
                .model(model)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC).toString())
                .thinking(thought)
                .done(false)
                .build();
    }

    private GenerateResponseDto responseChunk(String model, String content, boolean done) {
        return GenerateResponseDto.builder()
                .model(model)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC).toString())
                .response(content)
                .done(done)
                .build();
    }

    private GenerateResponseDto doneChunk(String model) {
        return GenerateResponseDto.builder()
                .model(model)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC).toString())
                .done(true)
                .build();
    }

    private Flux<GenerateResponseDto> streamThinkingTokens(String model, String text) {
        List<String> tokens = TokenStreamUtils.tokenize(text);
        return Flux.fromIterable(tokens)
                .doOnSubscribe(sub -> log.info("[generate-stream][thinking] {} token(s) queued", tokens.size()))
                .doOnNext(token -> log.info("[generate-stream][thinking-token] {}", TokenStreamUtils.printable(token)))
                .map(token -> thinkingChunk(model, token));
    }

    private Flux<GenerateResponseDto> streamResponseTokens(String model, String text) {
        List<String> tokens = TokenStreamUtils.tokenize(text);
        return Flux.fromIterable(tokens)
                .doOnSubscribe(sub -> log.info("[generate-stream][content] {} token(s) queued", tokens.size()))
                .doOnNext(token -> log.info("[generate-stream][content-token] {}", TokenStreamUtils.printable(token)))
                .map(token -> responseChunk(model, token, false));
    }

    private Mono<GenerateResponseDto> applyTokenDelay(GenerateResponseDto chunk) {
        Duration delay = chunk.isDone() ? Duration.ZERO : properties.getTokenDelay();
        if (delay.isZero() || delay.isNegative()) {
            return Mono.just(chunk);
        }
        return Mono.just(chunk).delayElement(delay);
    }
}
