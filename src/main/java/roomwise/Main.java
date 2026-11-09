package roomwise;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        DatabaseInitializer.initialize();
        TimetableDao dao = new TimetableDao();
        dao.verifySchema();
        if (args.length > 0 && "--console".equalsIgnoreCase(args[0])) {
            new ConsoleMenu(dao).start();
            return;
        }
        SwingUtilities.invokeLater(() -> new RoomFinderWindow(dao).setVisible(true));
    }
}

record Room(int id, String name) {
}

record OccupiedSlot(LocalTime start, LocalTime end) {
}

record FreeRoom(Room room, LocalTime freeFrom, LocalTime freeUntil) {
}

record ClassDetails(int id, String room, LocalTime start, LocalTime end,
                    String courseCode, String courseName, String section,
                    String teacher, String type) {
}

final class TimetableDao {

    void verifySchema() {
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT r.id, r.room, t.day_of_week, t.course_code, "
                             + "t.teacher FROM rooms r JOIN timetable_slots t "
                             + "ON t.room_id = r.id LIMIT 1")) {
            statement.executeQuery().close();
        } catch (SQLException exception) {
            throw new DatabaseException("The RoomWise database is not initialized. "
                    + "Restart the application with MySQL Server running.", exception);
        }
    }

    List<Room> findAllRooms() {
        List<Room> rooms = new ArrayList<>();
        String sql = "SELECT id, room FROM rooms ORDER BY room";
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                rooms.add(new Room(results.getInt("id"), results.getString("room")));
            }
            return rooms;
        } catch (SQLException exception) {
            throw failure("read campus rooms", exception);
        }
    }

    List<OccupiedSlot> findOccupiedToday(LocalDate date) {
        List<OccupiedSlot> slots = new ArrayList<>();
        String day = date.getDayOfWeek().name();
        String sql = "SELECT start_time, end_time FROM timetable_slots "
                + "WHERE day_of_week = ? ORDER BY start_time";
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, day);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    slots.add(new OccupiedSlot(
                            results.getObject("start_time", LocalTime.class),
                            results.getObject("end_time", LocalTime.class)));
                }
            }
            return slots;
        } catch (SQLException exception) {
            throw failure("read today's timetable", exception);
        }
    }

    List<FreeRoom> findFreeRooms(LocalDate date, LocalTime now) {
        String day = date.getDayOfWeek().name();
        String sql = "SELECT r.id, r.room, "
                + "LEAST(COALESCE((SELECT MIN(t.start_time) FROM timetable_slots t "
                + "WHERE t.room_id = r.id AND t.day_of_week = ? "
                + "AND t.start_time > ?), CAST('23:59:59' AS TIME)), "
                + "COALESCE((SELECT MIN(b.start_time) FROM bookings b "
                + "WHERE b.room_id = r.id AND b.booking_date = ? "
                + "AND b.start_time > ?), CAST('23:59:59' AS TIME))) AS next_occupied "
                + "FROM rooms r "
                + "WHERE NOT EXISTS (SELECT 1 FROM timetable_slots active "
                + "WHERE active.room_id = r.id AND active.day_of_week = ? "
                + "AND active.start_time <= ? AND active.end_time > ?) "
                + "AND NOT EXISTS (SELECT 1 FROM bookings b "
                + "WHERE b.room_id = r.id AND b.booking_date = ? "
                + "AND b.start_time <= ? AND b.end_time > ?) "
                + "ORDER BY r.room";
        List<FreeRoom> free = new ArrayList<>();
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, day);
            statement.setObject(2, now);
            statement.setObject(3, date);
            statement.setObject(4, now);
            statement.setString(5, day);
            statement.setObject(6, now);
            statement.setObject(7, now);
            statement.setObject(8, date);
            statement.setObject(9, now);
            statement.setObject(10, now);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    free.add(new FreeRoom(
                            new Room(results.getInt("id"), results.getString("room")),
                            now,
                            results.getObject("next_occupied", LocalTime.class)));
                }
            }
            return free;
        } catch (SQLException exception) {
            throw failure("find free rooms", exception);
        }
    }

    List<ClassDetails> findOccupiedAt(LocalDate date, LocalTime at) {
        LocalTime end = at.equals(LocalTime.of(23, 59, 59)) ? at : at.plusSeconds(1);
        return findClasses(date, at, end);
    }

    List<ClassDetails> findClasses(LocalDate date, LocalTime start, LocalTime end) {
        List<ClassDetails> classes = new ArrayList<>();
        String sql = "SELECT t.id, r.room, t.start_time, t.end_time, "
                + "t.course_code, t.course_name, t.section, t.teacher, t.class_type "
                + "FROM timetable_slots t JOIN rooms r ON r.id = t.room_id "
                + "WHERE t.day_of_week = ? AND t.start_time < ? AND t.end_time > ? "
                + "ORDER BY t.start_time, r.room";
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, date.getDayOfWeek().name());
            statement.setObject(2, end);
            statement.setObject(3, start);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    classes.add(new ClassDetails(results.getInt("id"),
                            results.getString("room"),
                            results.getObject("start_time", LocalTime.class),
                            results.getObject("end_time", LocalTime.class),
                            results.getString("course_code"),
                            results.getString("course_name"),
                            results.getString("section"),
                            results.getString("teacher"),
                            results.getString("class_type")));
                }
            }
            return classes;
        } catch (SQLException exception) {
            throw failure("find active classes", exception);
        }
    }

    void book(Room room, String teacher, LocalDate date, LocalTime start, LocalTime end) {
        String conflictSql = "SELECT COUNT(*) FROM timetable_slots "
                + "WHERE room_id = ? AND day_of_week = ? AND start_time < ? AND end_time > ? "
                + "UNION ALL SELECT COUNT(*) FROM bookings "
                + "WHERE room_id = ? AND booking_date = ? AND start_time < ? AND end_time > ?";
        String insertSql = "INSERT INTO bookings (teacher, room_id, booking_date, start_time, end_time) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement conflict = connection.prepareStatement(conflictSql);
             PreparedStatement insert = connection.prepareStatement(insertSql)) {
            connection.setAutoCommit(false);
            conflict.setInt(1, room.id());
            conflict.setString(2, date.getDayOfWeek().name());
            conflict.setObject(3, end);
            conflict.setObject(4, start);
            conflict.setInt(5, room.id());
            conflict.setObject(6, date);
            conflict.setObject(7, end);
            conflict.setObject(8, start);
            try (ResultSet results = conflict.executeQuery()) {
                int conflicts = 0;
                while (results.next()) {
                    conflicts += results.getInt(1);
                }
                if (conflicts != 0) {
                    connection.rollback();
                    throw new IllegalArgumentException("The room was occupied while booking.");
                }
            }
            insert.setString(1, teacher);
            insert.setInt(2, room.id());
            insert.setObject(3, date);
            insert.setObject(4, start);
            insert.setObject(5, end);
            insert.executeUpdate();
            connection.commit();
        } catch (SQLException exception) {
            throw failure("save booking", exception);
        }
    }

    private DatabaseException failure(String action, SQLException exception) {
        return new DatabaseException("Could not " + action + ": "
                + exception.getMessage(), exception);
    }
}

