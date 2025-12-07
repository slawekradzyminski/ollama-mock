package com.awesome.testing.ollama.service;

import com.awesome.testing.ollama.config.OllamaMockProperties;
import com.awesome.testing.ollama.dto.GenerateResponseDto;
import com.awesome.testing.ollama.dto.StreamedRequestDto;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenerateService {

    private final ChatClient chatClient;
    private final OllamaMockProperties properties;

    public Flux<GenerateResponseDto> generateStream(StreamedRequestDto request) {
        String model = resolveModel(request.getModel());
        Instant start = Instant.now();
        AtomicLong chunkCounter = new AtomicLong();

        Flux<GenerateResponseDto> thinkingFlux = Boolean.TRUE.equals(request.getThink())
                ? Flux.just(buildThinkingChunk(model, OffsetDateTime.ofInstant(start, ZoneOffset.UTC)))
                : Flux.empty();

        Flux<GenerateResponseDto> responseFlux = chatClient.prompt()
                .user(request.getPrompt())
                .stream()
                .content()
                .filter(StringUtils::hasText)
                .map(chunk -> buildChunk(
                        model,
                        OffsetDateTime.now(ZoneOffset.UTC),
                        chunk,
                        false,
                        null))
                .doOnNext(chunk -> chunkCounter.incrementAndGet());

        Mono<GenerateResponseDto> completion = Mono.fromSupplier(() -> {
            Instant end = Instant.now();
            return buildChunk(
                    model,
                    OffsetDateTime.ofInstant(end, ZoneOffset.UTC),
                    "",
                    true,
                    Duration.between(start, end).toNanos());
        });

        return Flux.concat(thinkingFlux, responseFlux, completion)
                .doOnComplete(() -> log.info("Generated {} chunk(s) for model {}", chunkCounter.get(), model));
    }

    public Mono<GenerateResponseDto> generateSingle(StreamedRequestDto request) {
        String model = resolveModel(request.getModel());
        Instant start = Instant.now();

        return Mono.fromSupplier(() -> chatClient.prompt()
                        .user(request.getPrompt())
                        .call()
                        .content())
                .map(content -> buildChunk(
                        model,
                        OffsetDateTime.now(ZoneOffset.UTC),
                        content,
                        true,
                        Duration.between(start, Instant.now()).toNanos()));
    }

    private String resolveModel(String requestedModel) {
        if (StringUtils.hasText(requestedModel)) {
            return requestedModel;
        }
        return properties.getDefaultModel();
    }

    private GenerateResponseDto buildThinkingChunk(String model, OffsetDateTime timestamp) {
        return GenerateResponseDto.builder()
                .model(model)
                .createdAt(timestamp.toString())
                .thinking("Analyzing your prompt before generating a response...")
                .done(false)
                .build();
    }

    private GenerateResponseDto buildChunk(String model,
                                           OffsetDateTime timestamp,
                                           String content,
                                           boolean done,
                                           Long totalDuration) {
        return GenerateResponseDto.builder()
                .model(model)
                .createdAt(timestamp.toString())
                .response(content)
                .done(done)
                .totalDuration(totalDuration)
                .build();
    }
}
