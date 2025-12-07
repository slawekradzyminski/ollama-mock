package com.awesome.testing.ollama.controller;

import com.awesome.testing.ollama.dto.OllamaToolDefinitionDto;
import com.awesome.testing.ollama.service.ToolDefinitionCatalog;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/chat/tools", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ToolDefinitionController {

    private final ToolDefinitionCatalog catalog;

    @GetMapping("/definitions")
    public List<OllamaToolDefinitionDto> definitions() {
        return catalog.getDefinitions();
    }
}
