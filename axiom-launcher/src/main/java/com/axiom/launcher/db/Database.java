package com.axiom.launcher.db;

import com.axiom.launcher.security.FilePermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * SQLite database bootstrap for the Axiom Launcher.
 *
 * Database location: {@code ~/.axiom/axiom.db}
 * The {@code ~/.axiom/} directory is created automatically if absent.
 *
 * Connection strategy:
 *   Single-connection pool — one live Connection is held for the lifetime of
 *   the application. The connection is re-established if it is found closed
 *   (e.g., after a fatal JDBC error). All access is synchronized.
 *
 * Usage:
 *   {@code Database.init()}  — called once at startup; creates tables.
 *   {@code Database.getConnection()} — returns the live connection.
 *   {@code Database.close()}  — called at shutdown.
 */
public final class Database
{
    private static final Logger log = LoggerFactory.getLogger(Database.class);

    private static final String AXIOM_DIR;
    private static final String DB_PATH;

    static
    {
        String home = System.getProperty("user.home");
        File   dir  = new File(home, ".axiom");
        if (!dir.exists() && !dir.mkdirs())
        {
            log.warn("Could not create ~/.axiom directory at {}", dir.getAbsolutePath());
        }
        FilePermissions.setOwnerOnly(dir);
        AXIOM_DIR = dir.getAbsolutePath();
        DB_PATH   = new File(dir, "axiom.db").getAbsolutePath();
    }

    private static volatile Connection connection;

    private Database() {}

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Initialises the database: opens (or creates) the SQLite file and runs all
     * {@code CREATE TABLE IF NOT EXISTS} DDL statements.
     *
     * Safe to call multiple times — the IF NOT EXISTS guard prevents duplicates.
     *
     * @throws RuntimeException if the database cannot be initialised
     */
    public static void init()
    {
        log.info("Initialising database at {}", DB_PATH);
        try
        {
            Connection conn = getConnection(); // establishes & configures connection
            try (Statement stmt = conn.createStatement())
            {
                // Enable FK enforcement for this connection (SQLite default is OFF)
                stmt.execute("PRAGMA foreign_keys = ON");

                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS proxies (" +
                    "    id           INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "    name         TEXT    NOT NULL UNIQUE," +
                    "    host         TEXT    NOT NULL," +
                    "    port         INTEGER NOT NULL," +
                    "    username     TEXT," +
                    "    password_enc TEXT" +
                    ")"
                );

                // accounts references proxies — proxies must be created first
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS accounts (" +
                    "    id                   INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "    display_name         TEXT    NOT NULL," +
                    "    jagex_character_id   TEXT," +
                    "    bank_pin_enc         TEXT," +
                    "    preferred_world      INTEGER DEFAULT 0," +
                    "    proxy_id             INTEGER REFERENCES proxies(id)" +
                    "                                 ON DELETE SET NULL," +
                    "    notes                TEXT," +
                    "    created_at           TEXT    DEFAULT (datetime('now'))" +
                    ")"
                );

                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS sessions (" +
                    "    id          INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "    account_id  INTEGER REFERENCES accounts(id)," +
                    "    script_name TEXT," +
                    "    started_at  TEXT," +
                    "    ended_at    TEXT," +
                    "    status      TEXT DEFAULT 'RUNNING'" +
                    ")"
                );
            }
            log.info("Database initialised — schema ready");
        }
        catch (Exception e)
        {
            log.error("Database initialisation failed", e);
            throw new RuntimeException("Database init failed: " + e.getMessage(), e);
        }
    }

    /** Closes the live connection. Called once at application shutdown. */
    public static synchronized void close()
    {
        if (connection != null)
        {
            try
            {
                if (!connection.isClosed()) connection.close();
                log.debug("Database connection closed");
            }
            catch (Exception e)
            {
                log.warn("Error closing database connection", e);
            }
            finally
            {
                connection = null;
            }
        }
    }

    // ── Connection pool ───────────────────────────────────────────────────────

    /**
     * Returns the live SQLite connection, reconnecting if it was closed.
     *
     * <p>All callers run on either the JavaFX application thread or the
     * ClientManager monitor thread — synchronization prevents a race on
     * {@code connection} during a reconnect.</p>
     *
     * @throws Exception if JDBC cannot open the connection
     */
    public static synchronized Connection getConnection() throws Exception
    {
        if (connection == null || connection.isClosed())
        {
            log.debug("Opening SQLite connection to {}", DB_PATH);
            connection = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
            connection.setAutoCommit(true);

            // Foreign key enforcement must be re-applied on every new connection
            try (Statement s = connection.createStatement())
            {
                s.execute("PRAGMA foreign_keys = ON");
            }
        }
        return connection;
    }

    /** Returns the absolute path to the ~/.axiom directory. */
    public static String getAxiomDir() { return AXIOM_DIR; }

    /** Returns the absolute path to the database file. */
    public static String getDbPath() { return DB_PATH; }
}
