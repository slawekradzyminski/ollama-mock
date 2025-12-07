package com.awesome.testing.ollama.controller;

import com.awesome.testing.ollama.config.OllamaMockProperties;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;

@RestController
@RequestMapping(path = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class OllamaMetadataController {

    private final OllamaMockProperties properties;

    public OllamaMetadataController(OllamaMockProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/version")
    public Map<String, Object> version() {
        return Map.of(
                "version", properties.getVersion(),
                "mockModel", properties.getDefaultModel(),
                "timestamp", OffsetDateTime.now().toString()
        );
    }
}
