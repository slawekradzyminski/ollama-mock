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
public class ChatRequestDto {

    private String model;
    @Builder.Default
    private List<ChatMessageDto> messages = new ArrayList<>();
    private Map<String, Object> options;

    @Builder.Default
    private List<OllamaToolDefinitionDto> tools = new ArrayList<>();

    @Builder.Default
    private Boolean stream = true;

    @Builder.Default
    private Boolean think = false;
}
