package com.axiom.launcher.cli;

import picocli.CommandLine.Command;

/**
 * Root picocli command for the Axiom Launcher CLI.
 *
 * Invoked when the launcher is started with any command-line arguments:
 *   java -jar axiom-launcher.jar [subcommand] [options]
 *
 * Subcommands:
 *   run           — launch a single RuneLite client
 *   bulk-launch   — launch multiple clients from a CSV file
 *   accounts      — list / import / export OSRS accounts
 *   proxies       — list / import / export proxy configurations
 */
@Command(
    name                   = "axiom",
    mixinStandardHelpOptions = true,
    version                = "Axiom Launcher 1.0",
    description            = "Multi-client OSRS launcher and bot manager.",
    subcommands            = {
        RunCommand.class,
        BulkLaunchCommand.class,
        AccountsCommand.class,
        ProxiesCommand.class
    }
)
public class AxiomCLI implements Runnable
{
    @Override
    public void run()
    {
        System.out.println("Axiom Launcher CLI — use --help for available commands.");
    }
}
