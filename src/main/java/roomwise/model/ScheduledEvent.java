package roomwise.model;
import java.time.*;
import roomwise.util.Validation;
/** Abstraction shared by a dated Booking and a recurring TimetableSlot. */
public abstract class ScheduledEvent {
    private final int id, roomId;
    private final String teacher;
    private final LocalTime start, end;
    /** Establishes invariant identity, teacher and same-day time fields. */
    protected ScheduledEvent(int id, int roomId, String teacher, LocalTime start, LocalTime end) {
        if (id < 0 || roomId <= 0) throw new IllegalArgumentException("Invalid record or room ID.");
        Validation.times(start, end);
        this.id=id; this.roomId=roomId; this.teacher=Validation.text(teacher,150,"Teacher");
        this.start=start; this.end=end;
    }
    /** Returns the persistent identity, or zero for a new event. */
    public final int id() { return id; }
    /** Returns the rooms foreign key. */
    public final int roomId() { return roomId; }
    /** Returns the teacher's display name. */
    public final String teacher() { return teacher; }
    /** Returns the inclusive interval start. */
    public final LocalTime start() { return start; }
    /** Returns the exclusive interval end. */
    public final LocalTime end() { return end; }
    /** Uses half-open intervals, so adjacent events do not collide. */
    public final boolean overlaps(LocalTime from, LocalTime until) {
        return start.isBefore(until) && end.isAfter(from);
    }
    /** Subclasses implement dated versus recurring occurrence rules. */
    public abstract boolean occursOn(LocalDate date);
    /** Runtime polymorphism supplies the display label of each event. */
    public abstract String description();
}