final class ConsoleMenu {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm");
    private final TimetableDao dao;
    private final Scanner input = new Scanner(System.in);

    ConsoleMenu(TimetableDao dao) {
        this.dao = dao;
    }

    void start() {
        boolean running = true;
        while (running) {
            System.out.println("\n=== RoomWise Timetable ===");
            System.out.println("1. Free rooms now");
            System.out.println("2. Display all campus rooms");
            System.out.println("3. Book a room");
            System.out.println("4. Find classes during a time range");
            System.out.println("0. Exit");
            switch (read("Choose an option: ")) {
                case "1" -> showFreeRooms();
                case "2" -> showAllRooms();
                case "3" -> bookRoom();
                case "4" -> findClasses();
                case "0" -> running = false;
                default -> System.out.println("Enter 1, 2, 3, 4, or 0.");
            }
        }
        System.out.println("Goodbye.");
    }

    private void showFreeRooms() {
        try {
            LocalDate date = readDay(LocalDate.now());
            LocalTime at = readTimeOrNow();
            List<FreeRoom> free = dao.findFreeRooms(date, at);
            System.out.println("\nFree rooms on " + date.getDayOfWeek() + " at " + format(at) + ":");
            if (free.isEmpty()) {
                System.out.println("No free rooms found.");
                return;
            }
            free.forEach(room -> System.out.printf("Room %s | Free from %s | Occupied at %s%n",
                    room.room().name(), format(room.freeFrom()), format(room.freeUntil())));
            System.out.println("Total free rooms: " + free.size());
            System.out.println("Occupied rooms at this time: " + dao.findOccupiedAt(date, at).size());
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            System.out.println("Use a weekday such as TUESDAY and a time such as 16:10.");
        }
    }

