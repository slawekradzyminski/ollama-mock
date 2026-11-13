package com.awesome.testing.ollama.service;

/** A request named a model the mock does not serve; answered like Ollama: 404 {"error":"model 'x' not found"}. */
public class ModelNotFoundException extends RuntimeException {

    private final String model;

    public ModelNotFoundException(String model) {
        super("model '%s' not found".formatted(model));
        this.model = model;
    }

    public String getModel() {
        return model;
    }
}
