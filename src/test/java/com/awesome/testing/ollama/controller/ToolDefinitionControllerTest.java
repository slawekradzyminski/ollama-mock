package com.awesome.testing.ollama.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.awesome.testing.ollama.dto.OllamaToolDefinitionDto;
import com.awesome.testing.ollama.service.ToolDefinitionCatalog;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(controllers = ToolDefinitionController.class)
class ToolDefinitionControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ToolDefinitionCatalog catalog;

    @Test
    void shouldExposeToolDefinitions() {
        Mockito.when(catalog.getDefinitions()).thenReturn(List.of(
                OllamaToolDefinitionDto.builder()
                        .function(new com.awesome.testing.ollama.dto.OllamaToolFunctionDto())
                        .build()
        ));

        webTestClient.get()
                .uri("/api/chat/tools/definitions")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(OllamaToolDefinitionDto.class)
                .value(list -> assertThat(list).hasSize(1));
    }
}