    private void showAllRooms() {
        List<Room> rooms = dao.findAllRooms();
        System.out.println("\nRooms in the campus:");
        rooms.forEach(room -> System.out.printf("ID %d | Room %s%n", room.id(), room.name()));
        System.out.println("Total campus rooms: " + rooms.size());
    }

    private void bookRoom() {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now().withSecond(0).withNano(0);
        List<FreeRoom> free = dao.findFreeRooms(today, now);
        if (free.isEmpty()) {
            System.out.println("No rooms are free to book right now.");
            return;
        }
        System.out.println("\nRooms available to book now:");
        free.forEach(room -> System.out.printf("ID %d | Room %s | Book until %s%n",
                room.room().id(), room.room().name(), format(room.freeUntil())));
        System.out.println("A room can only be booked before its next scheduled class.");
        try {
            int id = Integer.parseInt(read("Room ID: "));
            FreeRoom selected = free.stream().filter(room -> room.room().id() == id)
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("That room is not free."));
            String teacher = read("Teacher name: ");
            LocalTime end = parse(read("Booking end time (H:MM): "));
            if (teacher.isBlank() || !end.isAfter(now) || end.isAfter(selected.freeUntil())) {
                System.out.println("Booking must have a teacher and end before "
                        + format(selected.freeUntil()) + ".");
                return;
            }
            dao.book(selected.room(), teacher, today, now, end);
            System.out.printf("Booked Room %s for %s until %s.%n",
                    selected.room().name(), teacher, format(end));
        } catch (NumberFormatException | DateTimeParseException exception) {
            System.out.println("Use a valid room ID and time such as 16:30.");
        } catch (IllegalArgumentException exception) {
            System.out.println("Booking failed: " + exception.getMessage());
        }
    }

    private void findClasses() {
        try {
            LocalTime start = parse(read("Start time (H:MM): "));
            LocalTime end = parse(read("End time (H:MM): "));
            if (!end.isAfter(start)) {
                System.out.println("End time must be after start time.");
                return;
            }
            List<ClassDetails> classes = dao.findClasses(LocalDate.now(), start, end);
            System.out.println("\nClasses on " + LocalDate.now().getDayOfWeek()
                    + " overlapping " + format(start) + " - " + format(end) + ":");
            if (classes.isEmpty()) {
                System.out.println("No active classes found.");
                return;
            }
            classes.forEach(item -> System.out.printf(
                    "ID %d | Room %s | %s - %s | %s %s | Section %s | Teacher %s | %s%n",
                    item.id(), item.room(), format(item.start()), format(item.end()),
                    item.courseCode(), item.courseName(), item.section(),
                    item.teacher(), item.type()));
        } catch (DateTimeParseException exception) {
            System.out.println("Use a time such as 9:00 or 13:20.");
        }
    }

    private LocalDate readDay(LocalDate today) {
        String value = read("Day (MONDAY-SUNDAY, Enter for today): ");
        if (value.isBlank()) {
            return today;
        }
        return today.with(DayOfWeek.valueOf(value.trim().toUpperCase()));
    }

    private LocalTime readTimeOrNow() {
        String value = read("Time (H:MM, Enter for now): ");
        if (value.isBlank()) {
            return LocalTime.now().withSecond(0).withNano(0);
        }
        return parse(value);
    }

    private LocalTime parse(String value) {
        return LocalTime.parse(value, TIME);
    }

    private String format(LocalTime value) {
        if (value.equals(LocalTime.MAX) || value.equals(LocalTime.of(23, 59, 59))) {
            return "end of day";
        }
        return value.format(TIME);
    }

    private String read(String prompt) {
        System.out.print(prompt);
        return input.nextLine().trim();
    }
}

