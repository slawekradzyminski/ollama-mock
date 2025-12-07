package com.awesome.testing.ollama.scenario.chat;

import java.util.Locale;

public enum ChatScenarioTrigger {
    USER,
    TOOL,
    UNKNOWN;

    public static ChatScenarioTrigger from(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "user" -> USER;
            case "tool" -> TOOL;
            default -> UNKNOWN;
        };
    }
}
