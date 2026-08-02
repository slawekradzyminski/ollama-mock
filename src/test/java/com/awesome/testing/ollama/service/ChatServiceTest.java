package com.awesome.testing.ollama.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.awesome.testing.ollama.config.OllamaMockProperties;
import com.awesome.testing.ollama.dto.ChatMessageDto;
import com.awesome.testing.ollama.dto.ChatRequestDto;
import com.awesome.testing.ollama.scenario.chatbasic.ChatDialogueScenarioRepository;
import tools.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class ChatServiceTest {

    private static final String DEFAULT_MODEL = "qwen3.5:2b";

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        OllamaMockProperties properties = new OllamaMockProperties();
        properties.setDefaultModel(DEFAULT_MODEL);
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
                    assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.getModel()).isEqualTo(DEFAULT_MODEL));
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
                    assertThat(thinking).contains("qwen3.5:2b default");
                    assertThat(content).contains("defaults to qwen3.5:2b");
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
                    assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.getModel()).isEqualTo(DEFAULT_MODEL));
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
                    assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.getModel()).isEqualTo(DEFAULT_MODEL));
                    assertThat(chunks.stream()
                            .filter(chunk -> chunk.getMessage() != null)
                            .map(chunk -> chunk.getMessage().getThinking())
                            .allMatch(thought -> thought == null)).isTrue();
                    String content = chunks.stream()
                            .filter(chunk -> chunk.getMessage() != null)
                            .map(chunk -> chunk.getMessage().getContent())
                            .filter(msg -> msg != null)
                            .collect(Collectors.joining());
                    assertThat(content).contains("defaults to qwen3.5:2b");
                    assertThat(chunks.get(chunks.size() - 1).isDone()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    void shouldAggregateAllDialogueSectionsForSingleResponse() {
        ChatRequestDto request = ChatRequestDto.builder()
                .model("custom-chat-model")
                .messages(List.of(ChatMessageDto.builder()
                        .role("user")
                        .content("Narrate the full streaming timeline for this mock")
                        .build()))
                .think(true)
                .build();

        StepVerifier.create(chatService.chatSingle(request))
                .assertNext(response -> {
                    assertThat(response.getModel()).isEqualTo("custom-chat-model");
                    assertThat(response.isDone()).isFalse();
                    assertThat(response.getMessage().getThinking()).contains("Collecting the internal notes");
                    assertThat(response.getMessage().getContent())
                            .contains("First you fire a request", "Then the thinking paragraph", "Finally the assistant text")
                            .contains("\n\n");
                })
                .verifyComplete();
    }

    @Test
    void shouldDelayNonTerminalChatChunksWithoutWaitingInRealTime() {
        OllamaMockProperties properties = new OllamaMockProperties();
        properties.setDefaultModel(DEFAULT_MODEL);
        properties.setTokenDelay(Duration.ofSeconds(1));
        ChatService delayedService = new ChatService(
                properties,
                new ChatDialogueScenarioRepository(new ObjectMapper()));
        ChatRequestDto request = ChatRequestDto.builder()
                .messages(List.of(ChatMessageDto.builder()
                        .role("user")
                        .content("Give me a quick status update on the Ollama mock")
                        .build()))
                .build();

        StepVerifier.withVirtualTime(() -> delayedService.chatStream(request).take(1))
                .expectSubscription()
                .expectNoEvent(Duration.ofMillis(999))
                .thenAwait(Duration.ofMillis(1))
                .assertNext(response -> assertThat(response.isDone()).isFalse())
                .verifyComplete();
    }

    @Test
    void shouldReturnCompleteSupportedPromptListForUnknownSingleRequest() {
        ChatRequestDto request = ChatRequestDto.builder()
                .messages(List.of(ChatMessageDto.builder().role("user").content("Unknown").build()))
                .build();

        StepVerifier.create(chatService.chatSingle(request))
                .assertNext(response -> assertThat(response.getMessage().getContent())
                        .contains(
                                "- Give me a quick status update on the Ollama mock",
                                "- Narrate the full streaming timeline for this mock"))
                .verifyComplete();
    }
}
