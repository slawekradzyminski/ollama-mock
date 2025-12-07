package com.awesome.testing.ollama.service;

import com.awesome.testing.ollama.dto.OllamaToolDefinitionDto;
import com.awesome.testing.ollama.dto.OllamaToolFunctionDto;
import com.awesome.testing.ollama.dto.OllamaToolParametersDto;
import com.awesome.testing.ollama.dto.OllamaToolParametersRequirementDto;
import com.awesome.testing.ollama.dto.OllamaToolSchemaPropertyDto;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ToolDefinitionCatalog {

    @Getter
    private List<OllamaToolDefinitionDto> definitions;

    @PostConstruct
    void init() {
        definitions = List.of(
                productSnapshotTool(),
                listProductsTool()
        );
        log.info("Registered {} tool definitions for mock: {}", definitions.size(),
                definitions.stream().map(def -> def.getFunction().getName()).toList());
    }

    private OllamaToolDefinitionDto productSnapshotTool() {
        return OllamaToolDefinitionDto.builder()
                .function(OllamaToolFunctionDto.builder()
                        .name("get_product_snapshot")
                        .description("Return a trusted snapshot for a product so the assistant can cite real pricing/stock.")
                        .parameters(OllamaToolParametersDto.builder()
                                .type("object")
                                .properties(Map.of(
                                        "productId", property("integer", "Numeric product id shown in the catalog."),
                                        "name", property("string", "Exact product name when the id is unknown.")
                                ))
                                .oneOf(List.of(
                                        requirement("productId"),
                                        requirement("name")
                                ))
                                .build())
                        .build())
                .build();
    }

    private OllamaToolDefinitionDto listProductsTool() {
        return OllamaToolDefinitionDto.builder()
                .function(OllamaToolFunctionDto.builder()
                        .name("list_products")
                        .description("Return a lightweight catalog slice before calling get_product_snapshot for details.")
                        .parameters(OllamaToolParametersDto.builder()
                                .type("object")
                                .properties(Map.of(
                                        "offset", property("integer", "Zero-based offset (default 0)."),
                                        "limit", property("integer", "Number of products to fetch (default 25)."),
                                        "category", property("string", "Case-insensitive category filter, e.g., 'electronics'."),
                                        "inStockOnly", property("boolean", "If true, only return products with stockQuantity > 0.")
                                ))
                                .build())
                        .build())
                .build();
    }

    private OllamaToolSchemaPropertyDto property(String type, String description) {
        return OllamaToolSchemaPropertyDto.builder()
                .type(type)
                .description(description)
                .build();
    }

    private OllamaToolParametersRequirementDto requirement(String field) {
        return OllamaToolParametersRequirementDto.builder()
                .required(List.of(field))
                .build();
    }
}
