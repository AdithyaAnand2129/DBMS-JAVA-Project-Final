package roomwise.model;
import java.time.*;
import java.util.Objects;
/** A one-date reservation inherits common scheduling state and validation. */
public final class Booking extends ScheduledEvent {
    private final LocalDate date;
    /** Constructs a validated booking. */
    public Booking(int id, int roomId, String teacher, LocalDate date, LocalTime start, LocalTime end) {
        super(id,roomId,teacher,start,end); this.date=Objects.requireNonNull(date,"Booking date is required");
    }
    /** Returns the exact reservation date. */
    public LocalDate date() { return date; }
    @Override public boolean occursOn(LocalDate day) { return date.equals(day); }
    @Override public String description() { return "Booking | " + teacher(); }
}
