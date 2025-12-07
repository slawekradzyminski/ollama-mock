package com.awesome.testing.ollama.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.awesome.testing.ollama.config.OllamaMockProperties;
import com.awesome.testing.ollama.dto.ChatMessageDto;
import com.awesome.testing.ollama.dto.ChatRequestDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class ChatServiceTest {

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(
                Mockito.mock(ChatClient.class),
                new ObjectMapper(),
                new OllamaMockProperties());
    }

    @Test
    void shouldRequestToolCallWhenCatalogQuestionDetected() {
        ChatRequestDto request = ChatRequestDto.builder()
                .messages(List.of(ChatMessageDto.builder()
                        .role("user")
                        .content("What iPhones do we have available?")
                        .build()))
                .tools(List.of())
                .build();

        // Add dummy tool definition to trigger detection
        request.setTools(List.of(new com.awesome.testing.ollama.dto.OllamaToolDefinitionDto()));

        StepVerifier.create(chatService.chatStream(request))
                .assertNext(resp -> {
                    assertThat(resp.getMessage().getToolCalls()).isNotEmpty();
                    assertThat(resp.getMessage().getToolCalls().get(0).getFunction().getName())
                            .isEqualTo("list_products");
                })
                .assertNext(resp -> assertThat(resp.isDone()).isTrue())
                .verifyComplete();
    }

    @Test
    void shouldSummarizeToolPayloadWhenToolMessageProvided() {
        ChatMessageDto toolMessage = ChatMessageDto.builder()
                .role("tool")
                .toolName("list_products")
                .content("{\"products\":[{\"name\":\"iPhone 13 Pro\"},{\"name\":\"Pixel 8\"}]}")
                .build();

        ChatRequestDto request = ChatRequestDto.builder()
                .messages(List.of(toolMessage))
                .build();

        StepVerifier.create(chatService.chatStream(request))
                .assertNext(resp -> assertThat(resp.getMessage().getContent())
                        .contains("iPhone 13 Pro")
                        .contains("Pixel 8"))
                .assertNext(resp -> assertThat(resp.isDone()).isTrue())
                .verifyComplete();
    }

    @Test
    void shouldProvideSimpleMockResponseWhenNoTools() {
        ChatRequestDto request = ChatRequestDto.builder()
                .messages(List.of(ChatMessageDto.builder()
                        .role("user")
                        .content("Hello mock")
                        .build()))
                .build();

        StepVerifier.create(chatService.chatStream(request))
                .assertNext(resp -> assertThat(resp.getMessage().getContent()).contains("Hello mock"))
                .assertNext(resp -> assertThat(resp.isDone()).isTrue())
                .verifyComplete();
    }
}
