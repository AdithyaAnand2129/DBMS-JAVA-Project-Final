package roomwise.model;
import java.time.*;
import java.util.Objects;
import roomwise.util.Validation;
/** A weekly class shares scheduling behavior with Booking through inheritance. */
public final class TimetableSlot extends ScheduledEvent {
    /** Restricts a class to the database's supported types. */
    public enum Type { THEORY, LAB }
    private final DayOfWeek day;
    private final String courseCode, courseName, section;
    private final Type type;
    /** Constructs a recurring class with validated column values. */
    public TimetableSlot(int id,int roomId,String teacher,DayOfWeek day,LocalTime start,LocalTime end,
                         String courseCode,String courseName,String section,Type type) {
        super(id,roomId,teacher,start,end); this.day=Objects.requireNonNull(day);
        this.courseCode=Validation.text(courseCode,40,"Course code");
        this.courseName=Validation.text(courseName,180,"Course name");
        this.section=Validation.text(section,180,"Section"); this.type=Objects.requireNonNull(type);
    }
    /** Returns the recurring weekday. */ public DayOfWeek day() { return day; }
    /** Returns the source course code. */ public String courseCode() { return courseCode; }
    /** Returns the course name. */ public String courseName() { return courseName; }
    /** Returns the class group. */ public String section() { return section; }
    /** Returns THEORY or LAB. */ public Type type() { return type; }
    @Override public boolean occursOn(LocalDate date) { return date.getDayOfWeek()==day; }
    @Override public String description() { return type + " | " + courseCode + " " + courseName + " | " + teacher(); }
}
