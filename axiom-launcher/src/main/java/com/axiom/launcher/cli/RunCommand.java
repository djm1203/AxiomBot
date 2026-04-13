package com.axiom.launcher.cli;

import com.axiom.launcher.client.ClientInstance;
import com.axiom.launcher.client.ClientManager;
import com.axiom.launcher.db.Account;
import com.axiom.launcher.db.AccountRepository;
import com.axiom.launcher.db.Database;
import com.axiom.launcher.db.Proxy;
import com.axiom.launcher.db.ProxyRepository;
import com.axiom.launcher.security.CryptoManager;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * axiom run — launches a single RuneLite client and blocks until it exits.
 *
 * Example:
 *   java -jar axiom-launcher.jar run --account "Main" --script "Axiom Woodcutting" --world 301
 */
@Command(
    name = "run",
    mixinStandardHelpOptions = true,
    description = "Launch a single RuneLite client and block until it exits."
)
public class RunCommand implements Callable<Integer>
{
    @Option(names = "--account", required = true,
            description = "Account display name or Jagex character ID (case-insensitive).")
    private String accountArg;

    @Option(names = "--script", required = true,
            description = "Script name, e.g. 'Axiom Woodcutting'.")
    private String script;

    @Option(names = "--world", defaultValue = "0",
            description = "World number (0 = client default).")
    private int world;

    @Option(names = "--proxy",
            description = "Proxy name as configured in the launcher (optional).")
    private String proxyName;

    @Option(names = "--heap-mb", defaultValue = "512",
            description = "JVM heap ceiling in megabytes.")
    private int heapMb;

    @Option(names = "--window-x", defaultValue = "0",
            description = "Window X position hint.")
    private int windowX;

    @Option(names = "--window-y", defaultValue = "0",
            description = "Window Y position hint.")
    private int windowY;

    @Option(names = "--window-w", defaultValue = "0",
            description = "Window width hint (reserved for future tiling support).")
    @SuppressWarnings("unused")
    private int windowW;

    @Option(names = "--window-h", defaultValue = "0",
            description = "Window height hint (reserved for future tiling support).")
    @SuppressWarnings("unused")
    private int windowH;

    @Override
    public Integer call()
    {
        // ── Init ──────────────────────────────────────────────────────────────
        try
        {
            Database.init();
            CryptoManager.init();
        }
        catch (Exception e)
        {
            System.err.println("Fatal: initialisation failed — " + e.getMessage());
            return 1;
        }

        // ── Resolve account ───────────────────────────────────────────────────
        Account account = new AccountRepository().findAll().stream()
            .filter(a -> a.displayName.equalsIgnoreCase(accountArg)
                      || accountArg.equalsIgnoreCase(a.jagexCharacterId))
            .findFirst()
            .orElse(null);

        if (account == null)
        {
            System.err.println("Error: no account found matching: " + accountArg);
            System.err.println("       Use 'axiom accounts list' to see available accounts.");
            return 1;
        }

        // ── Resolve proxy (optional) ──────────────────────────────────────────
        Proxy proxy = null;
        if (proxyName != null)
        {
            proxy = new ProxyRepository().findAll().stream()
                .filter(p -> p.name.equalsIgnoreCase(proxyName))
                .findFirst()
                .orElse(null);
            if (proxy == null)
            {
                System.err.println("Error: no proxy found with name: " + proxyName);
                System.err.println("       Use 'axiom proxies list' to see available proxies.");
                return 1;
            }
        }

        // ── Launch ────────────────────────────────────────────────────────────
        ClientInstance instance;
        try
        {
            instance = new ClientManager().launch(account, script, world, proxy, heapMb, windowX, windowY);
        }
        catch (RuntimeException e)
        {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }

        System.out.printf("Launched: %s on %s (PID %d)%n",
            script, account.displayName, instance.getProcess().pid());

        // ── Block until exit ──────────────────────────────────────────────────
        try
        {
            int exitCode = instance.getProcess().waitFor();
            System.out.printf("Client exited with code %d.%n", exitCode);
            return exitCode == 0 ? 0 : 1;
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            System.err.println("Interrupted while waiting for client.");
            return 1;
        }
    }
}
