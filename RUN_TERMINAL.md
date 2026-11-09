# Run RoomWise in terminal mode

This starts the text-based console version instead of the Swing window.

## 1) Prepare the database

- Start MySQL Server
- Check `src/main/resources/db.properties`
- Import schema/data if needed:
  - `src/main/resources/schema.sql`
  - `src/main/resources/real_data.sql`

## 2) Build the project

From the project root:

```bash
mvn clean package
```

## 3) Run the console version

```bash
java -cp target/classes;C:/Users/<your-user>/.m2/repository/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar roomwise.Main --console
```

On macOS/Linux:

```bash
java -cp target/classes:/Users/<your-user>/.m2/repository/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar roomwise.Main --console
```

## 4) Console options

1. Free rooms now
2. Display all campus rooms
3. Book a room
4. Find classes during a time range
0. Exit

The console menu works without opening the GUI.

