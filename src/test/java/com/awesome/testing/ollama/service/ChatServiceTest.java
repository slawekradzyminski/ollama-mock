package com.awesome.testing.ollama.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.awesome.testing.ollama.config.OllamaMockProperties;
import com.awesome.testing.ollama.dto.ChatMessageDto;
import com.awesome.testing.ollama.dto.ChatRequestDto;
import com.awesome.testing.ollama.dto.ChatResponseDto;
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
        properties.setModels(List.of("custom-chat-model"));
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
                    assertThat(response.isDone()).isTrue();
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
        properties.setModels(List.of("custom-chat-model"));
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

    private static final String STATUS_PROMPT = "Give me a quick status update on the Ollama mock";
    private static final String LIMITATIONS_PROMPT = "What limitations should I expect from the mock?";

    @Test
    void shouldStreamAnswerToLatestUserPromptInMultiTurnHistory() {
        ChatRequestDto request = ChatRequestDto.builder()
                .messages(List.of(
                        user(STATUS_PROMPT),
                        assistant("The Ollama mock is up on port 11434."),
                        user(LIMITATIONS_PROMPT)))
                .build();

        StepVerifier.create(chatService.chatStream(request).collectList())
                .assertNext(chunks -> assertThat(streamedContent(chunks))
                        .startsWith("It only understands a curated set of prompts")
                        .doesNotContain("port 11434"))
                .verifyComplete();
    }

    @Test
    void shouldAnswerLatestUserPromptWhenStreamingIsDisabled() {
        ChatRequestDto request = ChatRequestDto.builder()
                .messages(List.of(
                        user(STATUS_PROMPT),
                        assistant("ok"),
                        user(LIMITATIONS_PROMPT)))
                .build();

        StepVerifier.create(chatService.chatSingle(request))
                .assertNext(response -> assertThat(response.getMessage().getContent())
                        .startsWith("It only understands a curated set of prompts"))
                .verifyComplete();
    }

    @Test
    void shouldFallBackToEarlierKnownPromptWhenLatestPromptIsUnknown() {
        ChatRequestDto request = ChatRequestDto.builder()
                .messages(List.of(
                        user(LIMITATIONS_PROMPT),
                        assistant("ok"),
                        user(STATUS_PROMPT),
                        assistant("ok"),
                        user("Thanks!")))
                .build();

        StepVerifier.create(chatService.chatSingle(request))
                .assertNext(response -> assertThat(response.getMessage().getContent())
                        .contains("port 11434"))
                .verifyComplete();
    }

    @Test
    void shouldIgnoreAssistantMessagesThatRepeatAKnownPrompt() {
        ChatRequestDto request = ChatRequestDto.builder()
                .messages(List.of(
                        user(STATUS_PROMPT),
                        assistant(LIMITATIONS_PROMPT)))
                .build();

        StepVerifier.create(chatService.chatSingle(request))
                .assertNext(response -> assertThat(response.getMessage().getContent())
                        .contains("port 11434"))
                .verifyComplete();
    }

    @Test
    void shouldMarkNonStreamingScenarioReplyAsDone() {
        ChatRequestDto request = ChatRequestDto.builder()
                .messages(List.of(user(STATUS_PROMPT)))
                .build();

        StepVerifier.create(chatService.chatSingle(request))
                .assertNext(response -> {
                    assertThat(response.isDone()).isTrue();
                    assertThat(response.getModel()).isEqualTo(DEFAULT_MODEL);
                    assertThat(response.getCreatedAt()).isNotBlank();
                    assertThat(response.getMessage().getRole()).isEqualTo("assistant");
                    assertThat(response.getMessage().getContent()).contains("port 11434");
                })
                .verifyComplete();
    }

    @Test
    void shouldMarkNonStreamingUnsupportedPromptReplyAsDone() {
        ChatRequestDto request = ChatRequestDto.builder()
                .messages(List.of(user("Random question")))
                .build();

        StepVerifier.create(chatService.chatSingle(request))
                .assertNext(response -> {
                    assertThat(response.isDone()).isTrue();
                    assertThat(response.getMessage().getContent()).contains("Sorry, only these chat prompts are supported");
                })
                .verifyComplete();
    }

    private static ChatMessageDto user(String content) {
        return ChatMessageDto.builder().role("user").content(content).build();
    }

    private static ChatMessageDto assistant(String content) {
        return ChatMessageDto.builder().role("assistant").content(content).build();
    }

    private static String streamedContent(List<ChatResponseDto> chunks) {
        return chunks.stream()
                .filter(chunk -> chunk.getMessage() != null && chunk.getMessage().getContent() != null)
                .map(chunk -> chunk.getMessage().getContent())
                .collect(Collectors.joining());
    }
}
