package roomwise.model;
import roomwise.util.Validation;
/** Encapsulates room identity and a validated name. */
public final class Room {
    private final int id;
    private String name;
    /** Zero denotes an unsaved room; database IDs are positive. */
    public Room(int id, String name) {
        if (id < 0) throw new IllegalArgumentException("Room ID cannot be negative.");
        this.id = id; setName(name);
    }
    /** Returns the database key. */
    public int id() { return id; }
    /** Returns the room name. */
    public String name() { return name; }
    /** Enforces the NOT NULL / VARCHAR(40) domain before a database write. */
    public void setName(String name) { this.name = Validation.text(name, 40, "Room name"); }
    @Override public String toString() { return id + " - " + name; }
}
