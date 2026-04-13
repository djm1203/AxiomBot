package com.axiom.launcher.cli;

import com.axiom.launcher.db.Account;
import com.axiom.launcher.db.AccountRepository;
import com.axiom.launcher.db.Database;
import com.axiom.launcher.db.Proxy;
import com.axiom.launcher.db.ProxyRepository;
import com.axiom.launcher.security.CryptoManager;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * axiom proxies — CRUD and CSV import/export for proxy configurations.
 *
 * Subcommands: list, import, export
 */
@Command(
    name = "proxies",
    mixinStandardHelpOptions = true,
    description = "Manage HTTP/HTTPS proxy configurations.",
    subcommands = {
        ProxiesCommand.ListCommand.class,
        ProxiesCommand.ImportCommand.class,
        ProxiesCommand.ExportCommand.class
    }
)
public class ProxiesCommand implements Runnable
{
    @Override
    public void run()
    {
        System.out.println("Usage: axiom proxies [list|import|export]");
        System.out.println("Run 'axiom proxies --help' for details.");
    }

    static boolean init()
    {
        try { Database.init(); CryptoManager.init(); return true; }
        catch (Exception e) { System.err.println("Fatal: " + e.getMessage()); return false; }
    }

    // ── list ───────────────────────────────────────────────────────────────────

    @Command(name = "list", mixinStandardHelpOptions = true,
             description = "Print all proxies to stdout.")
    static class ListCommand implements Callable<Integer>
    {
        @Override
        public Integer call()
        {
            if (!init()) return 1;

            ProxyRepository   proxyRepo   = new ProxyRepository();
            AccountRepository accountRepo = new AccountRepository();

            List<Proxy>   proxies  = proxyRepo.findAll();
            List<Account> accounts = accountRepo.findAll();

            if (proxies.isEmpty()) { System.out.println("No proxies configured."); return 0; }

            // Count linked accounts per proxy
            Map<Integer, Long> linkedCounts = accounts.stream()
                .filter(a -> a.proxyId != null)
                .collect(Collectors.groupingBy(a -> a.proxyId, Collectors.counting()));

            String fmt = "  %-4s  %-22s  %-32s  %-14s  %s%n";
            System.out.printf(fmt, "ID", "Name", "Host:Port", "User", "Linked");
            System.out.println("  " + "─".repeat(82));

            for (Proxy p : proxies)
            {
                long linked = linkedCounts.getOrDefault(p.id, 0L);
                String user = p.username != null && !p.username.isEmpty() ? p.username : "—";
                String auth = linked == 1 ? "1 account" : linked + " accounts";
                System.out.printf(fmt,
                    p.id,
                    trunc(p.name, 22),
                    trunc(p.host + ":" + p.port, 32),
                    trunc(user, 14),
                    auth);
            }

            System.out.printf("%n  %d proxy/proxies.%n", proxies.size());
            return 0;
        }
    }

    // ── import ─────────────────────────────────────────────────────────────────

    @Command(name = "import", mixinStandardHelpOptions = true,
             description = {
                 "Import proxies from a CSV file.",
                 "CSV columns: name, host, port, username, password",
                 "Merges on name: updates existing, inserts new."
             })
    static class ImportCommand implements Callable<Integer>
    {
        @Option(names = "--file", required = true, description = "CSV file path.")
        private File file;

        @Override
        public Integer call()
        {
            if (!init()) return 1;

            if (!file.isFile())
            {
                System.err.println("Error: file not found: " + file.getAbsolutePath());
                return 1;
            }

            List<Map<String, String>> rows;
            try { rows = CsvUtil.read(file); }
            catch (Exception e) { System.err.println("Error reading CSV: " + e.getMessage()); return 1; }

            if (rows.isEmpty()) { System.out.println("CSV has no data rows."); return 0; }

            ProxyRepository proxyRepo = new ProxyRepository();

            Map<String, Proxy> existingByName = proxyRepo.findAll().stream()
                .collect(Collectors.toMap(p -> p.name.toLowerCase(), p -> p));

            int inserted = 0, updated = 0, failed = 0;

            for (int i = 0; i < rows.size(); i++)
            {
                Map<String, String> row = rows.get(i);
                String name = CsvUtil.get(row, "name");
                if (name.isEmpty())
                {
                    System.err.printf("  Row %d: 'name' is empty — skipped.%n", i + 1);
                    failed++;
                    continue;
                }

                try
                {
                    Proxy existing = existingByName.get(name.toLowerCase());
                    Proxy p        = existing != null ? existing : new Proxy();

                    p.name     = name;
                    p.host     = CsvUtil.get(row, "host");
                    p.username = CsvUtil.get(row, "username");

                    String portStr = CsvUtil.get(row, "port");
                    if (!portStr.isEmpty()) p.port = Integer.parseInt(portStr);

                    // Password: only overwrite if CSV provides a value
                    String pw = CsvUtil.get(row, "password");
                    if (!pw.isEmpty()) p.passwordEnc = pw;  // repo encrypts on write

                    if (existing != null)
                    {
                        proxyRepo.update(p);
                        System.out.printf("  Updated: %s%n", name);
                        updated++;
                    }
                    else
                    {
                        proxyRepo.insert(p);
                        System.out.printf("  Inserted: %s%n", name);
                        inserted++;
                    }
                }
                catch (Exception e)
                {
                    System.err.printf("  Row %d (%s): %s%n", i + 1, name, e.getMessage());
                    failed++;
                }
            }

            System.out.printf("%nImport complete: %d inserted, %d updated, %d failed.%n",
                inserted, updated, failed);
            return failed > 0 ? 1 : 0;
        }
    }

    // ── export ─────────────────────────────────────────────────────────────────

    @Command(name = "export", mixinStandardHelpOptions = true,
             description = {
                 "Export proxies to a CSV file.",
                 "Columns: name, host, port, username, password",
                 "password is written as plaintext (decrypted)."
             })
    static class ExportCommand implements Callable<Integer>
    {
        @Option(names = "--file", required = true, description = "Destination CSV file path.")
        private File file;

        @Override
        public Integer call()
        {
            if (!init()) return 1;

            ProxyRepository proxyRepo = new ProxyRepository();
            List<Proxy>     proxies   = proxyRepo.findAll();

            List<String>   headers = Arrays.asList("name", "host", "port", "username", "password");
            List<String[]> rows    = new ArrayList<>();

            for (Proxy p : proxies)
            {
                rows.add(new String[] {
                    nvl(p.name,        ""),
                    nvl(p.host,        ""),
                    String.valueOf(p.port),
                    nvl(p.username,    ""),
                    nvl(p.passwordEnc, "")  // already plaintext after mapRow() decrypt
                });
            }

            try
            {
                CsvUtil.write(file, headers, rows);
                System.out.printf("Exported %d proxy/proxies to %s%n",
                    proxies.size(), file.getAbsolutePath());
                return 0;
            }
            catch (Exception e)
            {
                System.err.println("Error writing CSV: " + e.getMessage());
                return 1;
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static String nvl(String s, String fallback) { return s != null ? s : fallback; }

    private static String trunc(String s, int max)
    {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
