package roomwise.dao;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import roomwise.exception.DatabaseException;
import roomwise.util.DatabaseConnection;
/** Idempotent schema setup that never reloads or deletes existing timetable data. */
public final class DatabaseInitializer {
    private DatabaseInitializer() {}
    /** Creates missing tables and performs the one legacy foreign-key migration. */
    public static void initialize() {
        try(Connection c=DatabaseConnection.open()) {
            try(PreparedStatement s=c.prepareStatement("SELECT GET_LOCK('roomwise_schema_setup',10)");ResultSet r=s.executeQuery()) {
                r.next(); if(r.getInt(1)!=1) throw new SQLException("Schema setup is busy");
            }
            try {
                script(c,"schema.sql");
                String rule=null;
                try(PreparedStatement s=c.prepareStatement("SELECT DELETE_RULE FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME='fk_slot_room'");ResultSet r=s.executeQuery()) {
                    if(r.next()) rule=r.getString(1);
                }
                if("CASCADE".equals(rule)) {
                    try(PreparedStatement s=c.prepareStatement("ALTER TABLE timetable_slots DROP FOREIGN KEY fk_slot_room, ADD CONSTRAINT fk_slot_room_restrict FOREIGN KEY(room_id) REFERENCES rooms(id) ON DELETE RESTRICT")) { s.execute(); }
                }
            } finally {
                try(PreparedStatement s=c.prepareStatement("SELECT RELEASE_LOCK('roomwise_schema_setup')")) { s.execute(); }
            }
            System.out.println("Database ready. Existing records preserved.");
        } catch(SQLException | IOException e) { throw new DatabaseException("Schema setup failed. Check MySQL privileges and configuration.",e); }
    }
    /** Explicitly loads the supplied dataset only into an empty application schema. */
    public static void seed() {
        try(Connection c=DatabaseConnection.open()) {
            c.setAutoCommit(false);
            try {
                try(PreparedStatement s=c.prepareStatement("SELECT id FROM schedule_guard WHERE id=1 FOR UPDATE");ResultSet r=s.executeQuery()) {
                    if(!r.next()) throw new SQLException("Run schema initialization first");
                }
                try(PreparedStatement s=c.prepareStatement("SELECT (SELECT COUNT(*) FROM rooms)+(SELECT COUNT(*) FROM timetable_slots)+(SELECT COUNT(*) FROM bookings)");ResultSet r=s.executeQuery()) {
                    r.next(); if(r.getInt(1)!=0) throw new IllegalArgumentException("Seed refused: application tables must be empty. Existing records were preserved.");
                }
                script(c,"real_data.sql"); c.commit();
                System.out.println("Loaded supplied timetable data.");
            } catch(SQLException | IOException | RuntimeException e) {
                try { c.rollback(); } catch(SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        } catch(SQLException | IOException e) { throw new DatabaseException("Initial timetable import failed; no records were committed.",e); }
    }
    private static void script(Connection c,String name) throws IOException,SQLException {
        try(InputStream input=DatabaseInitializer.class.getClassLoader().getResourceAsStream(name)) {
            if(input==null) throw new IOException("Schema resource is missing");
            String sql=new String(input.readAllBytes(),StandardCharsets.UTF_8).replaceAll("(?m)^\\s*--.*$", "");
            for(String part:sql.split(";")) {
                String command=part.trim();
                // Respect the configured database (including isolated integration-test schemas).
                if(command.isEmpty() || command.toUpperCase().startsWith("USE ") || command.toUpperCase().startsWith("CREATE DATABASE ")
                        || command.equalsIgnoreCase("START TRANSACTION") || command.equalsIgnoreCase("COMMIT")) continue;
                try(PreparedStatement s=c.prepareStatement(command)) { s.execute(); }
            }
        }
    }
}
