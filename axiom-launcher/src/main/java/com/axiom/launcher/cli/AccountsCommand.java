package com.axiom.launcher.cli;

import com.axiom.launcher.db.Account;
import com.axiom.launcher.db.AccountRepository;
import com.axiom.launcher.db.Database;
import com.axiom.launcher.db.Proxy;
import com.axiom.launcher.db.ProxyRepository;
import com.axiom.launcher.security.CryptoManager;
import com.axiom.launcher.util.CsvValidator;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * axiom accounts — CRUD and CSV import/export for managed OSRS accounts.
 *
 * Subcommands: list, import, export
 */
@Command(
    name = "accounts",
    mixinStandardHelpOptions = true,
    description = "Manage OSRS accounts.",
    subcommands = {
        AccountsCommand.ListCommand.class,
        AccountsCommand.ImportCommand.class,
        AccountsCommand.ExportCommand.class
    }
)
public class AccountsCommand implements Runnable
{
    @Override
    public void run()
    {
        System.out.println("Usage: axiom accounts [list|import|export]");
        System.out.println("Run 'axiom accounts --help' for details.");
    }

    // ── Shared init helper ─────────────────────────────────────────────────────

    static boolean init()
    {
        try { Database.init(); CryptoManager.init(); return true; }
        catch (Exception e) { System.err.println("Fatal: " + e.getMessage()); return false; }
    }

    // ── list ───────────────────────────────────────────────────────────────────

    @Command(name = "list", mixinStandardHelpOptions = true,
             description = "Print all accounts to stdout.")
    static class ListCommand implements Callable<Integer>
    {
        @Override
        public Integer call()
        {
            if (!init()) return 1;

            AccountRepository accountRepo = new AccountRepository();
            ProxyRepository   proxyRepo   = new ProxyRepository();

            // Build proxy name lookup
            Map<Integer, String> proxyNames = proxyRepo.findAll().stream()
                .collect(Collectors.toMap(p -> p.id, p -> p.name));

            List<Account> accounts = accountRepo.findAll();

            if (accounts.isEmpty())
            {
                System.out.println("No accounts configured.");
                return 0;
            }

            String fmt = "  %-4s  %-26s  %-20s  %-4s  %s%n";
            System.out.printf(fmt, "ID", "Display Name", "Jagex ID", "World", "Proxy");
            System.out.println("  " + "─".repeat(78));

            for (Account a : accounts)
            {
                String jagexId  = nvl(a.jagexCharacterId, "—");
                String proxy    = a.proxyId != null ? proxyNames.getOrDefault(a.proxyId, "id:" + a.proxyId) : "—";
                String world    = a.preferredWorld == 0 ? "any" : String.valueOf(a.preferredWorld);
                System.out.printf(fmt, a.id, trunc(a.displayName, 26), trunc(jagexId, 20), world, proxy);
            }

            System.out.printf("%n  %d account(s).%n", accounts.size());
            return 0;
        }
    }

    // ── import ─────────────────────────────────────────────────────────────────

    @Command(name = "import", mixinStandardHelpOptions = true,
             description = {
                 "Import accounts from a CSV file.",
                 "CSV columns: display_name, jagex_character_id, proxy_name, preferred_world, bank_pin",
                 "Merges on display_name: updates existing, inserts new."
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

            AccountRepository accountRepo = new AccountRepository();
            ProxyRepository   proxyRepo   = new ProxyRepository();

            // Build lookup maps
            Map<String, Account> existingByName = accountRepo.findAll().stream()
                .collect(Collectors.toMap(a -> a.displayName.toLowerCase(), a -> a));
            Map<String, Integer> proxyIdByName  = proxyRepo.findAll().stream()
                .collect(Collectors.toMap(p -> p.name.toLowerCase(), p -> p.id));

            int inserted = 0, updated = 0, failed = 0;

            for (int i = 0; i < rows.size(); i++)
            {
                Map<String, String> row = rows.get(i);
                String displayName = CsvUtil.get(row, "display_name").trim();
                if (displayName.isEmpty())
                {
                    System.err.printf("  Row %d: display_name is empty — skipped.%n", i + 1);
                    failed++;
                    continue;
                }

                // Validate before touching the database
                CsvValidator.ValidationResult validation =
                    CsvValidator.validateAccount(row, proxyIdByName.keySet());
                if (!validation.valid)
                {
                    System.err.printf("  Row %d (%s): %s — skipped.%n", i + 1, displayName, validation.error);
                    failed++;
                    continue;
                }
                if (validation.warning != null)
                {
                    System.out.printf("  Row %d (%s): warning — %s%n", i + 1, displayName, validation.warning);
                }

                try
                {
                    Account existing = existingByName.get(displayName.toLowerCase());
                    Account a        = existing != null ? existing : new Account();

                    a.displayName      = displayName;
                    a.jagexCharacterId = CsvUtil.get(row, "jagex_character_id");
                    a.preferredWorld   = CsvUtil.getInt(row, "preferred_world", 0);
                    a.notes            = CsvUtil.get(row, "notes");

                    // Bank PIN: only overwrite if the CSV provides a value
                    String pin = CsvUtil.get(row, "bank_pin");
                    if (!pin.isEmpty()) a.bankPinEnc = pin;  // repo encrypts on write

                    // Proxy: resolve by name; leave proxyId unchanged if column absent
                    String proxyName = CsvUtil.get(row, "proxy_name");
                    if (!proxyName.isEmpty())
                        a.proxyId = proxyIdByName.getOrDefault(proxyName.toLowerCase(), null);

                    if (existing != null)
                    {
                        accountRepo.update(a);
                        System.out.printf("  Updated: %s%n", displayName);
                        updated++;
                    }
                    else
                    {
                        accountRepo.insert(a);
                        System.out.printf("  Inserted: %s%n", displayName);
                        inserted++;
                    }
                }
                catch (Exception e)
                {
                    System.err.printf("  Row %d (%s): %s%n", i + 1, displayName, e.getMessage());
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
                 "Export accounts to a CSV file.",
                 "Columns: display_name, jagex_character_id, proxy_name, preferred_world, bank_pin",
                 "bank_pin is written as plaintext (decrypted)."
             })
    static class ExportCommand implements Callable<Integer>
    {
        @Option(names = "--file", required = true, description = "Destination CSV file path.")
        private File file;

        @Override
        public Integer call()
        {
            if (!init()) return 1;

            AccountRepository accountRepo = new AccountRepository();
            ProxyRepository   proxyRepo   = new ProxyRepository();

            Map<Integer, String> proxyNames = proxyRepo.findAll().stream()
                .collect(Collectors.toMap(p -> p.id, p -> p.name));

            List<Account> accounts = accountRepo.findAll();
            List<String>  headers  = Arrays.asList(
                "display_name", "jagex_character_id", "proxy_name", "preferred_world", "bank_pin");
            List<String[]> rows = new ArrayList<>();

            for (Account a : accounts)
            {
                rows.add(new String[] {
                    nvl(a.displayName,      ""),
                    nvl(a.jagexCharacterId, ""),
                    a.proxyId != null ? proxyNames.getOrDefault(a.proxyId, "") : "",
                    String.valueOf(a.preferredWorld),
                    nvl(a.bankPinEnc, "")   // already plaintext after mapRow() decrypt
                });
            }

            try
            {
                CsvUtil.write(file, headers, rows);
                System.out.printf("Exported %d account(s) to %s%n",
                    accounts.size(), file.getAbsolutePath());
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
