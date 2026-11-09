# Timetable source data

The full college timetable exports are included in this folder:

- `theory.json` — 3,030 theory records
- `labs.json` — 1,137 lab records

For a MySQL Workbench import without running Java, execute
`../src/main/resources/schema.sql` first and then
`../src/main/resources/real_data.sql`. The SQL file is generated from both
JSON files and contains all 4,167 timetable records.

The SQL conversion retains room, weekday, times, course, section, teacher, and
class-type metadata. Options 1 and 2 intentionally display only the fields
students need; options 3 and 4 use the retained metadata for safe bookings and
detailed class searches.
