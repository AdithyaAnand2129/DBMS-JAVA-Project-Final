package roomwise;
import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import roomwise.dao.*;
import roomwise.exception.*;
import roomwise.model.*;
import roomwise.service.CampusService;
import roomwise.util.DatabaseConnection;
import static org.junit.jupiter.api.Assertions.*;

/** Runs only on request; creates/drops its own uniquely named test database. */
@EnabledIfSystemProperty(named="roomwise.integration",matches="true")
class DatabaseIntegrationTest {
    static String testDatabase,originalUrl;
    CampusService service=new CampusService(new JdbcCampusDao());
    LocalDate date=LocalDate.of(2026,11,9);
    @BeforeAll static void createTestDatabase() throws Exception {
        Properties p=new Properties();
        try(InputStream in=Files.newInputStream(Path.of(System.getProperty("roomwise.config","db.properties")))) { p.load(in); }
        originalUrl=p.getProperty("db.url");
        testDatabase="roomwise_test_"+UUID.randomUUID().toString().replace("-","");
        try(Connection c=DatabaseConnection.open(); PreparedStatement s=c.prepareStatement("CREATE DATABASE "+testDatabase)) { s.execute(); }
        String url=originalUrl.replaceFirst("(jdbc:mysql://[^/]+/)[^?]*", "$1"+testDatabase);
        if(url.equals(originalUrl)) throw new IllegalStateException("Could not select isolated test database");
        System.setProperty("roomwise.db.url",url); DatabaseInitializer.initialize();
    }
    @AfterAll static void removeTestDatabase() throws Exception {
        System.clearProperty("roomwise.db.url");
        if(testDatabase!=null && testDatabase.matches("roomwise_test_[a-f0-9]{32}"))
            try(Connection c=DatabaseConnection.open(); PreparedStatement s=c.prepareStatement("DROP DATABASE "+testDatabase)) { s.execute(); }
    }
    @BeforeEach void clearFixtures() throws Exception {
        try(Connection c=DatabaseConnection.open()) {
            for(String sql:List.of("DELETE FROM bookings","DELETE FROM timetable_slots","DELETE FROM rooms"))
                try(PreparedStatement s=c.prepareStatement(sql)) { s.executeUpdate(); }
        }
    }
    Booking b(int id,int room,String teacher,int from,int to) { return new Booking(id,room,teacher,date,LocalTime.of(from,0),LocalTime.of(to,0)); }
    TimetableSlot t(int id,int room,String teacher,int from,int to) { return new TimetableSlot(id,room,teacher,DayOfWeek.MONDAY,LocalTime.of(from,0),LocalTime.of(to,0),"CS23","Java","S3",TimetableSlot.Type.THEORY); }
    @Test void roomCrudAndDuplicateConstraint() {
        int id=service.createRoom("TEST-A"); assertTrue(id>0); assertEquals("TEST-A",service.room(id).name());
        assertThrows(DatabaseException.class,() -> service.createRoom("TEST-A"));
        service.updateRoom(id,"TEST-B"); assertEquals("TEST-B",service.room(id).name());
        service.deleteRoom(id); assertTrue(service.rooms().isEmpty());
        assertThrows(NotFoundException.class,() -> service.updateRoom(id,"Missing"));
    }
    @Test void terminalMenuUpdatesReadsDeletesAndHandlesInvalidInput() {
        int room = service.createRoom("TERMINAL-TEST");
        String input = "3\nU\n" + room + "\nRENAMED\n3\nR\n3\nU\n0\n3\nD\n" + room + "\nDELETE\n0\n";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new roomwise.ui.ConsoleMenu(service, new Scanner(input), new PrintStream(output)).start();
        String text = output.toString();
        assertTrue(text.contains("RENAMED"));
        assertTrue(text.contains("ID must be positive"));
        assertTrue(text.contains("Deleted room #" + room));
        assertTrue(text.contains("Goodbye."));
        assertTrue(service.rooms().isEmpty());
    }
    @Test void bookingCrudPersistsAndDeletes() {
        int room=service.createRoom("TEST-A"), id=service.createBooking(b(0,room,"Teacher",9,10));
        assertEquals(id,service.bookings().get(0).id()); service.updateBooking(b(id,room,"Teacher",10,11));
        assertEquals(LocalTime.of(10,0),new JdbcCampusDao().bookings().get(0).start());
        service.deleteBooking(id); assertTrue(service.bookings().isEmpty());
    }
    @Test void blankDateUsesCurrentDateAndTimeWithoutAnotherPrompt() {
        LocalDateTime before = LocalDateTime.now().withSecond(0).withNano(0);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new roomwise.ui.ConsoleMenu(service, new Scanner("1\n\n2\n\n0\n"), new PrintStream(output)).start();
        LocalDateTime after = LocalDateTime.now().withSecond(0).withNano(0);
        String text = output.toString();
        assertFalse(text.contains("Time (HH:MM)"));
        assertFalse(text.contains("Action failed"));
        assertTrue(text.contains("Goodbye."));
        java.util.regex.Matcher result = java.util.regex.Pattern.compile(
                "(?:FREE ROOMS|OCCUPIED EVENTS) \\| (\\d{4}-\\d{2}-\\d{2}) \\| (\\d{2}:\\d{2})").matcher(text);
        int count = 0;
        while (result.find()) {
            LocalDateTime selected = LocalDateTime.parse(result.group(1) + "T" + result.group(2));
            assertFalse(selected.isBefore(before));
            assertFalse(selected.isAfter(after));
            count++;
        }
        assertEquals(2, count);
    }
    @Test void explicitDateStillAsksForTimeAndInvalidDateRecovers() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new roomwise.ui.ConsoleMenu(service,
                new Scanner("1\ninvalid\n1\n2026-11-09\n09:00\n0\n"), new PrintStream(output)).start();
        String text = output.toString();
        assertTrue(text.contains("Action failed"));
        assertTrue(text.contains("Time (HH:MM)"));
        assertTrue(text.contains("FREE ROOMS | 2026-11-09 | 09:00"));
        assertTrue(text.contains("Goodbye."));
    }
    @Test void timetableCrudPersistsAndDeletes() {
        int room=service.createRoom("TEST-A"), id=service.createSlot(t(0,room,"Teacher",9,10));
        assertEquals(id,service.slots().get(0).id()); service.updateSlot(t(id,room,"Teacher",10,11));
        assertEquals(LocalTime.of(10,0),service.slots().get(0).start()); service.deleteSlot(id); assertTrue(service.slots().isEmpty());
    }
    @Test void roomAndTeacherConflictsAreRejected() {
        int a=service.createRoom("A"), other=service.createRoom("B"); service.createSlot(t(0,a,"Teacher",9,10));
        assertThrows(BookingConflictException.class,() -> service.createBooking(b(0,a,"Another",9,10)));
        assertThrows(BookingConflictException.class,() -> service.createBooking(b(0,other," teacher ",9,10)));
        service.createBooking(b(0,a,"Another",10,11)); assertEquals(1,service.bookings().size());
    }
    @Test void rejectedUpdateRollsBackAndKeepsOriginal() {
        int room=service.createRoom("A"); service.createBooking(b(0,room,"T",9,10));
        int id=service.createBooking(b(0,room,"T",10,11));
        assertThrows(BookingConflictException.class,() -> service.updateBooking(b(id,room,"T",9,11)));
        assertEquals(LocalTime.of(10,0),service.bookings().stream().filter(x -> x.id()==id).findFirst().orElseThrow().start());
    }
    @Test void timetableEditChecksDatedBookings() {
        int room=service.createRoom("A"); service.createBooking(b(0,room,"T",9,10));
        assertThrows(BookingConflictException.class,() -> service.createSlot(t(0,room,"Other",9,10)));
    }
    @Test void availabilityIncludesBothSubclassesAndBoundaries() {
        int a=service.createRoom("A"), b=service.createRoom("B"); service.createSlot(t(0,a,"T1",9,10)); service.createBooking(b(0,b,"T2",10,11));
        assertEquals(1,service.search(date,LocalTime.of(9,0)).size());
        assertEquals(1,service.occupied(date,LocalTime.of(10,0)).size());
        assertEquals("Booking | T2",service.occupied(date,LocalTime.of(10,0)).get(0).description());
        assertTrue(service.search(date,LocalTime.of(9,0),LocalTime.of(11,0)).isEmpty());
        assertEquals(LocalTime.of(10,0),service.search(date,LocalTime.of(9,0)).get(0).freeUntil());
        assertEquals(2,service.search(date,LocalTime.of(11,0)).size());
    }
    @Test void referentialIntegrityAndTimeCheckAreEnforced() throws Exception {
        int room=service.createRoom("A"); service.createSlot(t(0,room,"T",9,10));
        assertThrows(IllegalArgumentException.class,() -> service.deleteRoom(room));
        try(Connection c=DatabaseConnection.open()) {
            assertThrows(SQLException.class,() -> { try(PreparedStatement s=c.prepareStatement("DELETE FROM rooms WHERE id=?")) { s.setInt(1,room);s.executeUpdate(); } });
            assertThrows(SQLException.class,() -> { try(PreparedStatement s=c.prepareStatement("INSERT INTO bookings(teacher,room_id,booking_date,start_time,end_time) VALUES ('T',?,'2026-11-09','10:00','09:00')")) { s.setInt(1,room);s.executeUpdate(); } });
            assertThrows(SQLException.class,() -> { try(PreparedStatement s=c.prepareStatement("INSERT INTO bookings(teacher,room_id,booking_date,start_time,end_time) VALUES ('T',-1,'2026-11-09','09:00','10:00')")) {s.executeUpdate();} });
        }
    }
    @Test void parameterBindingStoresQuotedTextAsData() {
        String name="X'); DROP TABLE rooms; --"; int id=service.createRoom(name);
        assertEquals(name,service.room(id).name()); assertEquals(1,service.rooms().size());
    }
    @Test void initializationPreservesEditedData() {
        int room=service.createRoom("KEEP");service.createSlot(t(0,room,"T",9,10));
        DatabaseInitializer.initialize(); assertEquals("KEEP",service.room(room).name()); assertEquals(1,service.slots().size());
    }
    @Test void explicitSeedLoadsDatasetAndRefusesExistingData() {
        DatabaseInitializer.seed();
        assertEquals(157,service.rooms().size()); assertEquals(4167,service.slots().size());
        assertThrows(IllegalArgumentException.class,DatabaseInitializer::seed);
        assertEquals(4167,service.slots().size());
    }
    @Test void legacyCascadeMigrationPreservesRows() throws Exception {
        int room=service.createRoom("LEGACY"); service.createSlot(t(0,room,"T",9,10));
        try(Connection c=DatabaseConnection.open(); PreparedStatement s=c.prepareStatement("ALTER TABLE timetable_slots DROP FOREIGN KEY fk_slot_room_restrict, ADD CONSTRAINT fk_slot_room FOREIGN KEY(room_id) REFERENCES rooms(id) ON DELETE CASCADE")) { s.execute(); }
        DatabaseInitializer.initialize(); assertEquals(1,service.slots().size());
        try(Connection c=DatabaseConnection.open(); PreparedStatement s=c.prepareStatement("DELETE FROM rooms WHERE id=?")) {
            s.setInt(1,room); assertThrows(SQLException.class,s::executeUpdate);
        }
    }
    @Test void concurrentRoomBookingsHaveExactlyOneWinner() throws Exception { concurrent(false); }
    @Test void concurrentTeacherBookingsAcrossRoomsHaveExactlyOneWinner() throws Exception { concurrent(true); }
    private void concurrent(boolean sameTeacher) throws Exception {
        int a=service.createRoom("A"), b=sameTeacher?service.createRoom("B"):a;
        ExecutorService pool=Executors.newFixedThreadPool(2); CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
        try {
            List<Future<Boolean>> results=new ArrayList<>(); int[] rooms={a,b};
            for(int i=0;i<2;i++) { final int n=i; results.add(pool.submit(() -> { ready.countDown(); go.await();
                try { service.createBooking(b(0,rooms[n],sameTeacher?"T":"T"+n,9,10)); return true; }
                catch(BookingConflictException e) { return false; }
            })); }
            assertTrue(ready.await(5,TimeUnit.SECONDS)); go.countDown();
            int winners=0; for(Future<Boolean> result:results) if(result.get(20,TimeUnit.SECONDS)) winners++;
            assertEquals(1,winners); assertEquals(1,service.bookings().size());
        } finally { go.countDown(); pool.shutdownNow(); }
    }
}
