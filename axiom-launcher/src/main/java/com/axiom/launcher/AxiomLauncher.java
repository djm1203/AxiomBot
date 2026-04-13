package com.axiom.launcher;

import com.axiom.launcher.cli.AxiomCLI;
import com.axiom.launcher.db.Database;
import com.axiom.launcher.security.CryptoManager;
import com.axiom.launcher.ui.MainWindow;
import javafx.application.Application;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

/**
 * Entry point for the Axiom Launcher.
 *
 * Routing:
 *   - No args → start JavaFX GUI
 *   - Any args → delegate to picocli AxiomCLI and exit with its return code
 *
 * The JavaFX path initialises the SQLite database before showing the window,
 * which ensures ~/.axiom/axiom.db exists and all tables are present before
 * any view tries to load data.
 */
public class AxiomLauncher extends Application
{
    private static final Logger log = LoggerFactory.getLogger(AxiomLauncher.class);

    public static void main(String[] args)
    {
        if (args.length > 0)
        {
            // CLI mode — delegate to picocli, exit with its return code
            int exitCode = new CommandLine(new AxiomCLI()).execute(args);
            System.exit(exitCode);
        }

        // GUI mode — launch JavaFX
        launch(args);
    }

    @Override
    public void start(Stage primaryStage)
    {
        log.info("Axiom Launcher starting");

        try
        {
            Database.init();
        }
        catch (Exception e)
        {
            log.error("Fatal: database initialization failed", e);
            System.exit(1);
        }

        try
        {
            CryptoManager.init();
        }
        catch (Exception e)
        {
            log.error("Fatal: CryptoManager initialization failed", e);
            System.exit(1);
        }

        MainWindow window = new MainWindow(primaryStage);
        window.show();

        log.info("Axiom Launcher ready");
    }

    @Override
    public void stop()
    {
        log.info("Axiom Launcher shutting down");
        Database.close();
    }
}
