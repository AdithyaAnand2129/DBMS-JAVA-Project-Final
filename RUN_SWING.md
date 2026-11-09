# Run RoomWise in Swing mode

This starts the GUI window for the room finder.

## 1) Make sure MySQL is ready

- Start MySQL Server
- Confirm `src/main/resources/db.properties` has the correct database URL, username, and password
- If the database is empty, import the SQL files once:
  - `src/main/resources/schema.sql`
  - `src/main/resources/real_data.sql`

## 2) Build the project

From the project root (`RoomWise`):

```bash
mvn clean package
```

## 3) Run the Swing app

### Option A: run the built jar

```bash
java -jar target/roomwise-1.0-SNAPSHOT.jar
```

This starts the Swing UI directly.

### Option B: run the class manually

```bash
java -cp target/classes;C:/Users/<your-user>/.m2/repository/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar roomwise.Main
```

On macOS/Linux, use a colon instead of a semicolon:

```bash
java -cp target/classes:/Users/<your-user>/.m2/repository/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar roomwise.Main
```

## 4) What happens

The app opens the Swing room finder window and shows available rooms for the selected day and time.

