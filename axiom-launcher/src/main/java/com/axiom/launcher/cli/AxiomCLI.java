package com.axiom.launcher.cli;

import picocli.CommandLine.Command;

/**
 * Root picocli command for the Axiom Launcher CLI.
 *
 * Invoked when the launcher is started with any command-line arguments.
 * Subcommands (run, bulk-launch, accounts, proxies) will be added in a
 * follow-up prompt.
 *
 * Usage:
 *   java -jar axiom-launcher.jar --help
 *   java -jar axiom-launcher.jar run --account 1 --script "Axiom Fishing"
 */
@Command(
    name            = "axiom",
    mixinStandardHelpOptions = true,
    version         = "Axiom Launcher 1.0",
    description     = "Multi-client OSRS launcher and bot manager.",
    subcommands     = {}   // subcommands registered here in follow-up prompts
)
public class AxiomCLI implements Runnable
{
    @Override
    public void run()
    {
        System.out.println("Axiom Launcher CLI — use --help for available commands.");
    }
}
