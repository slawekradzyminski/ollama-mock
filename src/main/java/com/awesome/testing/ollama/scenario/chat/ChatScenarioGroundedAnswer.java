package com.awesome.testing.ollama.scenario.chat;

import java.math.BigDecimal;
import lombok.Data;

/**
 * A final answer composed from the tool results the client sent back, so the mock never names a
 * product the catalog did not return. {@code listIntro} precedes the listed products,
 * {@code emptyAnswer} replaces the list when no product matches, {@code maxPrice} keeps only
 * products at or below that price when the result carries prices (the backend's {@code list_products}
 * returns ids and names only, so the answer then says the prices are unconfirmed), and {@code closing}
 * is appended unchanged.
 */
@Data
public class ChatScenarioGroundedAnswer {

    private String listIntro;
    private String emptyAnswer;
    private BigDecimal maxPrice;
    private String closing;
}
