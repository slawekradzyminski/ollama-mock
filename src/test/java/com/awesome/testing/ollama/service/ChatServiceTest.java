package com.awesome.testing.ollama.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.awesome.testing.ollama.config.OllamaMockProperties;
import com.awesome.testing.ollama.dto.ChatMessageDto;
import com.awesome.testing.ollama.dto.ChatRequestDto;
import com.awesome.testing.ollama.scenario.chatbasic.ChatDialogueScenarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class ChatServiceTest {

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(
                new OllamaMockProperties(),
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

        StepVerifier.create(chatService.chatStream(request))
                .assertNext(chunk -> assertThat(chunk.getMessage().getThinking())
                        .contains("local mock server"))
                .assertNext(chunk -> assertThat(chunk.getMessage().getContent())
                        .contains("port 11434"))
                .assertNext(chunk -> assertThat(chunk.isDone()).isTrue())
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

        StepVerifier.create(chatService.chatStream(request))
                .assertNext(chunk -> assertThat(chunk.getMessage().getContent())
                        .contains("Sorry, only these chat prompts are supported"))
                .assertNext(chunk -> assertThat(chunk.isDone()).isTrue())
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

        StepVerifier.create(chatService.chatStream(request))
                .assertNext(chunk -> {
                    assertThat(chunk.getMessage().getThinking()).isNull();
                    assertThat(chunk.getMessage().getContent()).contains("port 11434");
                })
                .assertNext(chunk -> assertThat(chunk.isDone()).isTrue())
                .verifyComplete();
    }
}
