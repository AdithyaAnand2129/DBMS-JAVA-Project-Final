package roomwise.service;
import java.time.*;
import java.util.*;
import roomwise.dao.CampusDao;
import roomwise.model.*;
import roomwise.exception.NotFoundException;
import roomwise.util.Validation;
/** Business facade: UI depends on this class, while persistence uses an interface. */
public final class CampusService {
    private final CampusDao dao;
    /** Constructor injection makes the service independently testable. */
    public CampusService(CampusDao dao) { this.dao=Objects.requireNonNull(dao); }
    /** Lists room objects mapped from MySQL. */ public List<Room> rooms() { return dao.rooms(); }
    /** Lists recurring classes. */ public List<TimetableSlot> slots() { return dao.slots(); }
    /** Lists dated reservations. */ public List<Booking> bookings() { return dao.bookings(); }
    /** Resolves a room or raises a domain exception. */
    public Room room(int id) {
        for (Room room : rooms()) {
            if (room.id() == id) return room;
        }
        throw new NotFoundException("Room not found.");
    }
    /** Creates a validated room. */ public int createRoom(String name) { return dao.createRoom(new Room(0,name)); }
    /** Updates a room through its encapsulated model. */ public void updateRoom(int id,String name) { dao.updateRoom(new Room(id,name)); }
    /** Deletes only an unused room. */ public void deleteRoom(int id) { dao.deleteRoom(id); }
    /** Creates a booking atomically. */ public int createBooking(Booking b) { if(b.id()!=0) throw new IllegalArgumentException("New booking ID must be zero."); return dao.createBooking(b); }
    /** Reschedules an existing booking. */ public void updateBooking(Booking b) { dao.updateBooking(b); }
    /** Cancels a booking by deleting its row. */ public void deleteBooking(int id) { dao.deleteBooking(id); }
    /** Creates a recurring class. */ public int createSlot(TimetableSlot t) { if(t.id()!=0) throw new IllegalArgumentException("New slot ID must be zero."); return dao.createSlot(t); }
    /** Changes a recurring class. */ public void updateSlot(TimetableSlot t) { dao.updateSlot(t); }
    /** Removes a recurring class. */ public void deleteSlot(int id) { dao.deleteSlot(id); }
    private List<ScheduledEvent> events(LocalDate date) {
        List<ScheduledEvent> all = new ArrayList<>(dao.slots());
        all.addAll(dao.bookings());
        List<ScheduledEvent> matching = new ArrayList<>();
        for (ScheduledEvent event : all) {
            // Runtime polymorphism selects the subclass date rule.
            if (event.occursOn(date)) matching.add(event);
        }
        return matching;
    }

    /** Overload one: search at the current minute on the given date. */
    public List<FreeRoom> search(LocalDate date) {
        return search(date, LocalTime.now().withSecond(0).withNano(0));
    }

    /** Overload two: find free rooms and their next scheduled start. */
    public List<FreeRoom> search(LocalDate date, LocalTime at) {
        List<ScheduledEvent> events = events(date);
        List<FreeRoom> free = new ArrayList<>();
        for (Room room : rooms()) {
            boolean busy = false;
            LocalTime next = LocalTime.of(23, 59, 59);
            for (ScheduledEvent event : events) {
                if (event.roomId() != room.id()) continue;
                if (activeAt(event, at)) busy = true;
                if (event.start().isAfter(at) && event.start().isBefore(next)) {
                    next = event.start();
                }
            }
            if (!busy) free.add(new FreeRoom(room, at, next));
        }
        return free;
    }

    /** Overload three: rooms available throughout a requested interval. */
    public List<Room> search(LocalDate date, LocalTime from, LocalTime until) {
        Validation.times(from, until);
        List<ScheduledEvent> busyEvents = occupied(date, from, until);
        List<Room> free = new ArrayList<>();
        for (Room room : rooms()) {
            boolean busy = false;
            for (ScheduledEvent event : busyEvents) {
                if (event.roomId() == room.id()) {
                    busy = true;
                    break;
                }
            }
            if (!busy) free.add(room);
        }
        return free;
    }

    /** Reads classes and bookings active at one time. */
    public List<ScheduledEvent> occupied(LocalDate date, LocalTime at) {
        List<ScheduledEvent> result = new ArrayList<>();
        for (ScheduledEvent event : events(date)) {
            if (activeAt(event, at)) result.add(event);
        }
        return result;
    }

    /** Reads classes and bookings overlapping an interval. */
    public List<ScheduledEvent> occupied(LocalDate date, LocalTime from, LocalTime until) {
        Validation.times(from, until);
        List<ScheduledEvent> result = new ArrayList<>();
        for (ScheduledEvent event : events(date)) {
            if (event.overlaps(from, until)) result.add(event);
        }
        return result;
    }

    private boolean activeAt(ScheduledEvent event, LocalTime at) {
        return !event.start().isAfter(at) && event.end().isAfter(at);
    }
}
