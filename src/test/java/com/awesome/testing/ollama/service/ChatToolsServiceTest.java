package com.awesome.testing.ollama.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.awesome.testing.ollama.config.OllamaMockProperties;
import com.awesome.testing.ollama.dto.ChatMessageDto;
import com.awesome.testing.ollama.dto.ChatRequestDto;
import com.awesome.testing.ollama.scenario.chat.ChatScenarioRepository;
import tools.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class ChatToolsServiceTest {

    private static final String DEFAULT_MODEL = "qwen3.5:2b";

    private ChatToolsService chatToolsService;

    @BeforeEach
    void setUp() {
        OllamaMockProperties properties = new OllamaMockProperties();
        properties.setDefaultModel(DEFAULT_MODEL);
        properties.setTokenDelay(Duration.ZERO);
        properties.setToolCallDelay(Duration.ZERO);
        chatToolsService = new ChatToolsService(
                properties,
                new ChatScenarioRepository(new ObjectMapper()));
    }

    @Test
    void shouldEmitToolCallForPhonePrompt() {
        ChatRequestDto request = ChatRequestDto.builder()
                .messages(List.of(ChatMessageDto.builder()
                        .role("user")
                        .content("What iphones do we have available? Tell me the details about them")
                        .build()))
                .build();

        StepVerifier.create(chatToolsService.chatToolStream(request).collectList())
                .assertNext(chunks -> {
                    assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.getModel()).isEqualTo(DEFAULT_MODEL));
                    assertThat(chunks.get(0).getMessage().getToolCalls().get(0).getFunction().getName())
                            .isEqualTo("list_products");
                    assertThat(chunks.get(chunks.size() - 1).isDone()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    void shouldIssueSnapshotAfterCatalogResult() {
        ChatMessageDto userMessage = ChatMessageDto.builder()
                .role("user")
                .content("What iphones do we have available? Tell me the details about them")
                .build();
        ChatMessageDto toolMessage = ChatMessageDto.builder()
                .role("tool")
                .toolName("list_products")
                .build();

        ChatRequestDto request = ChatRequestDto.builder()
                .messages(List.of(userMessage, toolMessage))
                .build();

        StepVerifier.create(chatToolsService.chatToolStream(request).collectList())
                .assertNext(chunks -> {
                    assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.getModel()).isEqualTo(DEFAULT_MODEL));
                    assertThat(chunks.get(0).getMessage().getToolCalls().get(0).getFunction().getName())
                            .isEqualTo("get_product_snapshot");
                    assertThat(chunks.get(chunks.size() - 1).isDone()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    void shouldRespondWithSummaryAfterSnapshot() {
        ChatMessageDto userMessage = ChatMessageDto.builder()
                .role("user")
                .content("What iphones do we have available? Tell me the details about them")
                .build();
        ChatMessageDto catalog = ChatMessageDto.builder()
                .role("tool")
                .toolName("list_products")
                .build();
        ChatMessageDto snapshot = ChatMessageDto.builder()
                .role("tool")
                .toolName("get_product_snapshot")
                .build();

        ChatRequestDto request = ChatRequestDto.builder()
                .messages(List.of(userMessage, catalog, snapshot))
                .build();

        StepVerifier.create(chatToolsService.chatToolStream(request).collectList())
                .assertNext(chunks -> {
                    assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.getModel()).isEqualTo(DEFAULT_MODEL));
                    String content = chunks.stream()
                            .filter(chunk -> chunk.getMessage() != null && chunk.getMessage().getContent() != null)
                            .map(chunk -> chunk.getMessage().getContent())
                            .reduce("", String::concat);
                    assertThat(content).contains("iPhone 13 Pro");
                    assertThat(chunks.get(chunks.size() - 1).isDone()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    void shouldOfferSupportedPromptListWhenUnknown() {
        ChatRequestDto request = ChatRequestDto.builder()
                .messages(List.of(ChatMessageDto.builder()
                        .role("user")
                        .content("Unsupported tool prompt")
                        .build()))
                .build();

        StepVerifier.create(chatToolsService.chatToolStream(request).collectList())
                .assertNext(chunks -> {
                    assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.getModel()).isEqualTo(DEFAULT_MODEL));
                    String content = chunks.stream()
                            .filter(chunk -> chunk.getMessage() != null && chunk.getMessage().getContent() != null)
                            .map(chunk -> chunk.getMessage().getContent())
                            .reduce("", String::concat);
                    assertThat(content).contains("Sorry, only these chat tool prompts are supported");
                    assertThat(chunks.get(chunks.size() - 1).isDone()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    void shouldAcceptThinkingRequestsWithoutChangingToolFlow() {
        ChatRequestDto request = ChatRequestDto.builder()
                .messages(List.of(ChatMessageDto.builder()
                        .role("user")
                        .content("What iphones do we have available? Tell me the details about them")
                        .build()))
                .think(true)
                .build();

        StepVerifier.create(chatToolsService.chatToolStream(request).collectList())
                .assertNext(chunks -> {
                    assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.getModel()).isEqualTo(DEFAULT_MODEL));
                    assertThat(chunks.stream()
                            .filter(chunk -> chunk.getMessage() != null)
                            .flatMap(chunk -> java.util.stream.Stream.ofNullable(chunk.getMessage().getThinking()))
                            .findAny())
                            .isEmpty();
                    assertThat(chunks.get(0).getMessage().getToolCalls().get(0).getFunction().getName())
                            .isEqualTo("list_products");
                    assertThat(chunks.get(chunks.size() - 1).isDone()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnToolCallForSingleResponseMode() {
        ChatRequestDto request = ChatRequestDto.builder()
                .model("custom-tools-model")
                .messages(List.of(ChatMessageDto.builder()
                        .role("user")
                        .content("What iphones do we have available? Tell me the details about them")
                        .build()))
                .build();

        StepVerifier.create(chatToolsService.chatToolSingle(request))
                .assertNext(response -> {
                    assertThat(response.getModel()).isEqualTo("custom-tools-model");
                    assertThat(response.getMessage().getToolCalls()).singleElement()
                            .satisfies(toolCall -> assertThat(toolCall.getFunction().getName())
                                    .isEqualTo("list_products"));
                })
                .verifyComplete();
    }

    @Test
    void shouldUseTheToolCallDelayWithoutWaitingInRealTime() {
        OllamaMockProperties properties = new OllamaMockProperties();
        properties.setDefaultModel(DEFAULT_MODEL);
        properties.setTokenDelay(Duration.ofSeconds(1));
        properties.setToolCallDelay(Duration.ofSeconds(2));
        ChatToolsService delayedService = new ChatToolsService(
                properties,
                new ChatScenarioRepository(new ObjectMapper()));
        ChatRequestDto request = ChatRequestDto.builder()
                .messages(List.of(ChatMessageDto.builder()
                        .role("user")
                        .content("What iphones do we have available? Tell me the details about them")
                        .build()))
                .build();

        StepVerifier.withVirtualTime(() -> delayedService.chatToolStream(request).take(1))
                .expectSubscription()
                .expectNoEvent(Duration.ofMillis(1999))
                .thenAwait(Duration.ofMillis(1))
                .assertNext(response -> assertThat(response.getMessage().getToolCalls()).isNotEmpty())
                .verifyComplete();
    }

    @Test
    void shouldPreferToolCallWhenSingleStageAlsoContainsNarration() {
        ChatRequestDto request = ChatRequestDto.builder()
                .messages(List.of(
                        ChatMessageDto.builder()
                                .role("user")
                                .content("Walk me through a two-step catalog lookup where you narrate between tool calls")
                                .build(),
                        ChatMessageDto.builder().role("tool").toolName("list_products").build()
                ))
                .build();

        StepVerifier.create(chatToolsService.chatToolSingle(request))
                .assertNext(response -> {
                    assertThat(response.getMessage().getContent()).isNull();
                    assertThat(response.getMessage().getToolCalls().getFirst().getFunction().getName())
                            .isEqualTo("get_product_snapshot");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnCompleteToolPromptListForUnknownSingleRequest() {
        ChatRequestDto request = ChatRequestDto.builder()
                .messages(List.of(ChatMessageDto.builder().role("user").content("Unknown").build()))
                .build();

        StepVerifier.create(chatToolsService.chatToolSingle(request))
                .assertNext(response -> assertThat(response.getMessage().getContent())
                        .contains(
                                "- What iphones do we have available? Tell me the details about them",
                                "- Walk me through a two-step catalog lookup where you narrate between tool calls"))
                .verifyComplete();
    }
}
