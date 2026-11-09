# RoomWise

Java 17+ campus room finder with a terminal menu, MySQL and plain JDBC.
Rooms, bookings and recurring timetable classes have complete CRUD. Scheduling
writes reject overlapping room or teacher events inside a guarded transaction.

## Project layout

```
src/main/java/roomwise/
  Main.java          Application composition and launch
  model/             Room, ScheduledEvent, Booking, TimetableSlot, FreeRoom
  dao/               CampusDao, JdbcCampusDao, DatabaseInitializer
  service/           CampusService
  ui/                ConsoleMenu
  util/              DatabaseConnection, Validation
  exception/         DatabaseException, BookingConflictException, NotFoundException
src/test/java/roomwise/  Model and isolated MySQL integration tests
database/               schema.sql, real_data.sql and original data exports
docs/                   Final report and archived authoring assets
db.properties.example   Example settings; copy privately to db.properties
pom.xml                  Maven build
run_roomwise.bat          Windows launcher
```

## Setup and run

Use Java 17 or newer, Maven and MySQL 8.0.16+ with InnoDB. Verification used
JDK 21 and MySQL 8.0.40. Create roomwise_db using MySQL Workbench and configure
db.properties from the example. Run from the RoomWise directory. The properties
file is read from the working directory and is not packaged in the JAR.
Alternatively pass -Droomwise.config=ABSOLUTE_PATH before the Java class name.

```powershell
mvn package dependency:copy-dependencies -DincludeScope=runtime
java -cp "target/classes;target/dependency/*" roomwise.Main

```

Run run_roomwise.bat to open the terminal menu directly. The launcher cleans
generated target files after the application exits. If another process holds
a dependency JAR, close that process and run mvn clean later.

For a **new empty database only**, populate the supplied 157 rooms / 4,167
timetable rows once:

```powershell
java -cp "target/classes;target/dependency/*" roomwise.Main --seed
```

Startup creates missing tables and migrates the legacy timetable foreign key
from CASCADE to RESTRICT. It does not reload data when counts change. --seed
refuses nonempty domain tables, preserving existing changes. Directly executing
real_data.sql is not the recommended import route; use the guarded seed command.

## Features and OOP evidence

- **Encapsulation:** private model fields; validated Room.setName.
- **Abstraction:** ScheduledEvent abstract class and CampusDao interface.
- **Inheritance:** Booking and TimetableSlot extend ScheduledEvent.
- **Runtime polymorphism:** occursOn / description called through the parent.
- **Overloading:** three CampusService.search signatures.
- **Packages:** UI calls service; service depends on DAO interface; SQL stays in DAO.
- **Exceptions:** custom errors, try-with-resources and explicit rollback.
- **CRUD:** Rooms, Bookings and Timetable console submenus.

Booking dates use YYYY-MM-DD and times use HH:MM (optional seconds). The point
search reports the next event; interval search requires the whole interval to
be free. Occupied results include both bookings and classes and distinguish
event counts from distinct room counts. Adjacent intervals are allowed.

All application writes lock the singleton schedule_guard InnoDB row before
checking conflicts. Teacher comparisons trim and ignore case. External SQL
writers must honor the same lock protocol. Room deletion is restricted while
dependent bookings or timetable rows exist.

## Tests

```powershell
mvn test
mvn test "-Droomwise.integration=true"
```

The normal command runs 4 model tests and skips the database integration class.
The explicit integration command runs all **21 tests**. It creates and removes
its own UUID-named roomwise_test_* database, so the configured account needs
CREATE/DROP DATABASE privileges for this test run. Campus data is not cleared.
Tests cover all entity CRUD, constraints, overlap boundaries, rollback, quoted
input, safe seeding, schema migration and concurrent room/teacher reservations.

## Report and demonstration

The updated 56-page docs/RoomWise_Final_Project_Report.pdf documents the simplified terminal-only implementation. It includes
all requested chapters, REC logo, confirmed team/faculty details, diagrams,
SQL schema, code images and images rendered from actual terminal output. docs/report-source.zip
retains earlier materials and current terminal-20261109/ source/assets, input sequences and full output logs.
The older RoomWise_Implementation_and_Database_Report.pdf is a historical
pre-upgrade explanation; its missing-feature notes no longer describe this build.

Use uniquely named demo records for evaluation: add a room, create/read/update
a booking, reject a conflicting booking, delete it, then delete the empty room.
All three team members should explain every OOP concept and follow the UI to
service to DAO to SQL path. The final report contains a full demo sequence.

This is a trusted local prototype: no login/authorization, stable teacher IDs,
semester calendar, overnight events, capacity/equipment filters or audit history.
Source timetable collisions are retained. Availability scans event collections;
terminal operations wait for query completion. The report explains these limitations.

Keep local settings private. The settings file was tracked before this work;
ignore rules do not remove credentials from existing Git history.

## Simpler implementation

ConsoleMenu uses one named method per task and readBooking/readSlot helpers
that collect one field at a time. CampusService uses ordinary for loops and
boolean variables instead of chained stream expressions. The class hierarchy,
DAO interface and package boundaries remain visible for OOP evaluation. Shared
JDBC transaction helpers keep the concurrency rules in one place.

The new terminal integration test verifies updates, reads, deletion and recovery
after an invalid ID. See Chapter 5 for the source walkthrough and Appendix B for
recorded console demonstrations.

Date/time shortcut: at `Date (YYYY-MM-DD or enter for current date and time)`,
press Enter to use the computer's current local date and time (to the minute),
without a separate Time/Start prompt. This works for free-room and occupied
searches, interval starts, and booking starts. Intervals/bookings still require
an explicit end time. Enter a date to continue with manual time entry.
