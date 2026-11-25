package rs.project1k.plan;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.TreeSet;

/** Comparison shared by schema edits, all record entry paths and backup restore. */
final class UniqueValues {
    private UniqueValues() {}

    static void requireDistinct(Iterable<?> values, String fieldName) {
        Set<String> seen = new HashSet<>();
        for (Object value : values) {
            String key = key(value);
            if (key != null && !seen.add(key)) {
                throw new IllegalArgumentException("Vrednost već postoji u koloni „" + fieldName + "“.");
            }
        }
    }

    static void requireDistinctTuples(Iterable<? extends List<?>> tuples, String fieldNames) {
        Set<String> seen = new HashSet<>();
        for (List<?> tuple : tuples) {
            StringBuilder encoded = new StringBuilder();
            boolean complete = true;
            for (Object component : tuple) {
                String key = key(component);
                if (key == null) { complete = false; break; }
                encoded.append(key.length()).append(':').append(key);
            }
            // Incomplete combinations may repeat, like nullable composite SQL UNIQUE keys.
            if (complete && !seen.add(encoded.toString())) {
                throw new IllegalArgumentException("Već postoji ista kombinacija: " + fieldNames + ".");
            }
        }
    }

    static String key(Object value) {
        if (value == null) return null;
        if (value instanceof Collection<?>) {
            Collection<?> selection = (Collection<?>) value;
            if (selection.isEmpty()) return null;
            // Links and multiple choices represent a selection, regardless of its order.
            Set<String> members = new TreeSet<>();
            for (Object member : selection) {
                String key = key(member);
                if (key != null) members.add(key);
            }
            if (members.isEmpty()) return null;
            StringBuilder encoded = new StringBuilder("set:");
            for (String member : members) encoded.append(member.length()).append(':').append(member);
            return encoded.toString();
        }
        if (value instanceof Number) {
            // Keep long integer precision; decimal spellings such as 1 and 1.0 compare equally.
            return "number:" + new BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
        }
        if (value instanceof Boolean) return "boolean:" + value;
        String text = value.toString().trim();
        // Empty values may repeat. Required is an independent field setting.
        return text.isEmpty() ? null : "text:" + text;
    }
}
