package com.awesome.testing.ollama.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ollama.mock")
public class OllamaMockProperties {

    /**
     * Version string exposed on /api/version.
     */
    private String version = "0.0.1-local";

    /**
     * Default model name when a request does not specify one.
     */
    private String defaultModel = "gpt-4o-mini";
}
