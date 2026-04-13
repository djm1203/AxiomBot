package com.axiom.launcher.ui;

import com.axiom.api.script.ScriptManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarFile;

/**
 * Provides the list of available Axiom scripts.
 *
 * Primary source: scans JAR files in the configured scripts directory for
 * {@code @ScriptManifest} annotations. Only classes in the
 * {@code com/axiom/scripts/} package are inspected to avoid loading thousands
 * of bundled dependency classes from fat JARs.
 *
 * Fallback: if the directory is absent/empty or no scripts are found, the 12
 * built-in Axiom scripts are returned with hardcoded metadata.
 */
public final class ScriptRegistry
{
    private static final Logger log = LoggerFactory.getLogger(ScriptRegistry.class);

    private ScriptRegistry() {}

    /**
     * Loads the script list. Expands a leading {@code ~} in {@code scriptsDirPath}.
     *
     * @param scriptsDirPath path to the scripts directory (may start with {@code ~})
     * @return unmodifiable list of discovered scripts; never null, never empty
     */
    public static List<ScriptInfo> loadScripts(String scriptsDirPath)
    {
        String expanded = scriptsDirPath.replace("~", System.getProperty("user.home"));
        File   dir      = new File(expanded);

        if (dir.isDirectory())
        {
            File[] jars = dir.listFiles((d, n) -> n.endsWith(".jar"));
            if (jars != null && jars.length > 0)
            {
                List<ScriptInfo> found = new ArrayList<>();
                for (File jar : jars)
                {
                    try { found.addAll(scanJar(jar)); }
                    catch (Exception e)
                    {
                        log.warn("Could not scan script JAR {}: {}", jar.getName(), e.getMessage());
                    }
                }
                if (!found.isEmpty())
                {
                    log.info("ScriptRegistry: loaded {} script(s) from {}", found.size(), dir);
                    return Collections.unmodifiableList(found);
                }
            }
        }

        log.debug("ScriptRegistry: using built-in script list (no JARs found in {})", expanded);
        return getBuiltinScripts();
    }

    // ── Scanning ──────────────────────────────────────────────────────────────

    private static List<ScriptInfo> scanJar(File jar) throws Exception
    {
        List<ScriptInfo> result = new ArrayList<>();
        URL[] urls = { jar.toURI().toURL() };

        // Use the system classloader as parent so axiom-api annotations resolve
        try (URLClassLoader cl = new URLClassLoader(urls, ScriptRegistry.class.getClassLoader());
             JarFile jf = new JarFile(jar))
        {
            jf.stream()
              .filter(e -> e.getName().startsWith("com/axiom/scripts/")
                        && e.getName().endsWith(".class"))
              .forEach(entry ->
              {
                  String className = entry.getName()
                      .replace('/', '.')
                      .replace(".class", "");
                  try
                  {
                      Class<?> cls      = cl.loadClass(className);
                      ScriptManifest m  = cls.getAnnotation(ScriptManifest.class);
                      if (m != null)
                      {
                          result.add(new ScriptInfo(
                              m.name(),
                              m.category().name(),
                              m.version(),
                              m.author(),
                              m.description(),
                              emojiForName(m.name())
                          ));
                      }
                  }
                  catch (Throwable ignored) { /* dependency not on classpath — skip */ }
              });
        }
        return result;
    }

    // ── Built-in fallback ─────────────────────────────────────────────────────

    public static List<ScriptInfo> getBuiltinScripts()
    {
        return Arrays.asList(
            new ScriptInfo("Axiom Woodcutting", "SKILLING", "1.0", "Axiom",
                "Chops trees and banks or drops logs.", "🌲"),
            new ScriptInfo("Axiom Fishing", "SKILLING", "1.0", "Axiom",
                "Fishes at various spots and banks.", "🎣"),
            new ScriptInfo("Axiom Mining", "SKILLING", "1.0", "Axiom",
                "Mines ore and banks.", "⛏"),
            new ScriptInfo("Axiom Smithing", "SKILLING", "1.0", "Axiom",
                "Smelts bars and smiths items.", "🔨"),
            new ScriptInfo("Axiom Cooking", "SKILLING", "1.0", "Axiom",
                "Cooks raw food on a fire or range.", "🍳"),
            new ScriptInfo("Axiom Firemaking", "SKILLING", "1.0", "Axiom",
                "Burns logs for Firemaking XP.", "🔥"),
            new ScriptInfo("Axiom Fletching", "SKILLING", "1.0", "Axiom",
                "Cuts and strings bows.", "🏹"),
            new ScriptInfo("Axiom Crafting", "SKILLING", "1.0", "Axiom",
                "Crafts jewelry and leather items.", "🪡"),
            new ScriptInfo("Axiom Herblore", "SKILLING", "1.0", "Axiom",
                "Makes potions from herbs and secondaries.", "🌿"),
            new ScriptInfo("Axiom Alchemy", "SKILLING", "1.0", "Axiom",
                "High-alchemises items for Magic XP and GP.", "⚗"),
            new ScriptInfo("Axiom Thieving", "SKILLING", "1.0", "Axiom",
                "Pickpockets NPCs for Thieving XP and GP.", "🎭"),
            new ScriptInfo("Axiom Combat", "COMBAT", "1.0", "Axiom",
                "Fights monsters with configurable attack style.", "⚔")
        );
    }

    private static String emojiForName(String name)
    {
        if (name == null) return "📜";
        String lower = name.toLowerCase();
        if (lower.contains("woodcut"))  return "🌲";
        if (lower.contains("fish"))     return "🎣";
        if (lower.contains("mining"))   return "⛏";
        if (lower.contains("smith"))    return "🔨";
        if (lower.contains("cook"))     return "🍳";
        if (lower.contains("firemaking")) return "🔥";
        if (lower.contains("fletch"))   return "🏹";
        if (lower.contains("craft"))    return "🪡";
        if (lower.contains("herb"))     return "🌿";
        if (lower.contains("alchemy"))  return "⚗";
        if (lower.contains("thiev"))    return "🎭";
        if (lower.contains("combat"))   return "⚔";
        return "📜";
    }
}
