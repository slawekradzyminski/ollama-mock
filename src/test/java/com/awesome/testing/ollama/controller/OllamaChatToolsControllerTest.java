package com.awesome.testing.ollama.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.awesome.testing.ollama.dto.ChatMessageDto;
import com.awesome.testing.ollama.dto.ChatRequestDto;
import com.awesome.testing.ollama.dto.ChatResponseDto;
import com.awesome.testing.ollama.service.ChatToolsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@WebFluxTest(controllers = OllamaChatToolsController.class)
class OllamaChatToolsControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ChatToolsService chatToolsService;

    @Test
    void shouldStreamToolResponsesWithApiPath() {
        ChatResponseDto chunk = ChatResponseDto.builder()
                .model("mock")
                .message(new ChatMessageDto())
                .done(false)
                .build();
        given(chatToolsService.chatToolStream(any())).willReturn(Flux.just(chunk));

        webTestClient.post()
                .uri("/api/chat/tools")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ChatRequestDto.builder().messages(java.util.List.of()).build())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_NDJSON);
    }

    @Test
    void shouldStreamToolResponsesWithLegacyPath() {
        ChatResponseDto chunk = ChatResponseDto.builder()
                .model("mock")
                .message(new ChatMessageDto())
                .done(false)
                .build();
        given(chatToolsService.chatToolStream(any())).willReturn(Flux.just(chunk));

        webTestClient.post()
                .uri("/chat/tools")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ChatRequestDto.builder().messages(java.util.List.of()).build())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_NDJSON);
    }

    @Test
    void shouldReturnSingleToolChunk() {
        ChatResponseDto response = ChatResponseDto.builder()
                .model("mock")
                .message(new ChatMessageDto())
                .done(true)
                .build();
        given(chatToolsService.chatToolSingle(any())).willReturn(Mono.just(response));

        ChatRequestDto request = ChatRequestDto.builder()
                .messages(java.util.List.of())
                .stream(false)
                .build();

        webTestClient.post()
                .uri("/api/chat/tools")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.done").isEqualTo(true);
    }
}
