package com.awesome.testing.ollama.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDto {

    private String role;
    private String content;
    private String thinking;

    @JsonProperty("tool_calls")
    @Builder.Default
    private List<ToolCallDto> toolCalls = new ArrayList<>();

    @JsonProperty("tool_name")
    private String toolName;
}
