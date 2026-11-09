package roomwise;
import java.time.*;
import org.junit.jupiter.api.Test;
import roomwise.model.*;
import static org.junit.jupiter.api.Assertions.*;

class ModelTest {
    private final LocalDate monday=LocalDate.of(2026,11,9);
    private Booking booking() { return new Booking(0,1,"Demo Teacher",monday,LocalTime.of(9,0),LocalTime.of(10,0)); }
    @Test void roomEncapsulationValidatesUpdates() {
        Room room=new Room(1," A101 "); assertEquals("A101",room.name());
        assertThrows(IllegalArgumentException.class,() -> room.setName(" "));
        assertEquals("A101",room.name()); room.setName("A102"); assertEquals("A102",room.name());
    }
    @Test void halfOpenOverlapAllowsAdjacency() {
        assertFalse(booking().overlaps(LocalTime.of(10,0),LocalTime.of(11,0)));
        assertFalse(booking().overlaps(LocalTime.of(8,0),LocalTime.of(9,0)));
        assertTrue(booking().overlaps(LocalTime.of(9,30),LocalTime.of(10,30)));
    }
    @Test void invalidIntervalsAndTextAreRejected() {
        assertThrows(IllegalArgumentException.class,() -> new Booking(0,1,"T",monday,LocalTime.NOON,LocalTime.NOON));
        assertThrows(IllegalArgumentException.class,() -> new Booking(0,1," ",monday,LocalTime.of(9,0),LocalTime.of(10,0)));
        assertThrows(IllegalArgumentException.class,() -> new Room(0,"x".repeat(41)));
    }
    @Test void superclassReferencesDispatchByRuntimeType() {
        ScheduledEvent dated=booking();
        ScheduledEvent weekly=new TimetableSlot(0,1,"T",DayOfWeek.MONDAY,LocalTime.of(9,0),LocalTime.of(10,0),"CS","OOP","S3",TimetableSlot.Type.THEORY);
        assertTrue(dated.occursOn(monday)); assertFalse(dated.occursOn(monday.plusWeeks(1)));
        assertTrue(weekly.occursOn(monday.plusWeeks(1))); assertTrue(dated.description().startsWith("Booking"));
        assertTrue(weekly.description().startsWith("THEORY"));
    }
}
