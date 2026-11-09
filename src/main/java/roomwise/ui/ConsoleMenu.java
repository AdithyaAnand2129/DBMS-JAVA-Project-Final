package roomwise.ui;

import java.io.PrintStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.Set;
import roomwise.model.*;
import roomwise.service.CampusService;

/** Terminal menus: read input, call the service, and print results. */
public final class ConsoleMenu {
    private final CampusService service;
    private final Scanner input;
    private final PrintStream out;
    private final DateTimeFormatter clock = DateTimeFormatter.ofPattern("H:mm[:ss]");

    /** Uses the keyboard and terminal for normal operation. */
    public ConsoleMenu(CampusService service) {
        this(service, new Scanner(System.in), System.out);
    }

    /** Accepts input/output streams for reproducible terminal demonstrations and tests. */
    public ConsoleMenu(CampusService service, Scanner input, PrintStream out) {
        this.service = service;
        this.input = input;
        this.out = out;
    }

    /** Repeats the main menu until Exit or end of input. */
    public void start() {
        while (true) {
            printMenu();
            if (!input.hasNextLine()) return;
            try {
                switch (read("Choose: ")) {
                    case "0": out.println("Goodbye."); return;
                    case "1": showFreeRooms(); break;
                    case "2": showOccupied(); break;
                    case "3": rooms(); break;
                    case "4": bookings(); break;
                    case "5": timetable(); break;
                    case "6": showFreeInterval(); break;
                    default: out.println("Use a number from 0 to 6.");
                }
            } catch (NoSuchElementException endOfInput) {
                out.println("Input ended. Returning to the terminal.");
                return;
            } catch (RuntimeException error) {
                out.println("Action failed: " + error.getMessage());
            }
        }
    }

    private void printMenu() {
        out.println("\n========== ROOMWISE ==========");
        out.println("1. Find free rooms at a time");
        out.println("2. View occupied events");
        out.println("3. Manage rooms");
        out.println("4. Manage bookings");
        out.println("5. Manage timetable");
        out.println("6. Find free rooms for an interval");
        out.println("0. Exit");
    }

    private void showFreeRooms() {
        LocalDateTime selected = readDateTime("Time");
        LocalDate date = selected.toLocalDate();
        LocalTime at = selected.toLocalTime();
        List<FreeRoom> free = service.search(date, at);
        out.println("\nFREE ROOMS | " + date + " | " + at);
        for (FreeRoom item : free) {
            out.printf("%-5d %-24s Free until %s%n", item.room().id(), item.room().name(), item.freeUntil());
        }
        out.println("Total free rooms: " + free.size());
    }

    private void showFreeInterval() {
        LocalDateTime selected = readDateTime("Start");
        LocalDate date = selected.toLocalDate();
        LocalTime start = selected.toLocalTime();
        LocalTime end = readTime("End");
        List<Room> free = service.search(date, start, end);
        out.println("\nFREE THROUGHOUT | " + date + " | " + start + " - " + end);
        for (Room room : free) out.println(room);
        out.println("Total rooms free for the entire interval: " + free.size());
    }

    private void showOccupied() {
        LocalDateTime selected = readDateTime("Time");
        LocalDate date = selected.toLocalDate();
        LocalTime at = selected.toLocalTime();
        List<ScheduledEvent> events = service.occupied(date, at);
        Set<Integer> roomIds = new HashSet<>();
        out.println("\nOCCUPIED EVENTS | " + date + " | " + at);
        for (ScheduledEvent event : events) {
            roomIds.add(event.roomId());
            out.println("Room " + event.roomId() + " | " + event.start() + "-" + event.end() + " | " + event.description());
        }
        out.println("Active events: " + events.size() + " | Distinct rooms: " + roomIds.size());
    }

    private void rooms() {
        switch (operation()) {
            case "C":
                int created = service.createRoom(read("Room name: "));
                out.println("Created room #" + created);
                break;
            case "R":
                for (Room room : service.rooms()) out.println(room);
                break;
            case "U":
                int changed = readId("Room");
                service.updateRoom(changed, read("New room name: "));
                out.println("Updated room #" + changed);
                break;
            case "D":
                int deleted = readId("Room");
                if (confirm()) {
                    service.deleteRoom(deleted);
                    out.println("Deleted room #" + deleted);
                }
                break;
        }
    }

