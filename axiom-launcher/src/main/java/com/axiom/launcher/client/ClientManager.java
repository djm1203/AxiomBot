package com.axiom.launcher.client;

import com.axiom.launcher.db.Account;
import com.axiom.launcher.db.Database;
import com.axiom.launcher.db.Proxy;
import com.axiom.launcher.security.FilePermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages the lifecycle of all running RuneLite client subprocesses.
 *
 * <h3>Configuration</h3>
 * Properties are loaded from two locations in priority order:
 * <ol>
 *   <li>{@code ~/.axiom/launcher.properties} — user overrides</li>
 *   <li>{@code /launcher.properties} on the classpath — bundled defaults</li>
 * </ol>
 * User properties override defaults on a per-key basis.
 *
 * <h3>Threading</h3>
 * {@link #launch} and {@link #stopInstance} may be called from the JavaFX
 * application thread. The monitor thread polls from a daemon background thread.
 * {@code activeInstances} is a {@link CopyOnWriteArrayList} so both threads
 * can iterate/modify without explicit locking.
 */
public class ClientManager
{
    private static final Logger log = LoggerFactory.getLogger(ClientManager.class);

    private static final int MONITOR_POLL_INTERVAL_MS = 2_000;

    // ── Configuration keys ────────────────────────────────────────────────────
    private static final String KEY_RUNELITE_JAR    = "runelite.jar.path";
    private static final String KEY_DEFAULT_HEAP    = "default.heap.mb";
    private static final String KEY_DEVELOPER_MODE  = "developer.mode";
    private static final String KEY_LAUNCH_DELAY_MS = "launch.delay.ms";

    // ── State ─────────────────────────────────────────────────────────────────
    private final Properties            props           = new Properties();
    private final List<ClientInstance>  activeInstances = new CopyOnWriteArrayList<>();
    private final AtomicInteger         nextIndex       = new AtomicInteger(0);

    private volatile Thread monitorThread;

    // ── Constructor ───────────────────────────────────────────────────────────

    public ClientManager()
    {
        loadProperties();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Launches a new RuneLite client for the given account and script.
     *
     * <p>The subprocess command is:
     * <pre>
     * {java} -Xmx{heapMb}m -ea
     *        -Daxiom.script={scriptName}
     *        -Daxiom.world={world}
     *        -Daxiom.account.id={jagexCharacterId}
     *        [-Dhttps.proxyHost={host} -Dhttps.proxyPort={port}]
     *        [-Dhttp.proxyUser={username} -Dhttp.proxyPassword={password}]
     *        -Daxiom.window.x={windowX} -Daxiom.window.y={windowY}
     *        -jar {runeliteJarPath}
     *        [--developer-mode]
     * </pre>
     *
     * @param account    account to run under (provides jagexCharacterId and proxy)
     * @param scriptName script to auto-start inside the plugin (passed as system property)
     * @param world      world number to connect to (0 = client default)
     * @param proxy      optional proxy configuration; null = direct connection
     * @param heapMb     JVM heap ceiling in megabytes for this client
     * @param windowX    screen X position hint passed to the Axiom plugin
     * @param windowY    screen Y position hint passed to the Axiom plugin
     * @return the running ClientInstance
     * @throws RuntimeException if the runelite.jar.path is not configured or the
     *                          process could not be started
     */
    public ClientInstance launch(
        Account account,
        String  scriptName,
        int     world,
        Proxy   proxy,
        int     heapMb,
        int     windowX,
        int     windowY)
    {
        String runeliteJar = props.getProperty(KEY_RUNELITE_JAR, "").trim();
        if (runeliteJar.isEmpty())
        {
            throw new RuntimeException(
                "runelite.jar.path is not configured. " +
                "Set it in ~/.axiom/launcher.properties.");
        }

        if (!new File(runeliteJar).isFile())
        {
            throw new RuntimeException(
                "RuneLite JAR not found at: " + runeliteJar);
        }

        // ── Resolve the java executable ────────────────────────────────────
        String javaExe = ProcessHandle.current()
            .info()
            .command()
            .orElse("java");

        // ── Build argument list ────────────────────────────────────────────
        List<String> cmd = new ArrayList<>();
        cmd.add(javaExe);
        cmd.add("-Xmx" + heapMb + "m");
        cmd.add("-ea");

        // Axiom plugin reads these to identify which account/script is running
        cmd.add("-Daxiom.script=" + scriptName);
        if (world > 0) cmd.add("-Daxiom.world=" + world);
        if (account.jagexCharacterId != null && !account.jagexCharacterId.isEmpty())
        {
            cmd.add("-Daxiom.account.id=" + account.jagexCharacterId);
        }

        // Window position hint for multi-client tiling
        cmd.add("-Daxiom.window.x=" + windowX);
        cmd.add("-Daxiom.window.y=" + windowY);

        // Proxy (standard Java HTTPS/HTTP system properties)
        if (proxy != null)
        {
            cmd.add("-Dhttps.proxyHost=" + proxy.host);
            cmd.add("-Dhttps.proxyPort=" + proxy.port);
            if (proxy.username != null && !proxy.username.isEmpty())
            {
                cmd.add("-Dhttp.proxyUser=" + proxy.username);
                // passwordEnc holds plaintext after ProxyRepository.mapRow() decrypts it
                String password = proxy.passwordEnc != null ? proxy.passwordEnc : "";
                cmd.add("-Dhttp.proxyPassword=" + password);
            }
        }

        // JAR path
        cmd.add("-jar");
        cmd.add(runeliteJar);

        // RuneLite program arguments (not JVM args)
        if (Boolean.parseBoolean(props.getProperty(KEY_DEVELOPER_MODE, "false")))
        {
            cmd.add("--developer-mode");
        }

        // ── Start process ──────────────────────────────────────────────────
        log.info("Launching client #{}: script='{}' world={} account='{}' proxy={}",
            nextIndex.get(), scriptName, world, account.displayName,
            proxy != null ? proxy.name : "none");
        // Never log the full command — it contains proxy credentials as -D system properties

        try
        {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true); // merge stderr into stdout

            // Redirect output to a per-instance log file in ~/.axiom/logs/
            File logsDir = new File(Database.getAxiomDir(), "logs");
            if (!logsDir.exists())
            {
                logsDir.mkdirs();
                FilePermissions.setOwnerOnly(logsDir);
            }
            File logFile = new File(logsDir,
                "client-" + nextIndex.get() + "-" + System.currentTimeMillis() + ".log");
            pb.redirectOutput(logFile);

            Process process = pb.start();
            // Restrict the log file to owner-only after the process creates it
            FilePermissions.setOwnerOnly(logFile);
            int     index   = nextIndex.getAndIncrement();

            ClientInstance instance =
                new ClientInstance(index, account, scriptName, world, process);
            activeInstances.add(instance);

            log.info("Client #{} started (pid={})", index, process.pid());
            return instance;
        }
        catch (Exception e)
        {
            log.error("Failed to start RuneLite subprocess", e);
            throw new RuntimeException("Client launch failed: " + e.getMessage(), e);
        }
    }

    /**
     * Forcibly kills the given instance and removes it from the active list.
     */
    public void stopInstance(ClientInstance instance)
    {
        if (instance == null) return;
        log.info("Stopping client #{} ({})", instance.getInstanceIndex(), instance.getStatus());
        instance.kill();
        activeInstances.remove(instance);
    }

    /** Forcibly stops all active clients. */
    public void stopAll()
    {
        log.info("Stopping all {} active client(s)", activeInstances.size());
        for (ClientInstance inst : activeInstances)
        {
            inst.kill();
        }
        activeInstances.clear();
    }

    /** Returns an unmodifiable snapshot of the currently active instances. */
    public List<ClientInstance> getActiveInstances()
    {
        return Collections.unmodifiableList(activeInstances);
    }

    /**
     * Starts a daemon thread that polls every 2 seconds for dead processes and
     * marks them as CRASHED.
     *
     * Safe to call once after constructing ClientManager.
     * Calling it more than once starts an additional monitor thread (avoid).
     */
    public void startMonitorThread()
    {
        Thread t = new Thread(this::monitorLoop, "axiom-client-monitor");
        t.setDaemon(true);
        t.start();
        this.monitorThread = t;
        log.debug("Client monitor thread started");
    }

    // ── Properties loading ────────────────────────────────────────────────────

    private void loadProperties()
    {
        // 1. Load bundled defaults from classpath
        try (InputStream defaults = ClientManager.class.getResourceAsStream("/launcher.properties"))
        {
            if (defaults != null) props.load(defaults);
        }
        catch (Exception e)
        {
            log.warn("Could not load bundled launcher.properties: {}", e.getMessage());
        }

        // 2. Override with user-specific file at ~/.axiom/launcher.properties
        File userConfig = new File(Database.getAxiomDir(), "launcher.properties");
        if (userConfig.isFile())
        {
            try (FileInputStream fis = new FileInputStream(userConfig))
            {
                Properties user = new Properties();
                user.load(fis);
                props.putAll(user); // user values win
                log.debug("Loaded user launcher.properties from {}", userConfig.getAbsolutePath());
            }
            catch (Exception e)
            {
                log.warn("Could not load user launcher.properties: {}", e.getMessage());
            }
        }

        log.debug("Effective launcher config: runelite.jar.path='{}' heap={}mb dev-mode={}",
            props.getProperty(KEY_RUNELITE_JAR, ""),
            props.getProperty(KEY_DEFAULT_HEAP, "512"),
            props.getProperty(KEY_DEVELOPER_MODE, "false"));
    }

    // ── Monitor thread ────────────────────────────────────────────────────────

    private void monitorLoop()
    {
        while (!Thread.currentThread().isInterrupted())
        {
            try
            {
                Thread.sleep(MONITOR_POLL_INTERVAL_MS);
                checkInstances();
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.debug("Client monitor thread exiting");
    }

    private void checkInstances()
    {
        List<ClientInstance> dead = new ArrayList<>();

        for (ClientInstance inst : activeInstances)
        {
            if (!inst.isAlive() && !"STOPPED".equals(inst.getStatus()))
            {
                inst.setStatus("CRASHED");
                dead.add(inst);
                log.warn("Client #{} (account='{}') crashed — process exited unexpectedly",
                    inst.getInstanceIndex(),
                    inst.getAccount() != null ? inst.getAccount().displayName : "?");
            }
        }

        activeInstances.removeAll(dead);
    }

    // ── Convenience accessors ─────────────────────────────────────────────────

    /** Returns the configured default heap size in MB. */
    public int getDefaultHeapMb()
    {
        try { return Integer.parseInt(props.getProperty(KEY_DEFAULT_HEAP, "512")); }
        catch (NumberFormatException e) { return 512; }
    }

    /** Returns the configured delay between bulk-launches in milliseconds. */
    public long getLaunchDelayMs()
    {
        try { return Long.parseLong(props.getProperty(KEY_LAUNCH_DELAY_MS, "3000")); }
        catch (NumberFormatException e) { return 3_000L; }
    }
}
