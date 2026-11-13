package com.awesome.testing.ollama.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
    private String defaultModel = "qwen3.5:2b";

    /**
     * Further model names the mock serves besides the default; any other model is answered with 404.
     */
    private List<String> models = new ArrayList<>();

    /**
     * Delay between streamed tokens to mimic incremental decoding.
     */
    private Duration tokenDelay = Duration.ofMillis(150);

    /**
     * Delay before emitting a tool call chunk to simulate function-calling latency.
     */
    private Duration toolCallDelay = Duration.ofSeconds(1);
}
