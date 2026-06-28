package io.qwenbridge.normalization.rule;

import org.springframework.stereotype.Component;

@Component
public class HtmlEntityNormalizer implements InputNormalizationRule {

    @Override
    public String name() {
        return "html-entity";
    }

    @Override
    public String normalize(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }

        return input
                .replace("&lt;", "<")
                .replace("&LT;", "<")
                .replace("&#60;", "<")
                .replace("&#x3c;", "<")
                .replace("&#X3C;", "<")
                .replace("&gt;", ">")
                .replace("&GT;", ">")
                .replace("&#62;", ">")
                .replace("&#x3e;", ">")
                .replace("&#X3E;", ">")
                .replace("&quot;", "\"")
                .replace("&QUOT;", "\"")
                .replace("&#34;", "\"")
                .replace("&apos;", "'")
                .replace("&#39;", "'")
                .replace("&#x27;", "'")
                .replace("&#X27;", "'")
                .replace("&amp;", "&")
                .replace("&AMP;", "&");
    }
}
