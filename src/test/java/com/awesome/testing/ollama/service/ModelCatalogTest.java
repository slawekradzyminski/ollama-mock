package com.awesome.testing.ollama.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.awesome.testing.ollama.config.OllamaMockProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelCatalogTest {

    private final OllamaMockProperties properties = properties();

    private static OllamaMockProperties properties() {
        OllamaMockProperties properties = new OllamaMockProperties();
        properties.setDefaultModel("qwen3.5:2b");
        properties.setModels(List.of("hf.co/prism-ml/Bonsai-27B-gguf:Q1_0", "tiny", "registry.local:5000/team/small:latest"));
        return properties;
    }

    @Test
    void blankModelResolvesToTheDefault() {
        assertThat(ModelCatalog.resolve(properties, null)).isEqualTo("qwen3.5:2b");
        assertThat(ModelCatalog.resolve(properties, "  ")).isEqualTo("qwen3.5:2b");
    }

    @Test
    void servedModelsAreEchoedAsRequested() {
        assertThat(ModelCatalog.resolve(properties, "qwen3.5:2b")).isEqualTo("qwen3.5:2b");
        assertThat(ModelCatalog.resolve(properties, "HF.co/prism-ml/Bonsai-27B-gguf:Q1_0"))
                .isEqualTo("HF.co/prism-ml/Bonsai-27B-gguf:Q1_0");
    }

    @Test
    void aNameWithoutTagMeansLatest() {
        assertThat(ModelCatalog.resolve(properties, "tiny:latest")).isEqualTo("tiny:latest");
        assertThat(ModelCatalog.resolve(properties, "tiny")).isEqualTo("tiny");
        assertThat(ModelCatalog.resolve(properties, "registry.local:5000/team/small"))
                .isEqualTo("registry.local:5000/team/small");
        assertThatThrownBy(() -> ModelCatalog.resolve(properties, "qwen3.5"))
                .isInstanceOf(ModelNotFoundException.class);
        assertThatThrownBy(() -> ModelCatalog.resolve(properties, "hf.co/prism-ml/Bonsai-27B-gguf"))
                .isInstanceOf(ModelNotFoundException.class);
    }

    @Test
    void unknownModelIsNotFound() {
        assertThatThrownBy(() -> ModelCatalog.resolve(properties, "no-such-model:1b"))
                .isInstanceOf(ModelNotFoundException.class)
                .hasMessage("model 'no-such-model:1b' not found")
                .extracting(e -> ((ModelNotFoundException) e).getModel())
                .isEqualTo("no-such-model:1b");
    }
}
