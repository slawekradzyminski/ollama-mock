package com.awesome.testing.ollama.util;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.springframework.util.StringUtils;

@UtilityClass
public class TokenStreamUtils {

    public List<String> tokenize(String text) {
        if (!StringUtils.hasLength(text)) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        for (char ch : text.toCharArray()) {
            if (Character.isWhitespace(ch)) {
                if (buffer.length() > 0) {
                    tokens.add(buffer.toString());
                    buffer.setLength(0);
                }
                tokens.add(String.valueOf(ch));
            } else {
                buffer.append(ch);
            }
        }
        if (buffer.length() > 0) {
            tokens.add(buffer.toString());
        }
        return tokens;
    }

    public String printable(String token) {
        if (token == null) {
            return "(null)";
        }
        return token.replace("\n", "\\n")
                .replace("\t", "\\t");
    }
}
