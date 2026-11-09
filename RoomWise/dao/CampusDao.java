package roomwise.dao;
import java.util.List;
import roomwise.model.*;
/** Persistence abstraction: service/UI code does not depend on JDBC details. */
public interface CampusDao {
    /** Reads all rooms. */ List<Room> rooms();
    /** Reads all recurring timetable rows. */ List<TimetableSlot> slots();
    /** Reads all dated bookings. */ List<Booking> bookings();
    /** Creates a room and returns its generated primary key. */ int createRoom(Room room);
    /** Updates a named room, rejecting missing IDs. */ void updateRoom(Room room);
    /** Deletes an unused room; dependent schedules prevent deletion. */ void deleteRoom(int id);
    /** Creates a conflict-checked booking. */ int createBooking(Booking booking);
    /** Updates a booking atomically while excluding its old row from checks. */ void updateBooking(Booking booking);
    /** Deletes a booking by primary key. */ void deleteBooking(int id);
    /** Creates a conflict-checked weekly class. */ int createSlot(TimetableSlot slot);
    /** Updates a weekly class with room and teacher checks. */ void updateSlot(TimetableSlot slot);
    /** Deletes a weekly class. */ void deleteSlot(int id);
}
