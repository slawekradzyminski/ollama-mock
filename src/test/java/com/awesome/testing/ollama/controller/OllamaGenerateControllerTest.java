package com.awesome.testing.ollama.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.awesome.testing.ollama.dto.GenerateResponseDto;
import com.awesome.testing.ollama.dto.StreamedRequestDto;
import com.awesome.testing.ollama.service.GenerateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@WebFluxTest(controllers = OllamaGenerateController.class)
class OllamaGenerateControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private GenerateService generateService;

    @Test
    void shouldStreamNdjsonWhenStreamFlagTrue() {
        GenerateResponseDto chunk = GenerateResponseDto.builder()
                .model("mock")
                .response("hi")
                .done(false)
                .build();
        given(generateService.generateStream(any())).willReturn(Flux.just(chunk));

        webTestClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(StreamedRequestDto.builder().model("mock").prompt("hello").build())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_NDJSON)
                .expectBodyList(GenerateResponseDto.class)
                .hasSize(1);
    }

    @Test
    void shouldReturnJsonWhenStreamFlagFalse() {
        GenerateResponseDto response = GenerateResponseDto.builder()
                .model("mock")
                .response("full")
                .done(true)
                .build();
        given(generateService.generateSingle(any())).willReturn(Mono.just(response));

        StreamedRequestDto body = StreamedRequestDto.builder()
                .model("mock")
                .prompt("Hello")
                .stream(false)
                .build();

        webTestClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.done").isEqualTo(true)
                .jsonPath("$.response").isEqualTo("full");
    }
}
