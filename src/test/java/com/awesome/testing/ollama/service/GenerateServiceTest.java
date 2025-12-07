package com.awesome.testing.ollama.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.awesome.testing.ollama.config.OllamaMockProperties;
import com.awesome.testing.ollama.dto.GenerateResponseDto;
import com.awesome.testing.ollama.dto.StreamedRequestDto;
import com.awesome.testing.ollama.scenario.generate.GenerateScenarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class GenerateServiceTest {

    private GenerateService generateService;

    @BeforeEach
    void setUp() {
        OllamaMockProperties properties = new OllamaMockProperties();
        properties.setDefaultModel("default-model");
        properties.setTokenDelay(Duration.ZERO);
        generateService = new GenerateService(
                properties,
                new GenerateScenarioRepository(new ObjectMapper()));
    }

    @Test
    void shouldStreamScenarioChunksWhenThinkingEnabled() {
        StreamedRequestDto request = StreamedRequestDto.builder()
                .model("default-model")
                .prompt("Summarize the release plan")
                .think(true)
                .build();

        StepVerifier.create(generateService.generateStream(request).collectList())
                .assertNext(chunks -> {
                    String thinking = chunks.stream()
                            .map(GenerateResponseDto::getThinking)
                            .filter(thought -> thought != null)
                            .collect(Collectors.joining());
                    String content = chunks.stream()
                            .map(GenerateResponseDto::getResponse)
                            .filter(resp -> resp != null)
                            .collect(Collectors.joining());
                    assertThat(thinking).contains("release checklist");
                    assertThat(content).contains("mock Ollama service");
                    assertThat(chunks.get(chunks.size() - 1).isDone()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnFallbackForUnsupportedPrompt() {
        StreamedRequestDto request = StreamedRequestDto.builder()
                .model("default-model")
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
    void shouldReturnSingleScenarioResponse() {
        StreamedRequestDto request = StreamedRequestDto.builder()
                .model("default-model")
                .prompt("Provide a motivational quote")
                .build();

        StepVerifier.create(generateService.generateSingle(request))
                .assertNext(chunk -> {
                    assertThat(chunk.isDone()).isTrue();
                    assertThat(chunk.getResponse()).contains("Keep shipping mock services");
                })
                .verifyComplete();
    }

    @Test
    void shouldSkipThinkingChunksWhenDisabled() {
        StreamedRequestDto request = StreamedRequestDto.builder()
                .model("default-model")
                .prompt("Summarize the release plan")
                .think(false)
                .build();

        StepVerifier.create(generateService.generateStream(request).collectList())
                .assertNext(chunks -> {
                    assertThat(chunks.stream()
                            .map(GenerateResponseDto::getThinking)
                            .allMatch(thought -> thought == null)).isTrue();
                    String response = chunks.stream()
                            .map(GenerateResponseDto::getResponse)
                            .filter(resp -> resp != null)
                            .collect(Collectors.joining());
                    assertThat(response).contains("mock Ollama service");
                    assertThat(chunks.get(chunks.size() - 1).isDone()).isTrue();
                })
                .verifyComplete();
    }
}
