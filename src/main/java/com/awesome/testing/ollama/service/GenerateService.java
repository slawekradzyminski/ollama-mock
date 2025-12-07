package com.awesome.testing.ollama.service;

import com.awesome.testing.ollama.config.OllamaMockProperties;
import com.awesome.testing.ollama.dto.GenerateResponseDto;
import com.awesome.testing.ollama.dto.StreamedRequestDto;
import com.awesome.testing.ollama.scenario.generate.GenerateScenarioDefinition;
import com.awesome.testing.ollama.scenario.generate.GenerateScenarioRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class GenerateService {

    private final OllamaMockProperties properties;
    private final GenerateScenarioRepository scenarioRepository;

    public Flux<GenerateResponseDto> generateStream(StreamedRequestDto request) {
        String model = resolveModel(request.getModel());
        boolean thinkingEnabled = Boolean.TRUE.equals(request.getThink());
        List<GenerateResponseDto> chunks = scenarioRepository.findByPrompt(request.getPrompt())
                .map(scenario -> buildScenarioChunks(model, scenario, thinkingEnabled))
                .orElseGet(() -> List.of(unsupportedPromptChunk(model, false)));
        return Flux.fromIterable(chunks)
                .concatWithValues(doneChunk(model));
    }

    public Mono<GenerateResponseDto> generateSingle(StreamedRequestDto request) {
        String model = resolveModel(request.getModel());
        return scenarioRepository.findByPrompt(request.getPrompt())
                .map(scenario -> Mono.just(selectSingleChunk(model, scenario)))
                .orElseGet(() -> Mono.just(unsupportedPromptChunk(model, true)));
    }

    private String resolveModel(String requestedModel) {
        if (StringUtils.hasText(requestedModel)) {
            return requestedModel;
        }
        return properties.getDefaultModel();
    }

    private List<GenerateResponseDto> buildScenarioChunks(String model,
                                                          GenerateScenarioDefinition scenario,
                                                          boolean thinkingEnabled) {
        List<GenerateResponseDto> outputs = new ArrayList<>();
        scenario.getChunks().forEach(chunk -> {
            if (thinkingEnabled && StringUtils.hasText(chunk.getThinking())) {
                outputs.add(thinkingChunk(model, chunk.getThinking()));
            }
            if (StringUtils.hasText(chunk.getResponse())) {
                outputs.add(responseChunk(model, chunk.getResponse(), false));
            }
        });
        return outputs;
    }

    private GenerateResponseDto selectSingleChunk(String model, GenerateScenarioDefinition scenario) {
        List<com.awesome.testing.ollama.scenario.generate.GenerateScenarioChunkDefinition> chunks =
                new ArrayList<>(scenario.getChunks());
        Collections.reverse(chunks);
        for (var chunk : chunks) {
            if (StringUtils.hasText(chunk.getResponse())) {
                return responseChunk(model, chunk.getResponse(), true);
            }
        }
        return responseChunk(
                model,
                "This scenario does not have a final response configured yet.",
                true);
    }

    private GenerateResponseDto unsupportedPromptChunk(String model, boolean done) {
        String message = formatSupportedPromptMessage("Sorry, only these prompts are supported for this endpoint:");
        return responseChunk(model, message, done);
    }

    private String formatSupportedPromptMessage(String prefix) {
        List<String> prompts = scenarioRepository.supportedPrompts();
        if (prompts.isEmpty()) {
            return prefix + " (no scenarios configured)";
        }
        String joined = prompts.stream()
                .map(entry -> "- " + entry)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        return prefix + "\n" + joined;
    }

    private GenerateResponseDto thinkingChunk(String model, String thought) {
        return GenerateResponseDto.builder()
                .model(model)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC).toString())
                .thinking(thought)
                .done(false)
                .build();
    }

    private GenerateResponseDto responseChunk(String model, String content, boolean done) {
        return GenerateResponseDto.builder()
                .model(model)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC).toString())
                .response(content)
                .done(done)
                .build();
    }

    private GenerateResponseDto doneChunk(String model) {
        return GenerateResponseDto.builder()
                .model(model)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC).toString())
                .done(true)
                .build();
    }
}
