package com.awesome.testing.ollama.controller;

import com.awesome.testing.ollama.dto.ChatMessageDto;
import com.awesome.testing.ollama.dto.ChatRequestDto;
import com.awesome.testing.ollama.dto.ChatResponseDto;
import com.awesome.testing.ollama.dto.OllamaToolDefinitionDto;
import com.awesome.testing.ollama.service.ChatService;
import com.awesome.testing.ollama.service.ChatToolsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@WebFluxTest(controllers = OllamaChatController.class)
class OllamaChatControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ChatService chatService;
    @MockitoBean
    private ChatToolsService chatToolsService;

    @Test
    void shouldStreamChatResponses() {
        ChatResponseDto chunk = ChatResponseDto.builder()
                .model("mock")
                .message(new ChatMessageDto())
                .done(false)
                .build();
        given(chatService.chatStream(any())).willReturn(Flux.just(chunk));

        webTestClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ChatRequestDto.builder().messages(List.of()).build())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_NDJSON);
    }

    @Test
    void shouldStreamToolResponsesWhenToolsPresent() {
        ChatResponseDto chunk = ChatResponseDto.builder()
                .model("mock")
                .message(new ChatMessageDto())
                .done(false)
                .build();
        given(chatToolsService.chatToolStream(any())).willReturn(Flux.just(chunk));

        ChatRequestDto request = ChatRequestDto.builder()
                .tools(List.of(new OllamaToolDefinitionDto()))
                .build();

        webTestClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_NDJSON);
    }

    @Test
    void shouldReturnSingleChunkWhenStreamDisabled() {
        ChatResponseDto response = ChatResponseDto.builder()
                .model("mock")
                .message(new ChatMessageDto())
                .done(true)
                .build();
        given(chatService.chatSingle(any())).willReturn(Mono.just(response));

        ChatRequestDto request = ChatRequestDto.builder()
                .messages(List.of())
                .stream(false)
                .build();

        webTestClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.done").isEqualTo(true);
    }

    @Test
    void shouldReturnToolChunkWhenStreamDisabled() {
        ChatResponseDto response = ChatResponseDto.builder()
                .model("mock")
                .message(new ChatMessageDto())
                .done(true)
                .build();
        given(chatToolsService.chatToolSingle(any())).willReturn(Mono.just(response));

        ChatRequestDto request = ChatRequestDto.builder()
                .messages(List.of())
                .stream(false)
                .tools(List.of(new OllamaToolDefinitionDto()))
                .build();

        webTestClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.done").isEqualTo(true);
    }
}
