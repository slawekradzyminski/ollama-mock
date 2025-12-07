package com.awesome.testing.ollama.controller;

import com.awesome.testing.ollama.config.OllamaMockProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(controllers = OllamaMetadataController.class)
@Import(OllamaMetadataControllerTest.TestConfig.class)
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

    @TestConfiguration
    static class TestConfig {
        @Bean
        OllamaMockProperties mockProperties() {
            OllamaMockProperties props = new OllamaMockProperties();
            props.setVersion("0.0.1-test");
            props.setDefaultModel("test-model");
            return props;
        }
    }
}