    private void bookings() {
        switch (operation()) {
            case "C":
                int created = service.createBooking(readBooking(0));
                out.println("Created booking #" + created);
                break;
            case "R":
                List<Booking> list = service.bookings();
                for (Booking booking : list) {
                    out.println("#" + booking.id() + " | room " + booking.roomId() + " | " + booking.date()
                            + " | " + booking.start() + "-" + booking.end() + " | " + booking.teacher());
                }
                out.println("Total bookings: " + list.size());
                break;
            case "U":
                service.updateBooking(readBooking(readId("Booking")));
                out.println("Booking updated.");
                break;
            case "D":
                int deleted = readId("Booking");
                if (confirm()) {
                    service.deleteBooking(deleted);
                    out.println("Deleted booking #" + deleted);
                }
                break;
        }
    }

    private Booking readBooking(int id) {
        int roomId = readId("Room");
        String teacher = read("Teacher: ");
        LocalDateTime selected = readDateTime("Start");
        LocalDate date = selected.toLocalDate();
        LocalTime start = selected.toLocalTime();
        LocalTime end = readTime("End");
        return new Booking(id, roomId, teacher, date, start, end);
    }

    private void timetable() {
        switch (operation()) {
            case "C":
                int created = service.createSlot(readSlot(0));
                out.println("Created timetable slot #" + created);
                break;
            case "R":
                List<TimetableSlot> list = service.slots();
                for (TimetableSlot slot : list) {
                    out.println("#" + slot.id() + " | room " + slot.roomId() + " | " + slot.day()
                            + " " + slot.start() + "-" + slot.end() + " | " + slot.description());
                }
                out.println("Total timetable slots: " + list.size());
                break;
            case "U":
                service.updateSlot(readSlot(readId("Timetable slot")));
                out.println("Timetable slot updated.");
                break;
            case "D":
                int deleted = readId("Timetable slot");
                if (confirm()) {
                    service.deleteSlot(deleted);
                    out.println("Deleted timetable slot #" + deleted);
                }
                break;
        }
    }

    private TimetableSlot readSlot(int id) {
        int roomId = readId("Room");
        String teacher = read("Teacher: ");
        DayOfWeek day = DayOfWeek.valueOf(read("Weekday: ").toUpperCase());
        LocalTime start = readTime("Start");
        LocalTime end = readTime("End");
        String code = read("Course code: ");
        String name = read("Course name: ");
        String section = read("Section: ");
        TimetableSlot.Type type = TimetableSlot.Type.valueOf(read("THEORY or LAB: ").toUpperCase());
        return new TimetableSlot(id, roomId, teacher, day, start, end, code, name, section, type);
    }

    private String operation() {
        String value = read("C Create / R Read / U Update / D Delete: ").toUpperCase();
        if (!List.of("C", "R", "U", "D").contains(value)) {
            throw new IllegalArgumentException("Use C, R, U or D.");
        }
        return value;
    }

    private String read(String prompt) {
        out.print(prompt);
        return input.nextLine().trim();
    }

    private int readId(String entity) {
        int value = Integer.parseInt(read(entity + " ID: "));
        if (value <= 0) throw new IllegalArgumentException("ID must be positive.");
        return value;
    }

    private LocalDateTime readDateTime(String timeLabel) {
        String value = read("Date (YYYY-MM-DD or enter for current date and time): ");
        if (value.isBlank()) {
            // Take one snapshot so the date and time agree across midnight.
            return LocalDateTime.now().withSecond(0).withNano(0);
        }
        LocalDate date = LocalDate.parse(value);
        return LocalDateTime.of(date, readTime(timeLabel));
    }

    private LocalTime readTime(String label) {
        return LocalTime.parse(read(label + " (HH:MM): "), clock);
    }

    private boolean confirm() {
        return "DELETE".equals(read("Type DELETE to confirm: "));
    }
}
