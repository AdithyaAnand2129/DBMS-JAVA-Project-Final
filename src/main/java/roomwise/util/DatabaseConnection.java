package roomwise.util;
import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.Properties;
import roomwise.exception.DatabaseException;
/** Plain JDBC connection factory. Credentials remain outside the built JAR. */
public final class DatabaseConnection {
    private DatabaseConnection() {}
    /** Opens a fresh connection using the local file or an explicit config path. */
    public static Connection open() {
        Path path = Path.of(System.getProperty("roomwise.config", "db.properties"));
        try (InputStream input = Files.newInputStream(path)) {
            Properties p = new Properties(); p.load(input);
            String url = System.getProperty("roomwise.db.url", p.getProperty("db.url"));
            if (url == null || p.getProperty("db.user") == null)
                throw new IOException("Required connection settings are absent");
            url += (url.contains("?") ? "&" : "?") + "connectTimeout=10000&socketTimeout=30000";
            return DriverManager.getConnection(url, p.getProperty("db.user"), p.getProperty("db.password"));
        } catch (IOException | SQLException e) {
            throw new DatabaseException("Cannot connect to MySQL. Check the server and local db.properties.", e);
        }
    }
}
