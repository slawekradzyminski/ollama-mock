package com.awesome.testing.ollama.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Collections;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamedRequestDto {

    @NotBlank
    private String model;

    @NotBlank
    private String prompt;

    @Builder.Default
    private Map<String, Object> options = Collections.emptyMap();

    @Builder.Default
    private Boolean think = false;

    @Builder.Default
    private Boolean stream = true;
}
