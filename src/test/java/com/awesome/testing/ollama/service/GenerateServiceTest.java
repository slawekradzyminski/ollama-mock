package com.awesome.testing.ollama.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.awesome.testing.ollama.config.OllamaMockProperties;
import com.awesome.testing.ollama.dto.GenerateResponseDto;
import com.awesome.testing.ollama.dto.StreamedRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class GenerateServiceTest {

    @Mock
    private ChatClient chatClient;
    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;
    @Mock
    private ChatClient.StreamResponseSpec streamResponseSpec;
    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    private GenerateService generateService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        OllamaMockProperties properties = new OllamaMockProperties();
        properties.setDefaultModel("default-model");
        generateService = new GenerateService(chatClient, properties);

        given(chatClient.prompt()).willReturn(requestSpec);
        given(requestSpec.user(anyString())).willReturn(requestSpec);
        given(requestSpec.stream()).willReturn(streamResponseSpec);
        given(requestSpec.call()).willReturn(callResponseSpec);
    }

    @Test
    void shouldEmitThinkingAndCompletionChunksWhenStreaming() {
        given(streamResponseSpec.content()).willReturn(Flux.just("hello", "world"));

        StreamedRequestDto request = StreamedRequestDto.builder()
                .prompt("Hi!")
                .think(true)
                .build();

        StepVerifier.create(generateService.generateStream(request))
                .assertNext(chunk -> assertThat(chunk.getThinking()).isNotBlank())
                .assertNext(chunk -> assertThat(chunk.getResponse()).isEqualTo("hello"))
                .assertNext(chunk -> assertThat(chunk.getResponse()).isEqualTo("world"))
                .assertNext(chunk -> assertThat(chunk.isDone()).isTrue())
                .verifyComplete();
    }

    @Test
    void shouldReturnSingleChunkWhenStreamDisabled() {
        given(callResponseSpec.content()).willReturn("final answer");

        StreamedRequestDto request = StreamedRequestDto.builder()
                .prompt("Explain mock server")
                .stream(false)
                .build();

        StepVerifier.create(generateService.generateSingle(request))
                .assertNext(chunk -> {
                    assertThat(chunk.isDone()).isTrue();
                    assertThat(chunk.getResponse()).isEqualTo("final answer");
                    assertThat(chunk.getTotalDuration()).isNotNull();
                })
                .verifyComplete();
    }
}
