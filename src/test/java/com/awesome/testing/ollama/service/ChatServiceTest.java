package com.awesome.testing.ollama.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.awesome.testing.ollama.config.OllamaMockProperties;
import com.awesome.testing.ollama.dto.ChatMessageDto;
import com.awesome.testing.ollama.dto.ChatRequestDto;
import com.awesome.testing.ollama.scenario.chatbasic.ChatDialogueScenarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class ChatServiceTest {

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        OllamaMockProperties properties = new OllamaMockProperties();
        properties.setTokenDelay(Duration.ZERO);
        chatService = new ChatService(
                properties,
                new ChatDialogueScenarioRepository(new ObjectMapper()));
    }

    @Test
    void shouldStreamDialogueScenarioWithThinking() {
        ChatRequestDto request = ChatRequestDto.builder()
                .messages(List.of(ChatMessageDto.builder()
                        .role("user")
                        .content("Give me a quick status update on the Ollama mock")
                        .build()))
                .think(true)
                .build();

        StepVerifier.create(chatService.chatStream(request).collectList())
                .assertNext(chunks -> {
                    String thinking = chunks.stream()
                            .filter(chunk -> chunk.getMessage() != null)
                            .map(chunk -> chunk.getMessage().getThinking())
                            .filter(content -> content != null)
                            .collect(Collectors.joining());
                    String content = chunks.stream()
                            .filter(chunk -> chunk.getMessage() != null)
                            .map(chunk -> chunk.getMessage().getContent())
                            .filter(msg -> msg != null)
                            .collect(Collectors.joining());
                    assertThat(thinking).contains("local mock server");
                    assertThat(content).contains("port 11434");
                    assertThat(chunks.get(chunks.size() - 1).isDone()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    void shouldRespondWithSupportedPromptList() {
        ChatRequestDto request = ChatRequestDto.builder()
                .messages(List.of(ChatMessageDto.builder()
                        .role("user")
                        .content("Random question")
                        .build()))
                .build();

        StepVerifier.create(chatService.chatStream(request).collectList())
                .assertNext(chunks -> {
                    String content = chunks.stream()
                            .filter(chunk -> chunk.getMessage() != null)
                            .map(chunk -> chunk.getMessage().getContent())
                            .filter(msg -> msg != null)
                            .collect(Collectors.joining());
                    assertThat(content).contains("Sorry, only these chat prompts are supported");
                    assertThat(chunks.get(chunks.size() - 1).isDone()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    void shouldSkipThinkingWhenFlagDisabled() {
        ChatRequestDto request = ChatRequestDto.builder()
                .messages(List.of(ChatMessageDto.builder()
                        .role("user")
                        .content("Give me a quick status update on the Ollama mock")
                        .build()))
                .think(false)
                .build();

        StepVerifier.create(chatService.chatStream(request).collectList())
                .assertNext(chunks -> {
                    assertThat(chunks.stream()
                            .filter(chunk -> chunk.getMessage() != null)
                            .map(chunk -> chunk.getMessage().getThinking())
                            .allMatch(thought -> thought == null)).isTrue();
                    String content = chunks.stream()
                            .filter(chunk -> chunk.getMessage() != null)
                            .map(chunk -> chunk.getMessage().getContent())
                            .filter(msg -> msg != null)
                            .collect(Collectors.joining());
                    assertThat(content).contains("port 11434");
                    assertThat(chunks.get(chunks.size() - 1).isDone()).isTrue();
                })
                .verifyComplete();
    }
}
