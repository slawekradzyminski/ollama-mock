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
        properties.setModels(List.of("custom-tools-model", "custom-model"));
        properties.setTokenDelay(Duration.ZERO);
        properties.setToolCallDelay(Duration.ZERO);
        chatToolsService = new ChatToolsService(
                properties,
                new ChatScenarioRepository(new ObjectMapper()),
                new ToolAnswerComposer(new ObjectMapper()));
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
        ChatMessageDto toolMessage = tool("list_products", PHONES);

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
        ChatMessageDto catalog = tool("list_products", PHONES);
        ChatMessageDto snapshot = tool("get_product_snapshot", PHONE_SNAPSHOT);

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
                    assertThat(content).isEqualTo("According to the catalog, these electronics are in stock: "
                            + "iPhone 13 Pro and Samsung Galaxy S21. "
                            + "Snapshot for iPhone 13 Pro: priced at $999.99 with 50 unit(s) in stock.");
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
        properties.setModels(List.of("custom-tools-model", "custom-model"));
        properties.setTokenDelay(Duration.ofSeconds(1));
        properties.setToolCallDelay(Duration.ofSeconds(2));
        ChatToolsService delayedService = new ChatToolsService(
                properties,
                new ChatScenarioRepository(new ObjectMapper()),
                new ToolAnswerComposer(new ObjectMapper()));
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
                        tool("list_products", PHONES)
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

    @Test
    void shouldStartToolLoopOfLatestUserPromptInMultiTurnConversation() {
        ChatRequestDto request = ChatRequestDto.builder()
                .messages(List.of(
                        ChatMessageDto.builder()
                                .role("user")
                                .content("What iphones do we have available? Tell me the details about them")
                                .build(),
                        ChatMessageDto.builder().role("assistant").content("iPhone 13 Pro is in stock.").build(),
                        ChatMessageDto.builder().role("user").content("What Beauty products do we have available?").build()))
                .build();

        StepVerifier.create(chatToolsService.chatToolSingle(request))
                .assertNext(response -> {
                    var toolCall = response.getMessage().getToolCalls().getFirst().getFunction();
                    assertThat(toolCall.getName()).isEqualTo("list_products");
                    assertThat(toolCall.getArguments()).containsEntry("category", "beauty");
                })
                .verifyComplete();
    }

    @Test
    void shouldFinishToolLoopOfLatestUserPromptAfterItsToolResult() {
        ChatRequestDto request = ChatRequestDto.builder()
                .messages(List.of(
                        ChatMessageDto.builder()
                                .role("user")
                                .content("What iphones do we have available? Tell me the details about them")
                                .build(),
                        ChatMessageDto.builder().role("assistant").content("iPhone 13 Pro is in stock.").build(),
                        ChatMessageDto.builder().role("user").content("What Beauty products do we have available?").build(),
                        ChatMessageDto.builder().role("tool").toolName("list_products").content("{}").build()))
                .build();

        StepVerifier.create(chatToolsService.chatToolSingle(request))
                .assertNext(response -> assertThat(response.getMessage().getContent())
                        .startsWith("The beauty shelf currently offers"))
                .verifyComplete();
    }

    @Test
    void shouldIgnoreAssistantMessagesThatRepeatAKnownToolPrompt() {
        ChatRequestDto request = ChatRequestDto.builder()
                .messages(List.of(
                        ChatMessageDto.builder().role("user").content("What Beauty products do we have available?").build(),
                        ChatMessageDto.builder()
                                .role("assistant")
                                .content("What iphones do we have available? Tell me the details about them")
                                .build(),
                        ChatMessageDto.builder().role("tool").toolName("list_products").content("{}").build()))
                .build();

        StepVerifier.create(chatToolsService.chatToolSingle(request))
                .assertNext(response -> assertThat(response.getMessage().getContent())
                        .startsWith("The beauty shelf currently offers"))
                .verifyComplete();
    }

    @Test
    void shouldMarkNonStreamingToolCallReplyAsDone() {
        ChatRequestDto request = ChatRequestDto.builder()
                .model("custom-model")
                .messages(List.of(ChatMessageDto.builder().role("user").content("What Beauty products do we have available?").build()))
                .build();

        StepVerifier.create(chatToolsService.chatToolSingle(request))
                .assertNext(response -> {
                    assertThat(response.isDone()).isTrue();
                    assertThat(response.getModel()).isEqualTo("custom-model");
                    assertThat(response.getCreatedAt()).isNotBlank();
                    assertThat(response.getMessage().getToolCalls().getFirst().getFunction().getName())
                            .isEqualTo("list_products");
                })
                .verifyComplete();
    }

    @Test
    void shouldMarkNonStreamingFinalAnswerAsDone() {
        ChatRequestDto request = ChatRequestDto.builder()
                .messages(List.of(
                        ChatMessageDto.builder().role("user").content("What Beauty products do we have available?").build(),
                        ChatMessageDto.builder().role("tool").toolName("list_products").content("{}").build()))
                .build();

        StepVerifier.create(chatToolsService.chatToolSingle(request))
                .assertNext(response -> {
                    assertThat(response.isDone()).isTrue();
                    assertThat(response.getMessage().getContent()).startsWith("The beauty shelf currently offers");
                })
                .verifyComplete();
    }

    @Test
    void shouldMarkNonStreamingUnsupportedToolPromptReplyAsDone() {
        ChatRequestDto request = ChatRequestDto.builder()
                .messages(List.of(ChatMessageDto.builder().role("user").content("Unknown").build()))
                .build();

        StepVerifier.create(chatToolsService.chatToolSingle(request))
                .assertNext(response -> assertThat(response.isDone()).isTrue())
                .verifyComplete();
    }

    @Test
    void shouldMarkNonStreamingUnhandledStageReplyAsDone() {
        ChatRequestDto request = ChatRequestDto.builder()
                .messages(List.of(
                        ChatMessageDto.builder().role("user").content("What Beauty products do we have available?").build(),
                        ChatMessageDto.builder().role("tool").toolName("get_product_snapshot").content("{}").build()))
                .build();

        StepVerifier.create(chatToolsService.chatToolSingle(request))
                .assertNext(response -> {
                    assertThat(response.isDone()).isTrue();
                    assertThat(response.getMessage().getContent()).contains("is not configured yet");
                })
                .verifyComplete();
    }

    private static final String PHONES = """
            {"products":[{"id":7,"name":"iPhone 13 Pro"},{"id":8,"name":"Samsung Galaxy S21"}],
            "total":2,"page":0,"size":25}""";
    private static final String PHONE_SNAPSHOT = """
            {"id":7,"name":"iPhone 13 Pro","price":999.99,"stockQuantity":50,"category":"Electronics"}""";
    private static final String EMPTY_LIST = """
            {"products":[],"total":0,"page":0,"size":25}""";
    private static final String COMPUTERS = """
            {"products":[{"id":3,"name":"MacBook Pro 14","price":1999.99,"stockQuantity":25},
            {"id":9,"name":"ThinkPad X1","price":1349.5,"stockQuantity":4}],"total":2,"page":0,"size":25}""";

    private static ChatMessageDto user(String content) {
        return ChatMessageDto.builder().role("user").content(content).build();
    }

    private static ChatMessageDto tool(String name, String content) {
        return ChatMessageDto.builder().role("tool").toolName(name).content(content).build();
    }

    private String streamedAnswer(List<ChatMessageDto> messages) {
        return chatToolsService.chatToolStream(ChatRequestDto.builder().messages(messages).build())
                .filter(chunk -> chunk.getMessage() != null && chunk.getMessage().getContent() != null)
                .map(chunk -> chunk.getMessage().getContent())
                .reduce("", String::concat)
                .block();
    }

    @Test
    void shouldAskForSnapshotOfFirstListedProduct() {
        ChatRequestDto request = ChatRequestDto.builder()
                .messages(List.of(user("What iphones do we have available? Tell me the details about them"),
                        tool("list_products", PHONES)))
                .stream(false)
                .build();

        StepVerifier.create(chatToolsService.chatToolSingle(request))
                .assertNext(response -> assertThat(response.getMessage().getToolCalls()).singleElement()
                        .satisfies(call -> {
                            assertThat(call.getFunction().getName()).isEqualTo("get_product_snapshot");
                            assertThat(call.getFunction().getArguments()).isEqualTo(java.util.Map.of("productId", 7L));
                        }))
                .verifyComplete();
    }

    @Test
    void shouldAnswerFromEmptyCatalogWithoutInventingProductsOrCallingSnapshot() {
        var chunks = chatToolsService.chatToolStream(ChatRequestDto.builder()
                        .messages(List.of(user("Do we have any smart home devices in stock?"), tool("list_products", EMPTY_LIST)))
                        .build())
                .collectList()
                .block();

        assertThat(chunks).noneSatisfy(chunk -> assertThat(chunk.getMessage() != null
                && !chunk.getMessage().getToolCalls().isEmpty()).isTrue());
        String answer = chunks.stream()
                .filter(chunk -> chunk.getMessage() != null && chunk.getMessage().getContent() != null)
                .map(chunk -> chunk.getMessage().getContent())
                .reduce("", String::concat);
        assertThat(answer).isEqualTo("The catalog has no smart home devices in stock right now.");
    }

    @Test
    void shouldListOnlyLaptopsUnderTheAskedPrice() {
        String answer = streamedAnswer(List.of(user("List the laptops under $1500."), tool("list_products", COMPUTERS)));

        assertThat(answer).isEqualTo("The catalog lists these laptops: ThinkPad X1 ($1349.50, 4 in stock).");
    }

    @Test
    void shouldSayNoLaptopsWhenNoneIsUnderThePrice() {
        String answer = streamedAnswer(List.of(user("List the laptops under $1500."), tool("list_products", """
                {"products":[{"id":3,"name":"MacBook Pro 14","price":1999.99,"stockQuantity":25}],"total":1}""")));

        assertThat(answer).isEqualTo("The catalog has no laptops under $1500 right now.");
    }

    @Test
    void shouldAskTheCatalogForSeededCategories() {
        ChatRequestDto laptops = ChatRequestDto.builder().messages(List.of(user("List the laptops under $1500."))).build();
        ChatRequestDto gaming = ChatRequestDto.builder()
                .messages(List.of(user("What gaming accessories can you recommend?"))).build();

        StepVerifier.create(chatToolsService.chatToolSingle(laptops))
                .assertNext(response -> assertThat(response.getMessage().getToolCalls().getFirst().getFunction()
                        .getArguments()).containsEntry("category", "computers"))
                .verifyComplete();
        StepVerifier.create(chatToolsService.chatToolSingle(gaming))
                .assertNext(response -> assertThat(response.getMessage().getToolCalls().getFirst().getFunction()
                        .getArguments()).containsEntry("category", "gaming"))
                .verifyComplete();
    }

    @Test
    void shouldReportToolErrorsInsteadOfProducts() {
        String answer = streamedAnswer(List.of(user("What gaming accessories can you recommend?"),
                tool("list_products", "{\"error\":\"category must be a plain category name without tool markup\"}")));

        assertThat(answer).isEqualTo(
                "The catalog lookup failed: category must be a plain category name without tool markup.");
    }

    @Test
    void shouldReportUnreadableToolResult() {
        String answer = streamedAnswer(List.of(user("What gaming accessories can you recommend?"),
                tool("list_products", "not json")));

        assertThat(answer).isEqualTo("The catalog lookup failed: the tool result is not valid JSON.");
    }

    @Test
    void shouldReportFailedSnapshotLookup() {
        String answer = streamedAnswer(List.of(user("Walk me through a two-step catalog lookup where you narrate between tool calls"),
                tool("list_products", PHONES), tool("get_product_snapshot", "{\"error\":\"Product not found\"}")));

        assertThat(answer).isEqualTo("The catalog slice contained: iPhone 13 Pro and "
                + "Samsung Galaxy S21. The product snapshot lookup failed: Product not found. "
                + "With all tool data collected, the summary is complete.");
    }

    @Test
    void shouldUseOnlyToolResultsOfTheLatestQuestion() {
        String answer = streamedAnswer(List.of(
                user("What iphones do we have available? Tell me the details about them"),
                tool("list_products", PHONES),
                tool("get_product_snapshot", PHONE_SNAPSHOT),
                ChatMessageDto.builder().role("assistant").content("done").build(),
                user("What gaming accessories can you recommend?"),
                tool("list_products", EMPTY_LIST)));

        assertThat(answer).isEqualTo(
                "The catalog has no gaming products in stock right now, so I have nothing to recommend.");
    }

    @Test
    void shouldKeepTheCourseAssertedBeautyAnswerVerbatim() {
        String answer = streamedAnswer(List.of(user("What Beauty products do we have available?"),
                tool("list_products", EMPTY_LIST)));

        assertThat(answer).isEqualTo("The beauty shelf currently offers: Hydrating Face Serum and Velvet Matte "
                + "Lipstick. Let me know if you want pricing for either product.");
    }

    @Test
    void shouldRejectUnknownModelBeforeStreaming() {
        ChatRequestDto request = ChatRequestDto.builder()
                .model("no-such-model:1b")
                .messages(List.of(user("What Beauty products do we have available?")))
                .build();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> chatToolsService.chatToolStream(request))
                .isInstanceOf(ModelNotFoundException.class)
                .hasMessage("model 'no-such-model:1b' not found");
    }

    @Test
    void shouldKeepProductsPricedExactlyAtTheLimit() {
        String answer = streamedAnswer(List.of(user("List the laptops under $1500."), tool("list_products", """
                {"products":[{"id":4,"name":"Edge Book","price":1500,"stockQuantity":1}],"total":1}""")));

        assertThat(answer).isEqualTo("The catalog lists these laptops: Edge Book ($1500.00, 1 in stock).");
    }

    @Test
    void shouldReportEmptyToolContentAndTrimTrailingPeriodOfToolErrors() {
        String answer = streamedAnswer(List.of(user("Walk me through a two-step catalog lookup where you narrate between tool calls"),
                tool("list_products", ""), tool("get_product_snapshot", "{\"error\":\"Product not found.\"}")));

        assertThat(answer).isEqualTo("The catalog lookup failed: the tool returned no content. "
                + "The product snapshot lookup failed: Product not found. "
                + "With all tool data collected, the summary is complete.");
    }

    @Test
    void shouldListNameOnlyCatalogResultsAndSayPricesAreUnconfirmed() {
        String answer = streamedAnswer(List.of(user("List the laptops under $1500."), tool("list_products", """
                {"products":[{"id":3,"name":"MacBook Pro 14"},{"id":9,"name":"ThinkPad X1"}],"total":2,"page":0,"size":25}""")));

        assertThat(answer).isEqualTo("The catalog lists these laptops: MacBook Pro 14 and ThinkPad X1. "
                + "The catalog list has no prices, so I cannot confirm which of them cost under $1500.");
    }

    @Test
    void shouldDescribeProductsWithOnlySomeDetails() {
        String answer = streamedAnswer(List.of(user("What gaming accessories can you recommend?"), tool("list_products", """
                {"products":[{"id":1,"name":"Pad","stockQuantity":2},{"id":2,"name":"Stick","price":10}]}""")));

        assertThat(answer).isEqualTo("From the gaming shelf I can recommend: Pad (2 in stock) and Stick ($10.00).");
    }
}
