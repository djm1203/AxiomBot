package com.axiom.launcher;

import com.axiom.launcher.cli.AxiomCLI;
import picocli.CommandLine;

/**
 * Fat-JAR entry point.
 *
 * Must NOT extend {@link javafx.application.Application}. When a class that
 * extends Application is set as the MANIFEST.MF Main-Class, the JVM launcher
 * detects it as a JavaFX app and validates native library availability before
 * {@code main()} is ever called — which means {@code java -jar axiom.jar --help}
 * would fail with "JavaFX runtime components are missing" even though the CLI
 * path has no JavaFX dependency.
 *
 * This class routes CLI args to picocli before touching any JavaFX class, and
 * only calls {@link AxiomLauncher#main(String[])} (which extends Application)
 * when running in GUI mode.
 */
public class Main
{
    public static void main(String[] args)
    {
        if (args.length > 0)
        {
            // CLI mode — no JavaFX classes are loaded on this path
            int exitCode = new CommandLine(new AxiomCLI()).execute(args);
            System.exit(exitCode);
        }

        // GUI mode — delegate to AxiomLauncher (extends Application)
        AxiomLauncher.main(args);
    }
}
