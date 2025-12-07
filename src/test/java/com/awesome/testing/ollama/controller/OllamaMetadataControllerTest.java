package com.awesome.testing.ollama.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(controllers = OllamaMetadataController.class)
@TestPropertySource(properties = {
        "ollama.mock.version=0.0.1-test",
        "ollama.mock.default-model=test-model"
})
class OllamaMetadataControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void shouldExposeVersionMetadata() {
        webTestClient.get()
                .uri("/api/version")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.version").isEqualTo("0.0.1-test")
                .jsonPath("$.mockModel").isEqualTo("test-model")
                .jsonPath("$.timestamp").exists();
    }
}
