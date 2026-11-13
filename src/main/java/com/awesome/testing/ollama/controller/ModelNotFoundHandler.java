package com.awesome.testing.ollama.controller;

import com.awesome.testing.ollama.service.ModelNotFoundException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Real Ollama answers an unknown model with 404 and an {@code error} message before any stream starts. */
@RestControllerAdvice
public class ModelNotFoundHandler {

    @ExceptionHandler(ModelNotFoundException.class)
    public ResponseEntity<Map<String, String>> modelNotFound(ModelNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("error", exception.getMessage()));
    }
}
