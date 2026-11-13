package com.awesome.testing.ollama.service;

import com.awesome.testing.ollama.config.OllamaMockProperties;
import java.util.Locale;
import java.util.stream.Stream;
import org.springframework.util.StringUtils;

/**
 * The models this mock pretends to have pulled: the default model plus {@code ollama.mock.models}.
 * Like real Ollama, a name without a tag means {@code :latest}, and any other model is not found.
 */
public final class ModelCatalog {

    private ModelCatalog() {
    }

    /** Returns the model a reply is attributed to, or throws {@link ModelNotFoundException}. */
    public static String resolve(OllamaMockProperties properties, String requestedModel) {
        if (!StringUtils.hasText(requestedModel)) {
            return properties.getDefaultModel();
        }
        String requested = withTag(requestedModel);
        boolean known = Stream.concat(Stream.of(properties.getDefaultModel()), properties.getModels().stream())
                .filter(StringUtils::hasText)
                .map(ModelCatalog::withTag)
                .anyMatch(requested::equals);
        if (!known) {
            throw new ModelNotFoundException(requestedModel);
        }
        return requestedModel;
    }

    private static String withTag(String model) {
        String trimmed = model.trim().toLowerCase(Locale.ROOT);
        int lastSlash = trimmed.lastIndexOf('/');
        return trimmed.indexOf(':', lastSlash + 1) >= 0 ? trimmed : trimmed + ":latest";
    }
}
