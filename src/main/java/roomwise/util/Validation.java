package roomwise.util;
import java.time.LocalTime;
/** Shared validation for model constructors and application input. */
public final class Validation {
    private Validation() {}
    /** Returns trimmed nonblank text within a schema column's limit. */
    public static String text(String value, int max, String label) {
        if (value == null || value.isBlank() || value.trim().length() > max)
            throw new IllegalArgumentException(label + " must contain 1 to " + max + " characters.");
        return value.trim();
    }
    /** Validates a same-day, positive-length interval. */
    public static void times(LocalTime start, LocalTime end) {
        if (start == null || end == null || !end.isAfter(start))
            throw new IllegalArgumentException("End time must be after start time on the same day.");
        if (start.getNano() != 0 || end.getNano() != 0)
            throw new IllegalArgumentException("Use whole-second time values.");
    }
}
