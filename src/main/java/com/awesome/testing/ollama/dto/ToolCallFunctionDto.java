package com.awesome.testing.ollama.dto;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallFunctionDto {

    private String name;
    private Map<String, Object> arguments;
}