final class RoomFinderWindow extends JFrame {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm");
    private static final String[] SLOT_TIMES = {
            "Current time",
            "8:00", "9:00", "10:00", "11:00", "11:40",
            "12:00", "13:00", "13:20", "14:00", "15:10", "16:10"
    };

    private final TimetableDao dao;
    private final JComboBox<DayOfWeek> dayBox = new JComboBox<>(DayOfWeek.values());
    private final JComboBox<String> timeBox = new JComboBox<>(new DefaultComboBoxModel<>(SLOT_TIMES));
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new String[] {"Room", "Status", "Details"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    RoomFinderWindow(TimetableDao dao) {
        super("RoomWise");
        this.dao = dao;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(720, 480);
        setLocationRelativeTo(null);
        dayBox.setSelectedItem(LocalDate.now().getDayOfWeek());

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filters.add(new JLabel("Day"));
        filters.add(dayBox);
        filters.add(new JLabel("Time"));
        filters.add(timeBox);
        JButton freeButton = new JButton("Free");
        JButton occupiedButton = new JButton("Occupied");
        freeButton.addActionListener(this::showFree);
        occupiedButton.addActionListener(this::showOccupied);
        filters.add(freeButton);
        filters.add(occupiedButton);

        add(filters, BorderLayout.NORTH);
        add(new JScrollPane(new JTable(tableModel)), BorderLayout.CENTER);
        showFree(null);
    }

    private void showFree(ActionEvent ignored) {
        try {
            LocalDate date = selectedDate();
            LocalTime at = selectedTime();
            List<FreeRoom> free = dao.findFreeRooms(date, at);
            tableModel.setRowCount(0);
            for (FreeRoom room : free) {
                String until = format(room.freeUntil());
                tableModel.addRow(new Object[] {
                        room.room().name(),
                        "Free",
                        "Available until " + until
                });
            }
            setTitle("RoomWise — " + free.size() + " free rooms on "
                    + date.getDayOfWeek() + " at " + format(at));
        } catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(),
                    "Could not load free rooms", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showOccupied(ActionEvent ignored) {
        try {
            LocalDate date = selectedDate();
            LocalTime at = selectedTime();
            List<ClassDetails> occupied = dao.findOccupiedAt(date, at);
            tableModel.setRowCount(0);
            for (ClassDetails item : occupied) {
                tableModel.addRow(new Object[] {
                        item.room(),
                        "Occupied",
                        item.courseCode() + " " + item.courseName()
                                + " | " + format(item.start()) + " - " + format(item.end())
                                + " | " + item.teacher()
                });
            }
            setTitle("RoomWise — " + occupied.size() + " occupied rooms on "
                    + date.getDayOfWeek() + " at " + format(at));
        } catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(),
                    "Could not load occupied rooms", JOptionPane.ERROR_MESSAGE);
        }
    }

    private LocalDate selectedDate() {
        return LocalDate.now().with((DayOfWeek) dayBox.getSelectedItem());
    }

    private LocalTime selectedTime() {
        String selected = (String) timeBox.getSelectedItem();
        if (selected == null || "Current time".equals(selected)) {
            return LocalTime.now().withSecond(0).withNano(0);
        }
        return LocalTime.parse(selected, TIME);
    }

