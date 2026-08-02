package com.awesome.testing.ollama.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenStreamUtilsTest {

    @Test
    void shouldReturnNoTokensForMissingText() {
        assertThat(TokenStreamUtils.tokenize(null)).isEmpty();
        assertThat(TokenStreamUtils.tokenize("")).isEmpty();
    }

    @Test
    void shouldPreserveEveryWhitespaceCharacterAsAStreamingToken() {
        assertThat(TokenStreamUtils.tokenize("alpha  beta\n"))
                .containsExactly("alpha", " ", " ", "beta", "\n");
    }

    @Test
    void shouldFlushTheFinalWordWithoutTrailingWhitespace() {
        assertThat(TokenStreamUtils.tokenize("alpha beta"))
                .containsExactly("alpha", " ", "beta");
    }

    @Test
    void shouldMakeControlCharactersAndNullPrintableForLogs() {
        assertThat(TokenStreamUtils.printable(null)).isEqualTo("(null)");
        assertThat(TokenStreamUtils.printable("line one\n\tline two"))
                .isEqualTo("line one\\n\\tline two");
    }
}
