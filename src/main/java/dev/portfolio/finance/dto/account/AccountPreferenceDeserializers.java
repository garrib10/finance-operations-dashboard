package dev.portfolio.finance.dto.account;

import dev.portfolio.finance.entity.DateFormatPreference;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** Reject scalar coercion locally without changing other API request contracts. */
public final class AccountPreferenceDeserializers {
    private AccountPreferenceDeserializers() {
    }

    public static final class DateFormat extends ValueDeserializer<DateFormatPreference> {
        @Override
        public DateFormatPreference deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.hasToken(JsonToken.VALUE_STRING)) {
                for (DateFormatPreference format : DateFormatPreference.values()) {
                    if (format.name().equals(parser.getString())) {
                        return format;
                    }
                }
            }
            return context.reportInputMismatch(DateFormatPreference.class, "Date format must be MEDIUM or ISO");
        }
    }

    public static final class PageSize extends ValueDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                var value = parser.getBigIntegerValue();
                if (value.equals(java.math.BigInteger.TEN)
                        || value.equals(java.math.BigInteger.valueOf(25))
                        || value.equals(java.math.BigInteger.valueOf(50))) {
                    return value.intValue();
                }
            }
            return context.reportInputMismatch(Integer.class, "Transaction page size must be 10, 25, or 50");
        }
    }
}
