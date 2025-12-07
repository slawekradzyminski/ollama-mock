package com.awesome.testing.ollama.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponseDto {
    private String model;

    @JsonProperty("created_at")
    private String createdAt;

    private ChatMessageDto message;
    private boolean done;
}
