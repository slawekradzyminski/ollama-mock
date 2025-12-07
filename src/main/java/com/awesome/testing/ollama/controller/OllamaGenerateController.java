package com.awesome.testing.ollama.controller;

import com.awesome.testing.ollama.dto.GenerateResponseDto;
import com.awesome.testing.ollama.dto.StreamedRequestDto;
import com.awesome.testing.ollama.service.GenerateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.reactivestreams.Publisher;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OllamaGenerateController {

    private final GenerateService generateService;

    @PostMapping(value = "/generate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Publisher<GenerateResponseDto>> generate(@Valid @RequestBody StreamedRequestDto request) {
        boolean streamingEnabled = request.getStream() == null || request.getStream();
        if (streamingEnabled) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_NDJSON)
                    .body(generateService.generateStream(request));
        }
        Mono<GenerateResponseDto> single = generateService.generateSingle(request);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(single);
    }
}
