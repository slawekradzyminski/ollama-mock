package com.awesome.testing.ollama.service;

import com.awesome.testing.ollama.dto.ChatMessageDto;
import com.awesome.testing.ollama.scenario.chat.ChatScenarioGroundedAnswer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Builds tool-chat answers from the tool messages of the current turn: the products a
 * {@code list_products} result returned and the product a {@code get_product_snapshot} result
 * described. Nothing outside those results is named.
 */
@Component
@RequiredArgsConstructor
public class ToolAnswerComposer {

    static final String LIST_PRODUCTS = "list_products";
    static final String PRODUCT_SNAPSHOT = "get_product_snapshot";
    static final String FIRST_LISTED_PRODUCT_ID = "{{firstListedProductId}}";

    private final ObjectMapper objectMapper;

    public String compose(ChatScenarioGroundedAnswer answer, List<ChatMessageDto> turn) {
        List<String> sentences = new ArrayList<>();
        latestToolResult(turn, LIST_PRODUCTS).ifPresent(result -> sentences.add(describeList(answer, result)));
        latestToolResult(turn, PRODUCT_SNAPSHOT).ifPresent(result -> sentences.add(describeSnapshot(result)));
        if (sentences.isEmpty()) {
            sentences.add("No tool result was returned, so there is nothing from the catalog to report.");
        }
        if (StringUtils.hasText(answer.getClosing())) {
            sentences.add(answer.getClosing().trim());
        }
        return String.join(" ", sentences);
    }

    /**
     * Replaces {@value #FIRST_LISTED_PRODUCT_ID} in tool-call arguments with the id of the first product
     * the latest {@code list_products} result returned; empty when there is no such product.
     */
    public Optional<Map<String, Object>> resolveArguments(Map<String, Object> arguments, List<ChatMessageDto> turn) {
        if (arguments == null || !arguments.containsValue(FIRST_LISTED_PRODUCT_ID)) {
            return Optional.ofNullable(arguments);
        }
        Optional<Long> firstId = latestToolResult(turn, LIST_PRODUCTS)
                .flatMap(result -> listedProducts(result).stream().findFirst())
                .map(product -> product.path("id"))
                .filter(JsonNode::isNumber)
                .map(JsonNode::asLong);
        return firstId.map(id -> {
            Map<String, Object> resolved = new java.util.LinkedHashMap<>(arguments);
            resolved.replaceAll((key, value) -> FIRST_LISTED_PRODUCT_ID.equals(value) ? id : value);
            return resolved;
        });
    }

    private String describeList(ChatScenarioGroundedAnswer answer, JsonNode result) {
        if (result.hasNonNull("error")) {
            return "The catalog lookup failed: %s.".formatted(stripPeriod(result.get("error").asString()));
        }
        List<JsonNode> matching = listedProducts(result).stream()
                .filter(product -> withinPrice(product, answer.getMaxPrice()))
                .toList();
        if (matching.isEmpty()) {
            return StringUtils.hasText(answer.getEmptyAnswer())
                    ? answer.getEmptyAnswer().trim()
                    : "The catalog returned no matching products.";
        }
        String intro = StringUtils.hasText(answer.getListIntro()) ? answer.getListIntro().trim() : "The catalog returned:";
        String listed = "%s %s.".formatted(intro, joinNames(matching.stream().map(this::describeProduct).toList()));
        boolean unpriced = matching.stream().anyMatch(product -> !product.path("price").isNumber());
        if (answer.getMaxPrice() != null && unpriced) {
            listed += " The catalog list has no prices, so I cannot confirm which of them cost under $%s."
                    .formatted(answer.getMaxPrice().stripTrailingZeros().toPlainString());
        }
        return listed;
    }

    private String describeSnapshot(JsonNode result) {
        if (result.hasNonNull("error")) {
            return "The product snapshot lookup failed: %s.".formatted(stripPeriod(result.get("error").asString()));
        }
        return "Snapshot for %s: priced at %s with %s unit(s) in stock.".formatted(
                text(result, "name"), price(result.path("price")), text(result, "stockQuantity"));
    }

    /** {@code list_products} returns ids and names only; price and stock are added when a result carries them. */
    private String describeProduct(JsonNode product) {
        List<String> details = new ArrayList<>();
        if (product.path("price").isNumber()) {
            details.add(price(product.path("price")));
        }
        if (product.path("stockQuantity").isNumber()) {
            details.add(product.path("stockQuantity").asString() + " in stock");
        }
        return details.isEmpty() ? text(product, "name") : "%s (%s)".formatted(text(product, "name"), String.join(", ", details));
    }

    private List<JsonNode> listedProducts(JsonNode result) {
        JsonNode products = result.path("products");
        if (!products.isArray()) {
            return List.of();
        }
        List<JsonNode> listed = new ArrayList<>();
        products.forEach(listed::add);
        return listed;
    }

    private boolean withinPrice(JsonNode product, BigDecimal maxPrice) {
        if (maxPrice == null) {
            return true;
        }
        JsonNode price = product.path("price");
        // Without a price the product cannot be excluded; the answer then says prices are unconfirmed.
        return !price.isNumber() || price.decimalValue().compareTo(maxPrice) <= 0;
    }

    private Optional<JsonNode> latestToolResult(List<ChatMessageDto> turn, String toolName) {
        for (int i = turn.size() - 1; i >= 0; i--) {
            ChatMessageDto message = turn.get(i);
            if ("tool".equalsIgnoreCase(message.getRole()) && toolName.equalsIgnoreCase(message.getToolName())) {
                return Optional.of(parse(message.getContent()));
            }
        }
        return Optional.empty();
    }

    private JsonNode parse(String content) {
        if (!StringUtils.hasText(content)) {
            return objectMapper.createObjectNode().put("error", "the tool returned no content");
        }
        try {
            JsonNode node = objectMapper.readTree(content);
            return node.isObject() ? node : objectMapper.createObjectNode().put("error", "the tool result is not a JSON object");
        } catch (RuntimeException e) {
            return objectMapper.createObjectNode().put("error", "the tool result is not valid JSON");
        }
    }

    private static String joinNames(List<String> names) {
        if (names.size() == 1) {
            return names.getFirst();
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.getLast();
    }

    private static String price(JsonNode price) {
        return price.isNumber() ? "$" + price.decimalValue().setScale(2, RoundingMode.HALF_UP).toPlainString() : "an unknown price";
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "unknown" : value.asString();
    }

    private static String stripPeriod(String message) {
        return message.endsWith(".") ? message.substring(0, message.length() - 1) : message;
    }
}
