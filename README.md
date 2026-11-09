# RoomWise

RoomWise displays the supplied real college timetable using MySQL, JDBC, and a
small console menu.

## Simple project map

```text
RoomWise/
├── data/                 Real college timetable data
│   ├── theory.json       3,030 theory records
│   └── labs.json         1,137 lab records
├── src/main/java/com/roomwise/
│   └── Main.java         Student timetable application
├── src/main/resources/
│   ├── schema.sql        MySQL tables and relationships
│   ├── real_data.sql     Workbench-ready SQL generated from both JSON files
│   └── db.properties     Local database connection settings
├── pom.xml               Maven dependency list
└── README.md             This guide
```

## Explaining `Main.java`

All Java code is intentionally kept in one file for easy presentation. Use the
section order below when explaining it.

| Section | What it does | Concept to mention |
|---|---|---|
| `Main` | Starts the timetable browser | Entry point |
| `TimetableSlot` | Holds the four student-facing fields | Encapsulation |
| `TimetableDao` | Reads timetable records with prepared statements | JDBC / DAO |
| `ConsoleMenu` | Shows browse and search options | User interface |

## Database design

The redesigned database keeps timetable metadata internally in `rooms`,
`timetable_slots`, and `bookings`. The student-facing timetable fields remain
simple:

| Column | Meaning |
|---|---|
| `room` | Room name/number |
| `start_time` | Class start time |
| `end_time` | Class end time |
| `id` | Timetable record ID |

The weekday, course, section, teacher, class type, and booking fields are
retained internally because they are required for live availability, safe
bookings, and detailed class searches. Capacity, buildings, users, and
equipment are not stored because they are not needed by this application.

## How to run it

There are two ways to start the app:

- Swing GUI: `RUN_SWING.md`
- Terminal mode: `RUN_TERMINAL.md`

### Quick start

```bash
mvn clean package
java -jar target/roomwise-1.0-SNAPSHOT.jar
```

For the console version:

```bash
java -cp target/classes;C:/Users/<your-user>/.m2/repository/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar roomwise.Main --console
```

## Load and run it

In MySQL Workbench, run `src/main/resources/schema.sql` once. It removes the
old multi-table design and creates the minimal `timetable_slots` table.
Then run `src/main/resources/real_data.sql` once. It loads all 4,167 records
from `data/theory.json` and `data/labs.json`.

If Java reports that the database is using the old schema, the migration has
not been run yet. Execute both files in that order; `schema.sql` intentionally
removes the previous tables before creating the minimal table.

After the first successful setup, Java automatically checks the schema and
record count at startup and loads the bundled SQL when required. MySQL Server
must be running, but MySQL Workbench does not need to be open.

Compile and run from this folder with the MySQL Connector/J JAR on the
classpath:

```text
C:\Users\adith\.m2\repository\com\mysql\mysql-connector-j\8.3.0\mysql-connector-j-8.3.0.jar
```

The four console options are:

1. **Free rooms** — uses the current weekday and time automatically and shows
   each room's free start and next occupation time.
2. **Display all campus rooms** — lists every room and its ID.
3. **Book a room** — lists the currently free rooms, asks for the room ID and
   teacher, and permits booking only until the next scheduled class.
4. **Find classes during a time range** — shows room, course, section, teacher,
   class type, and times for active classes today.
Configure the local MySQL password in `src/main/resources/db.properties`.
