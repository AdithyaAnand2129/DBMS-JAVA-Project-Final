package roomwise.model;
import java.time.LocalTime;
/** Immutable result of availability calculation; not a separate database table. */
public record FreeRoom(Room room, LocalTime freeFrom, LocalTime freeUntil) {}
