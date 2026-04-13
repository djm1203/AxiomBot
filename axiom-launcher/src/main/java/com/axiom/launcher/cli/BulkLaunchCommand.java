package com.axiom.launcher.cli;

import com.axiom.launcher.client.ClientManager;
import com.axiom.launcher.db.Account;
import com.axiom.launcher.db.AccountRepository;
import com.axiom.launcher.db.Database;
import com.axiom.launcher.db.Proxy;
import com.axiom.launcher.db.ProxyRepository;
import com.axiom.launcher.security.CryptoManager;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * axiom bulk-launch — launches multiple RuneLite clients from a CSV file.
 *
 * CSV header row (all columns optional except account and script):
 *   account, script, world, proxy, heap_mb, window_x, window_y, window_w, window_h
 *
 * Example:
 *   java -jar axiom-launcher.jar bulk-launch --file clients.csv --delay-ms 5000
 */
@Command(
    name = "bulk-launch",
    mixinStandardHelpOptions = true,
    description = "Launch multiple RuneLite clients from a CSV file."
)
public class BulkLaunchCommand implements Callable<Integer>
{
    @Option(names = "--file", required = true,
            description = "Path to the CSV file.")
    private File csvFile;

    @Option(names = "--delay-ms", defaultValue = "3000",
            description = "Delay between launches in milliseconds (default: 3000).")
    private long delayMs;

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

        if (!csvFile.isFile())
        {
            System.err.println("Error: file not found: " + csvFile.getAbsolutePath());
            return 1;
        }

        // ── Parse CSV ─────────────────────────────────────────────────────────
        List<Map<String, String>> rows;
        try
        {
            rows = CsvUtil.read(csvFile);
        }
        catch (Exception e)
        {
            System.err.println("Error reading CSV: " + e.getMessage());
            return 1;
        }

        if (rows.isEmpty())
        {
            System.out.println("CSV file is empty or has only a header row — nothing to launch.");
            return 0;
        }

        AccountRepository accountRepo = new AccountRepository();
        ProxyRepository   proxyRepo   = new ProxyRepository();
        ClientManager     manager     = new ClientManager();

        int total    = rows.size();
        int launched = 0;
        int failed   = 0;

        // ── Launch each row ───────────────────────────────────────────────────
        for (int i = 0; i < total; i++)
        {
            Map<String, String> row = rows.get(i);
            String accountArg = CsvUtil.get(row, "account");
            String script     = CsvUtil.get(row, "script");

            System.out.printf("Launching %d/%d: %s on %s...%n", i + 1, total, script, accountArg);

            try
            {
                if (accountArg.isEmpty()) throw new IllegalArgumentException("'account' column is empty");
                if (script.isEmpty())     throw new IllegalArgumentException("'script' column is empty");

                Account account = accountRepo.findAll().stream()
                    .filter(a -> a.displayName.equalsIgnoreCase(accountArg)
                              || accountArg.equalsIgnoreCase(a.jagexCharacterId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("account not found: " + accountArg));

                String proxyName = CsvUtil.get(row, "proxy");
                Proxy  proxy     = null;
                if (!proxyName.isEmpty())
                {
                    proxy = proxyRepo.findAll().stream()
                        .filter(p -> p.name.equalsIgnoreCase(proxyName))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("proxy not found: " + proxyName));
                }

                int world   = CsvUtil.getInt(row, "world",    0);
                int heapMb  = CsvUtil.getInt(row, "heap_mb",  512);
                int windowX = CsvUtil.getInt(row, "window_x", 0);
                int windowY = CsvUtil.getInt(row, "window_y", 0);

                manager.launch(account, script, world, proxy, heapMb, windowX, windowY);
                System.out.printf("  OK%n");
                launched++;
            }
            catch (Exception e)
            {
                System.err.printf("  Error on row %d: %s%n", i + 1, e.getMessage());
                failed++;
                // continue to next row — never abort the whole batch
            }

            if (i < total - 1 && delayMs > 0)
            {
                try { Thread.sleep(delayMs); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        }

        System.out.printf("%nBulk launch complete: %d launched, %d failed (total %d).%n",
            launched, failed, total);
        return failed == total ? 1 : 0;   // exit 1 only if every row failed
    }
}
