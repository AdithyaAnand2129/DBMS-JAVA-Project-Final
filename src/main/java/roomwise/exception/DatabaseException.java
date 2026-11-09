package roomwise.exception;
/** Wraps database failures without exposing connection secrets to the user. */
public class DatabaseException extends RuntimeException {
    /** Retains the original cause for debugging. */
    public DatabaseException(String message, Throwable cause) { super(message, cause); }
}
