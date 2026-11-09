package roomwise.exception;
/** Signals that a selected record was removed or does not exist. */
public class NotFoundException extends IllegalArgumentException {
    /** Identifies the missing entity. */
    public NotFoundException(String message) { super(message); }
}
