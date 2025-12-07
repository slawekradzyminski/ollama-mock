package com.awesome.testing.ollama.controller;

import com.awesome.testing.ollama.dto.ChatRequestDto;
import com.awesome.testing.ollama.dto.ChatResponseDto;
import com.awesome.testing.ollama.service.ChatService;
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
public class OllamaChatController {

    private final ChatService chatService;

    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Publisher<ChatResponseDto>> chat(@Valid @RequestBody ChatRequestDto request) {
        boolean streamingEnabled = request.getStream() == null || request.getStream();
        if (streamingEnabled) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_NDJSON)
                    .body(chatService.chatStream(request));
        }
        Mono<ChatResponseDto> single = chatService.chatSingle(request);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(single);
    }
}
