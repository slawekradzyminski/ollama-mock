package com.awesome.testing.ollama.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.awesome.testing.ollama.config.OllamaMockProperties;
import com.awesome.testing.ollama.dto.GenerateResponseDto;
import com.awesome.testing.ollama.dto.StreamedRequestDto;
import com.awesome.testing.ollama.scenario.generate.GenerateScenarioRepository;
import tools.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class GenerateServiceTest {

    private static final String DEFAULT_MODEL = "qwen3.5:2b";

    private GenerateService generateService;

    @BeforeEach
    void setUp() {
        OllamaMockProperties properties = new OllamaMockProperties();
        properties.setDefaultModel(DEFAULT_MODEL);
        properties.setTokenDelay(Duration.ZERO);
        generateService = new GenerateService(
                properties,
                new GenerateScenarioRepository(new ObjectMapper()));
    }

    @Test
    void shouldStreamScenarioChunksWhenThinkingEnabled() {
        StreamedRequestDto request = StreamedRequestDto.builder()
                .prompt("Summarize the release plan")
                .think(true)
                .build();

        StepVerifier.create(generateService.generateStream(request).collectList())
                .assertNext(chunks -> {
                    assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.getModel()).isEqualTo(DEFAULT_MODEL));
                    String thinking = chunks.stream()
                            .map(GenerateResponseDto::getThinking)
                            .filter(thought -> thought != null)
                            .collect(Collectors.joining());
                    String content = chunks.stream()
                            .map(GenerateResponseDto::getResponse)
                            .filter(resp -> resp != null)
                            .collect(Collectors.joining());
                    assertThat(thinking).contains("qwen3.5:2b release checklist");
                    assertThat(content).contains("qwen3.5:2b is the default mock model");
                    assertThat(chunks.get(chunks.size() - 1).isDone()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnFallbackForUnsupportedPrompt() {
        StreamedRequestDto request = StreamedRequestDto.builder()
                .prompt("Unknown prompt")
                .build();

        StepVerifier.create(generateService.generateStream(request).collectList())
                .assertNext(chunks -> {
                    String response = chunks.stream()
                            .map(GenerateResponseDto::getResponse)
                            .filter(resp -> resp != null)
                            .collect(Collectors.joining());
                    assertThat(response).contains("Sorry, only these prompts are supported");
                    assertThat(chunks.get(chunks.size() - 1).isDone()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    void shouldDelayNonTerminalGenerateChunksWithoutWaitingInRealTime() {
        OllamaMockProperties properties = new OllamaMockProperties();
        properties.setDefaultModel(DEFAULT_MODEL);
        properties.setTokenDelay(Duration.ofSeconds(1));
        GenerateService delayedService = new GenerateService(
                properties,
                new GenerateScenarioRepository(new ObjectMapper()));
        StreamedRequestDto request = StreamedRequestDto.builder()
                .prompt("Provide a motivational quote")
                .build();

        StepVerifier.withVirtualTime(() -> delayedService.generateStream(request).take(1))
                .expectSubscription()
                .expectNoEvent(Duration.ofMillis(999))
                .thenAwait(Duration.ofMillis(1))
                .assertNext(response -> assertThat(response.isDone()).isFalse())
                .verifyComplete();
    }

    @Test
    void shouldReturnSingleScenarioResponse() {
        StreamedRequestDto request = StreamedRequestDto.builder()
                .prompt("Provide a motivational quote")
                .build();

        StepVerifier.create(generateService.generateSingle(request))
                .assertNext(chunk -> {
                    assertThat(chunk.getModel()).isEqualTo(DEFAULT_MODEL);
                    assertThat(chunk.isDone()).isTrue();
                    assertThat(chunk.getResponse()).contains("Keep shipping mock services");
                })
                .verifyComplete();
    }

    @Test
    void shouldUseLastResponseChunkForNonStreamingGeneration() {
        StreamedRequestDto request = StreamedRequestDto.builder()
                .prompt("Walk me through the streaming demo for /api/generate")
                .build();

        StepVerifier.create(generateService.generateSingle(request))
                .assertNext(chunk -> assertThat(chunk.getResponse())
                        .isEqualTo("Grab a terminal, run curl with --no-buffer, and you will see the exact same cadence "
                                + "that shows up in the logs."))
                .verifyComplete();
    }

    @Test
    void shouldReturnSupportedPromptListForUnknownSingleRequest() {
        StreamedRequestDto request = StreamedRequestDto.builder()
                .model("custom-generate-model")
                .prompt("Unknown")
                .build();

        StepVerifier.create(generateService.generateSingle(request))
                .assertNext(response -> {
                    assertThat(response.getModel()).isEqualTo("custom-generate-model");
                    assertThat(response.isDone()).isTrue();
                    assertThat(response.getResponse()).contains(
                            "- Summarize the release plan",
                            "- Walk me through the streaming demo for /api/generate");
                })
                .verifyComplete();
    }

    @Test
    void shouldSkipThinkingChunksWhenDisabled() {
        StreamedRequestDto request = StreamedRequestDto.builder()
                .prompt("Summarize the release plan")
                .think(false)
                .build();

        StepVerifier.create(generateService.generateStream(request).collectList())
                .assertNext(chunks -> {
                    assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.getModel()).isEqualTo(DEFAULT_MODEL));
                    assertThat(chunks.stream()
                            .map(GenerateResponseDto::getThinking)
                            .allMatch(thought -> thought == null)).isTrue();
                    String response = chunks.stream()
                            .map(GenerateResponseDto::getResponse)
                            .filter(resp -> resp != null)
                            .collect(Collectors.joining());
                    assertThat(response).contains("qwen3.5:2b is the default mock model");
                    assertThat(chunks.get(chunks.size() - 1).isDone()).isTrue();
                })
                .verifyComplete();
    }
}
