package roomwise.dao;
import java.sql.*;
import java.time.*;
import java.util.*;
import roomwise.model.*;
import roomwise.exception.*;
import roomwise.util.DatabaseConnection;
/** Hand-written JDBC, prepared parameters, row mapping and transactional CRUD. */
public final class JdbcCampusDao implements CampusDao {
    @FunctionalInterface private interface Work<T> { T run(Connection c) throws SQLException; }
    private <T> T read(Work<T> work) {
        try (Connection c=DatabaseConnection.open()) { return work.run(c); }
        catch (SQLException e) { throw failure(e); }
    }
    private <T> T write(Work<T> work) {
        try (Connection c=DatabaseConnection.open()) {
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            c.setAutoCommit(false);
            try {
                // One InnoDB guard row serializes all application schedule mutations.
                try (PreparedStatement lock=c.prepareStatement("SELECT id FROM schedule_guard WHERE id=1 FOR UPDATE");
                     ResultSet rows=lock.executeQuery()) {
                    if (!rows.next()) throw new SQLException("Scheduling guard is missing");
                }
                T value=work.run(c); c.commit(); return value;
            } catch (SQLException | RuntimeException e) {
                try { c.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        } catch (SQLException e) { throw failure(e); }
    }
    private DatabaseException failure(SQLException e) {
        String message=e.getSQLState()!=null && e.getSQLState().startsWith("23")
                ? "Database constraint rejected the change. Check duplicate names, room references and time values."
                : "Database operation failed. Check MySQL and the application configuration.";
        return new DatabaseException(message,e);
    }
    @Override public List<Room> rooms() {
        return read(c -> {
            List<Room> list=new ArrayList<>();
            try (PreparedStatement s=c.prepareStatement("SELECT id,room FROM rooms ORDER BY room"); ResultSet r=s.executeQuery()) {
                while(r.next()) list.add(new Room(r.getInt("id"),r.getString("room")));
            } return list;
        });
    }
    @Override public List<Booking> bookings() {
        return read(c -> {
            List<Booking> list=new ArrayList<>();
            try (PreparedStatement s=c.prepareStatement("SELECT * FROM bookings ORDER BY booking_date,start_time,id"); ResultSet r=s.executeQuery()) {
                while(r.next()) list.add(new Booking(r.getInt("id"),r.getInt("room_id"),r.getString("teacher"),
                        r.getObject("booking_date",LocalDate.class),r.getObject("start_time",LocalTime.class),r.getObject("end_time",LocalTime.class)));
            } return list;
        });
    }
    @Override public List<TimetableSlot> slots() {
        return read(c -> {
            List<TimetableSlot> list=new ArrayList<>();
            try (PreparedStatement s=c.prepareStatement("SELECT * FROM timetable_slots ORDER BY day_of_week,start_time,id"); ResultSet r=s.executeQuery()) {
                while(r.next()) list.add(new TimetableSlot(r.getInt("id"),r.getInt("room_id"),r.getString("teacher"),
                        DayOfWeek.valueOf(r.getString("day_of_week")),r.getObject("start_time",LocalTime.class),r.getObject("end_time",LocalTime.class),
                        r.getString("course_code"),r.getString("course_name"),r.getString("section"),TimetableSlot.Type.valueOf(r.getString("class_type"))));
            } return list;
        });
    }
    private int execute(Connection c,String sql,Object... values) throws SQLException {
        try(PreparedStatement s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)) {
            bind(s,values); s.executeUpdate();
            try(ResultSet keys=s.getGeneratedKeys()) { if(keys.next()) return keys.getInt(1); }
            throw new SQLException("No generated key returned");
        }
    }
    private void change(Connection c,String sql,Object... values) throws SQLException {
        try(PreparedStatement s=c.prepareStatement(sql)) {
            bind(s,values);
            if(s.executeUpdate()!=1) throw new NotFoundException("Record not found; refresh and try again.");
        }
    }
    private void bind(PreparedStatement s,Object... values) throws SQLException {
        for(int i=0;i<values.length;i++) s.setObject(i+1,values[i]);
    }
    private int count(Connection c,String sql,Object... values) throws SQLException {
        try(PreparedStatement s=c.prepareStatement(sql)) {
            bind(s,values); try(ResultSet r=s.executeQuery()) { r.next(); return r.getInt(1); }
        }
    }
    private void exists(Connection c,String table,int id) throws SQLException {
        // Table is an internal constant chosen by the caller; IDs are parameters.
        if(count(c,"SELECT COUNT(*) FROM "+table+" WHERE id=?",id)==0)
            throw new NotFoundException("The selected record no longer exists.");
    }
    @Override public int createRoom(Room room) {
        return write(c -> execute(c,"INSERT INTO rooms(room) VALUES (?)",room.name()));
    }
    @Override public void updateRoom(Room room) {
        write(c -> { change(c,"UPDATE rooms SET room=? WHERE id=?",room.name(),room.id()); return null; });
    }
    @Override public void deleteRoom(int id) {
        write(c -> {
            if(count(c,"SELECT COUNT(*) FROM timetable_slots WHERE room_id=?",id)>0 ||
               count(c,"SELECT COUNT(*) FROM bookings WHERE room_id=?",id)>0)
                throw new IllegalArgumentException("Remove this room's timetable and bookings before deleting it.");
            change(c,"DELETE FROM rooms WHERE id=?",id); return null;
        });
    }
    private void checkConflict(Connection c,ScheduledEvent event) throws SQLException {
        exists(c,"rooms",event.roomId());
        String conflict=" AND (room_id=? OR LOWER(TRIM(teacher))=LOWER(TRIM(?))) AND start_time < ? AND end_time > ? AND id <> ?";
        String day=event instanceof Booking b ? b.date().getDayOfWeek().name() : ((TimetableSlot)event).day().name();
        int ownSlot=event instanceof TimetableSlot ? event.id() : 0;
        int ownBooking=event instanceof Booking ? event.id() : 0;
        if(count(c,"SELECT COUNT(*) FROM timetable_slots WHERE day_of_week=?"+conflict,
                day,event.roomId(),event.teacher(),event.end(),event.start(),ownSlot)>0)
            throw new BookingConflictException("Room or teacher overlaps a recurring timetable class.");
        String dateCondition=event instanceof Booking ? "booking_date=?" : "WEEKDAY(booking_date)=?";
        Object date=event instanceof Booking b ? b.date() : ((TimetableSlot)event).day().getValue()-1;
        if(count(c,"SELECT COUNT(*) FROM bookings WHERE "+dateCondition+conflict,
                date,event.roomId(),event.teacher(),event.end(),event.start(),ownBooking)>0)
            throw new BookingConflictException("Room or teacher overlaps an existing booking.");
    }
    @Override public int createBooking(Booking b) {
        return write(c -> { checkConflict(c,b); return execute(c,
                "INSERT INTO bookings(teacher,room_id,booking_date,start_time,end_time) VALUES (?,?,?,?,?)",
                b.teacher(),b.roomId(),b.date(),b.start(),b.end()); });
    }
    @Override public void updateBooking(Booking b) {
        write(c -> { exists(c,"bookings",b.id()); checkConflict(c,b); change(c,
                "UPDATE bookings SET teacher=?,room_id=?,booking_date=?,start_time=?,end_time=? WHERE id=?",
                b.teacher(),b.roomId(),b.date(),b.start(),b.end(),b.id()); return null; });
    }
    @Override public void deleteBooking(int id) {
        write(c -> { change(c,"DELETE FROM bookings WHERE id=?",id); return null; });
    }
    @Override public int createSlot(TimetableSlot t) {
        return write(c -> { checkConflict(c,t); return execute(c,
                "INSERT INTO timetable_slots(room_id,day_of_week,start_time,end_time,course_code,course_name,section,teacher,class_type) VALUES (?,?,?,?,?,?,?,?,?)",
                t.roomId(),t.day().name(),t.start(),t.end(),t.courseCode(),t.courseName(),t.section(),t.teacher(),t.type().name()); });
    }
    @Override public void updateSlot(TimetableSlot t) {
        write(c -> { exists(c,"timetable_slots",t.id()); checkConflict(c,t); change(c,
                "UPDATE timetable_slots SET room_id=?,day_of_week=?,start_time=?,end_time=?,course_code=?,course_name=?,section=?,teacher=?,class_type=? WHERE id=?",
                t.roomId(),t.day().name(),t.start(),t.end(),t.courseCode(),t.courseName(),t.section(),t.teacher(),t.type().name(),t.id()); return null; });
    }
    @Override public void deleteSlot(int id) {
        write(c -> { change(c,"DELETE FROM timetable_slots WHERE id=?",id); return null; });
    }
}
