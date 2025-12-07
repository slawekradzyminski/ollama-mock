package com.awesome.testing.ollama.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.awesome.testing.ollama.config.OllamaMockProperties;
import com.awesome.testing.ollama.dto.StreamedRequestDto;
import com.awesome.testing.ollama.scenario.generate.GenerateScenarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class GenerateServiceTest {

    private GenerateService generateService;

    @BeforeEach
    void setUp() {
        OllamaMockProperties properties = new OllamaMockProperties();
        properties.setDefaultModel("default-model");
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

        StepVerifier.create(generateService.generateStream(request))
                .assertNext(chunk -> assertThat(chunk.getThinking()).contains("release checklist"))
                .assertNext(chunk -> assertThat(chunk.getResponse()).contains("mock Ollama service"))
                .assertNext(chunk -> assertThat(chunk.isDone()).isTrue())
                .verifyComplete();
    }

    @Test
    void shouldReturnFallbackForUnsupportedPrompt() {
        StreamedRequestDto request = StreamedRequestDto.builder()
                .model("default-model")
                .prompt("Unknown prompt")
                .build();

        StepVerifier.create(generateService.generateStream(request))
                .assertNext(chunk -> assertThat(chunk.getResponse())
                        .contains("Sorry, only these prompts are supported"))
                .assertNext(chunk -> assertThat(chunk.isDone()).isTrue())
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

        StepVerifier.create(generateService.generateStream(request))
                .assertNext(chunk -> {
                    assertThat(chunk.getThinking()).isNull();
                    assertThat(chunk.getResponse()).contains("mock Ollama service");
                })
                .assertNext(chunk -> assertThat(chunk.isDone()).isTrue())
                .verifyComplete();
    }
}
