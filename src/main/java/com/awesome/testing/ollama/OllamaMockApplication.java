package com.awesome.testing.ollama;

import com.awesome.testing.ollama.config.OllamaMockProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(OllamaMockProperties.class)
public class OllamaMockApplication {

    public static void main(String[] args) {
        SpringApplication.run(OllamaMockApplication.class, args);
    }

}
