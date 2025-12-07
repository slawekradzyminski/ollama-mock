package com.awesome.testing.ollama.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;

@RestController
@RequestMapping(path = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class OllamaMetadataController {

    private final String version;
    private final String mockModel;

    public OllamaMetadataController(
            @Value("${ollama.mock.version:0.0.1-local}") String version,
            @Value("${ollama.mock.default-model:gpt-4o-mini}") String mockModel) {
        this.version = version;
        this.mockModel = mockModel;
    }

    @GetMapping("/version")
    public Map<String, Object> version() {
        return Map.of(
                "version", version,
                "mockModel", mockModel,
                "timestamp", OffsetDateTime.now().toString()
        );
    }
}
