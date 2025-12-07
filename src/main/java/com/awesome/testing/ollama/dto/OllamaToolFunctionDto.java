package com.awesome.testing.ollama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OllamaToolFunctionDto {

    private String name;
    private String description;
    private OllamaToolParametersDto parameters;
}
