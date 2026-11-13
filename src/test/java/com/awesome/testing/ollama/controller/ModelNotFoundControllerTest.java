package com.awesome.testing.ollama.controller;

import com.awesome.testing.ollama.config.OllamaMockProperties;
import com.awesome.testing.ollama.scenario.chat.ChatScenarioRepository;
import com.awesome.testing.ollama.scenario.chatbasic.ChatDialogueScenarioRepository;
import com.awesome.testing.ollama.scenario.generate.GenerateScenarioRepository;
import com.awesome.testing.ollama.service.ChatService;
import com.awesome.testing.ollama.service.ChatToolsService;
import com.awesome.testing.ollama.service.GenerateService;
import com.awesome.testing.ollama.service.ToolAnswerComposer;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/** Unknown models get Ollama's 404 on every model endpoint, before any stream starts. */
@WebFluxTest(controllers = {OllamaGenerateController.class, OllamaChatController.class, OllamaChatToolsController.class})
@Import({ModelNotFoundControllerTest.TestConfig.class, GenerateService.class, ChatService.class, ChatToolsService.class,
        ToolAnswerComposer.class, GenerateScenarioRepository.class, ChatDialogueScenarioRepository.class,
        ChatScenarioRepository.class})
class ModelNotFoundControllerTest {

    private static final String UNKNOWN = "no-such-model:1b";
    private static final String TOOLS = "[{\"type\":\"function\",\"function\":{\"name\":\"list_products\"}}]";

    @Autowired
    private WebTestClient webTestClient;

    @ParameterizedTest
    @ValueSource(strings = {"true", "false"})
    void generateRejectsUnknownModel(String stream) {
        post("/api/generate", "{\"model\":\"%s\",\"prompt\":\"Provide a motivational quote\",\"stream\":%s}"
                .formatted(UNKNOWN, stream));
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "false"})
    void chatRejectsUnknownModel(String stream) {
        post("/api/chat", "{\"model\":\"%s\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"stream\":%s}"
                .formatted(UNKNOWN, stream));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/chat", "/api/chat/tools", "/chat/tools"})
    void toolChatRejectsUnknownModel(String path) {
        post(path, "{\"model\":\"%s\",\"messages\":[{\"role\":\"user\",\"content\":\"What Beauty products do we have available?\"}],\"tools\":%s}"
                .formatted(UNKNOWN, TOOLS));
    }

    @ParameterizedTest
    @ValueSource(strings = {"qwen3.5:2b", "hf.co/prism-ml/Bonsai-27B-gguf:Q1_0"})
    void servedModelsStillAnswer(String model) {
        webTestClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"model\":\"%s\",\"prompt\":\"Provide a motivational quote\",\"stream\":false}".formatted(model))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.model").isEqualTo(model)
                .jsonPath("$.done").isEqualTo(true);
    }

    private void post(String path, String body) {
        webTestClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .json("{\"error\":\"model 'no-such-model:1b' not found\"}", true);
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        OllamaMockProperties mockProperties() {
            OllamaMockProperties properties = new OllamaMockProperties();
            properties.setDefaultModel("qwen3.5:2b");
            properties.setModels(List.of("hf.co/prism-ml/Bonsai-27B-gguf:Q1_0"));
            properties.setTokenDelay(Duration.ZERO);
            properties.setToolCallDelay(Duration.ZERO);
            return properties;
        }
    }
}
