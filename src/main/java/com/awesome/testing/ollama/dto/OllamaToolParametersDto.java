package com.awesome.testing.ollama.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OllamaToolParametersDto {

    private String type;
    private Map<String, OllamaToolSchemaPropertyDto> properties;

    @Builder.Default
    private List<OllamaToolParametersRequirementDto> oneOf = new ArrayList<>();
}
