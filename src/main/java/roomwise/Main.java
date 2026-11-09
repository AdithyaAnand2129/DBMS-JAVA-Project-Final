package roomwise;
import roomwise.dao.*;
import roomwise.service.CampusService;
import roomwise.ui.*;
/** Composition root connects the UI, service interface and JDBC implementation. */
public final class Main {
    private Main() {}
    /** Launches the terminal menu, or explicitly seeds an empty database. */
    public static void main(String[] args) {
        try {
            DatabaseInitializer.initialize();
            if (args.length > 0 && "--seed".equalsIgnoreCase(args[0])) {
                DatabaseInitializer.seed();
                return;
            }
            CampusService service = new CampusService(new JdbcCampusDao());
            new ConsoleMenu(service).start();
        } catch (RuntimeException error) {
            System.err.println(error.getMessage());
            System.exit(1);
        }
    }
}
