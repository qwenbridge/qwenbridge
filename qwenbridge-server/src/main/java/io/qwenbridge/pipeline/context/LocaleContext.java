package io.qwenbridge.pipeline.context;

import java.util.Locale;
import java.util.Objects;


public record LocaleContext(

        Locale locale,
        String country,
        String currency

) {

    public LocaleContext {

        Objects.requireNonNull(locale, "locale must not be null");
        Objects.requireNonNull(country, "country must not be null");
        Objects.requireNonNull(currency, "currency must not be null");

    }

}