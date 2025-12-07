package com.awesome.testing.ollama.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.awesome.testing.ollama.config.OllamaMockProperties;
import com.awesome.testing.ollama.dto.ChatMessageDto;
import com.awesome.testing.ollama.dto.ChatRequestDto;
import com.awesome.testing.ollama.scenario.chat.ChatScenarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class ChatToolsServiceTest {

    private ChatToolsService chatToolsService;

    @BeforeEach
    void setUp() {
        chatToolsService = new ChatToolsService(
                new OllamaMockProperties(),
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

        StepVerifier.create(chatToolsService.chatToolStream(request))
                .assertNext(chunk -> assertThat(chunk.getMessage().getToolCalls().get(0).getFunction().getName())
                        .isEqualTo("list_products"))
                .assertNext(chunk -> assertThat(chunk.isDone()).isTrue())
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

        StepVerifier.create(chatToolsService.chatToolStream(request))
                .assertNext(chunk -> assertThat(chunk.getMessage().getToolCalls().get(0).getFunction().getName())
                        .isEqualTo("get_product_snapshot"))
                .assertNext(chunk -> assertThat(chunk.isDone()).isTrue())
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

        StepVerifier.create(chatToolsService.chatToolStream(request))
                .assertNext(chunk -> assertThat(chunk.getMessage().getContent())
                        .contains("iPhone 13 Pro"))
                .assertNext(chunk -> assertThat(chunk.isDone()).isTrue())
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

        StepVerifier.create(chatToolsService.chatToolStream(request))
                .assertNext(chunk -> assertThat(chunk.getMessage().getContent())
                        .contains("Sorry, only these chat tool prompts are supported"))
                .assertNext(chunk -> assertThat(chunk.isDone()).isTrue())
                .verifyComplete();
    }
}
