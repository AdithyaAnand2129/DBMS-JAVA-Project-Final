package roomwise.exception;
/** Reports a room or teacher schedule collision; also used for timetable edits. */
public class BookingConflictException extends IllegalArgumentException {
    /** Creates an actionable business-rule error. */
    public BookingConflictException(String message) { super(message); }
}
