package com.awesome.testing.ollama.service;

import com.awesome.testing.ollama.config.OllamaMockProperties;
import com.awesome.testing.ollama.dto.ChatMessageDto;
import com.awesome.testing.ollama.dto.ChatRequestDto;
import com.awesome.testing.ollama.dto.ChatResponseDto;
import com.awesome.testing.ollama.dto.ToolCallDto;
import com.awesome.testing.ollama.dto.ToolCallFunctionDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final OllamaMockProperties properties;

    public Flux<ChatResponseDto> chatStream(ChatRequestDto request) {
        String model = resolveModel(request.getModel());
        Flux<ChatResponseDto> conversationFlux;

        if (shouldRequestProductTooling(request)) {
            conversationFlux = Flux.just(toolCallChunk(model));
        } else if (containsToolMessage(request)) {
            conversationFlux = Flux.just(respondUsingToolPayload(model, request));
        } else {
            conversationFlux = Flux.just(simpleAssistantChunk(model, lastUserContent(request)));
        }

        return conversationFlux.concatWithValues(doneChunk(model));
    }

    public Mono<ChatResponseDto> chatSingle(ChatRequestDto request) {
        return chatStream(request)
                .filter(resp -> resp.getMessage() != null)
                .last();
    }

    private String resolveModel(String requestedModel) {
        if (StringUtils.hasText(requestedModel)) {
            return requestedModel;
        }
        return properties.getDefaultModel();
    }

    private boolean shouldRequestProductTooling(ChatRequestDto request) {
        if (CollectionUtils.isEmpty(request.getTools())) {
            return false;
        }
        if (containsToolMessage(request)) {
            return false;
        }
        String userMessage = lastUserContent(request);
        if (!StringUtils.hasText(userMessage)) {
            return false;
        }
        String normalized = userMessage.toLowerCase();
        return normalized.contains("iphone")
                || normalized.contains("product")
                || normalized.contains("catalog");
    }

    private boolean containsToolMessage(ChatRequestDto request) {
        return Optional.ofNullable(request.getMessages())
                .orElseGet(Collections::emptyList)
                .stream()
                .anyMatch(msg -> "tool".equalsIgnoreCase(msg.getRole()));
    }

    private String lastUserContent(ChatRequestDto request) {
        List<ChatMessageDto> messages = Optional.ofNullable(request.getMessages())
                .orElseGet(Collections::emptyList);
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessageDto message = messages.get(i);
            if ("user".equalsIgnoreCase(message.getRole()) && StringUtils.hasText(message.getContent())) {
                return message.getContent();
            }
        }
        return "";
    }

    private ChatResponseDto toolCallChunk(String model) {
        ToolCallDto toolCall = ToolCallDto.builder()
                .id("toolcall-" + UUID.randomUUID())
                .function(ToolCallFunctionDto.builder()
                        .name("list_products")
                        .arguments(Map.of(
                                "category", "electronics",
                                "inStockOnly", true,
                                "limit", 25
                        ))
                        .build())
                .build();

        ChatMessageDto message = ChatMessageDto.builder()
                .role("assistant")
                .thinking("Looking up the catalog before responding…")
                .toolCalls(List.of(toolCall))
                .build();

        return ChatResponseDto.builder()
                .model(model)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC).toString())
                .message(message)
                .done(false)
                .build();
    }

    private ChatResponseDto respondUsingToolPayload(String model, ChatRequestDto request) {
        ChatMessageDto latestToolMessage = findLatestByRole(request, "tool");
        String summary = summarizeToolPayload(latestToolMessage);
        ChatMessageDto message = ChatMessageDto.builder()
                .role("assistant")
                .content(summary)
                .build();
        return ChatResponseDto.builder()
                .model(model)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC).toString())
                .message(message)
                .done(false)
                .build();
    }

    private ChatMessageDto findLatestByRole(ChatRequestDto request, String role) {
        List<ChatMessageDto> messages = Optional.ofNullable(request.getMessages())
                .orElseGet(Collections::emptyList);
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessageDto message = messages.get(i);
            if (role.equalsIgnoreCase(message.getRole())) {
                return message;
            }
        }
        return null;
    }

    private String summarizeToolPayload(ChatMessageDto toolMessage) {
        if (toolMessage == null || !StringUtils.hasText(toolMessage.getContent())) {
            return "The catalog data is not available yet, but feel free to rerun the request.";
        }
        try {
            JsonNode root = objectMapper.readTree(toolMessage.getContent());
            if ("list_products".equalsIgnoreCase(toolMessage.getToolName()) && root.has("products")) {
                List<String> names = optionalArray(root.get("products")).stream()
                        .map(node -> node.get("name"))
                        .filter(JsonNode::isTextual)
                        .map(JsonNode::asText)
                        .collect(Collectors.toList());
                if (names.isEmpty()) {
                    return "The catalog is empty right now.";
                }
                List<String> top = names.stream().limit(2).toList();
                return "According to the catalog we currently have: " + String.join(", ", top) + ".";
            }
            if ("get_product_snapshot".equalsIgnoreCase(toolMessage.getToolName()) && root.has("name")) {
                String name = root.path("name").asText();
                String price = root.path("price").asText();
                return "Snapshot for " + name + ": priced at " + price + ".";
            }
        } catch (Exception ex) {
            log.warn("Failed to parse tool payload", ex);
        }
        return "Here is what the tool returned: " + toolMessage.getContent();
    }

    private List<JsonNode> optionalArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return StreamSupport.stream(node.spliterator(), false).collect(Collectors.toList());
    }

    private ChatResponseDto simpleAssistantChunk(String model, String userMessage) {
        String reply = StringUtils.hasText(userMessage)
                ? "Mock response: I hear you asked \"" + userMessage + "\". The real model would elaborate here."
                : "Mock response: awaiting additional details.";
        ChatMessageDto message = ChatMessageDto.builder()
                .role("assistant")
                .content(reply)
                .build();
        return ChatResponseDto.builder()
                .model(model)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC).toString())
                .message(message)
                .done(false)
                .build();
    }

    private ChatResponseDto doneChunk(String model) {
        return ChatResponseDto.builder()
                .model(model)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC).toString())
                .done(true)
                .build();
    }
}