    private static String format(LocalTime value) {
        if (value.equals(LocalTime.MAX) || value.equals(LocalTime.of(23, 59, 59))) {
            return "end of day";
        }
        return value.format(TIME);
    }
}

final class DatabaseInitializer {
    private static final int EXPECTED_RECORDS = 4167;

    private DatabaseInitializer() {
    }

    static void initialize() {
        System.out.println("Checking RoomWise database...");
        try (Connection connection = DatabaseConnection.open()) {
            if (!acquireInitializationLock(connection)) {
                throw new SQLException("Another RoomWise process is initializing the database");
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SET SESSION innodb_lock_wait_timeout = 10")) {
                statement.execute();
            }
            try {
                if (!hasRequiredSchema(connection)
                        || recordCount(connection) != EXPECTED_RECORDS
                        || hasMisparsedAfternoonTimes(connection)) {
                    System.out.println("Loading timetable data into MySQL...");
                    executeScript(connection, "schema.sql");
                    executeScript(connection, "real_data.sql");
                }
            } finally {
                releaseInitializationLock(connection);
            }
            System.out.println("Database ready.");
        } catch (SQLException | IOException exception) {
            throw new DatabaseException("Could not initialize the database automatically. "
                    + "Check MySQL Server and db.properties.", exception);
        }
    }

    private static boolean acquireInitializationLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT GET_LOCK('roomwise_database_initialization', 30)");
             ResultSet results = statement.executeQuery()) {
            results.next();
            return results.getInt(1) == 1;
        }
    }

    private static void releaseInitializationLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT RELEASE_LOCK('roomwise_database_initialization')")) {
            statement.executeQuery().close();
        }
    }

    private static boolean hasRequiredSchema(Connection connection) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND "
                + "((table_name = 'rooms' AND column_name IN ('id', 'room')) OR "
                + "(table_name = 'timetable_slots' AND column_name IN "
                + "('id', 'room_id', 'day_of_week', 'start_time', 'end_time', "
                + "'course_code', 'course_name', 'section', 'teacher', 'class_type')) OR "
                + "(table_name = 'bookings' AND column_name IN "
                + "('id', 'teacher', 'room_id', 'booking_date', 'start_time', 'end_time')))";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            results.next();
            return results.getInt(1) == 18;
        }
    }

    private static boolean hasMisparsedAfternoonTimes(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM timetable_slots WHERE start_time < '08:00:00'");
             ResultSet results = statement.executeQuery()) {
            results.next();
            return results.getInt(1) > 0;
        }
    }

    private static int recordCount(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM timetable_slots");
             ResultSet results = statement.executeQuery()) {
            results.next();
            return results.getInt(1);
        }
    }

    private static void executeScript(Connection connection, String resource)
            throws IOException, SQLException {
        String script;
        try (InputStream stream = DatabaseInitializer.class.getClassLoader()
                .getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IOException(resource + " is missing from the classpath");
            }
            script = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (String text : script.replaceAll("(?m)^\\s*--.*$", "").split(";")) {
            if (!text.trim().isEmpty()) {
                try (PreparedStatement statement = connection.prepareStatement(text.trim())) {
                    statement.execute();
                }
            }
        }
    }
}

final class DatabaseConnection {
    private DatabaseConnection() {
    }

    static Connection open() {
        try {
            Properties properties = new Properties();
            try (InputStream stream = DatabaseConnection.class.getClassLoader()
                    .getResourceAsStream("db.properties")) {
                if (stream == null) {
                    throw new IOException("db.properties is missing");
                }
                properties.load(stream);
            }
            String url = properties.getProperty("db.url");
            String separator = url.contains("?") ? "&" : "?";
            url += separator + "connectTimeout=10000&socketTimeout=30000";
            return DriverManager.getConnection(url,
                    properties.getProperty("db.user"), properties.getProperty("db.password"));
        } catch (IOException | SQLException exception) {
            throw new DatabaseException("Could not connect to roomwise_db.", exception);
        }
    }
}

final class DatabaseException extends RuntimeException {
    DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
