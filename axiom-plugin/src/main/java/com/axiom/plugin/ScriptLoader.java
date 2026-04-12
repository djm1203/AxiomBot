package com.axiom.plugin;

import com.axiom.api.script.BotScript;
import com.axiom.api.script.ScriptManifest;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

/**
 * Discovers BotScript implementations at startup by scanning the plugin's own JAR.
 *
 * All script modules (axiom-woodcutting, axiom-fishing, …) are shaded into the plugin
 * JAR, so every script class is on the same classpath. We scan only the
 * "com/axiom/scripts/" subtree to avoid processing MigLayout, RuneLite stubs, and
 * other bundled libraries.
 *
 * A class is treated as a script if it:
 *   1. Is in the com.axiom.scripts.* package hierarchy
 *   2. Extends BotScript (transitively)
 *   3. Is annotated with @ScriptManifest
 *   4. Is concrete (not abstract, not an interface)
 *   5. Has a no-arg constructor
 *
 * Adding a new script requires:
 *   - Add its module to axiom-scripts/pom.xml and axiom-plugin/pom.xml
 *   - No changes to this file or AxiomPanel
 */
@Slf4j
public class ScriptLoader
{
    private static final String SCRIPT_PACKAGE_PATH = "com/axiom/scripts/";

    /**
     * Returns a list of instantiated BotScript objects for all discovered scripts.
     * Each call returns fresh instances. Executed once at panel construction time.
     */
    public static List<BotScript> loadScripts()
    {
        List<BotScript> scripts = new ArrayList<>();

        try
        {
            URL jarUrl = ScriptLoader.class.getProtectionDomain()
                .getCodeSource()
                .getLocation();

            try (JarFile jar = new JarFile(new File(jarUrl.toURI())))
            {
                jar.stream()
                    .filter(e -> e.getName().endsWith(".class")
                              && !e.getName().contains("$")
                              && e.getName().startsWith(SCRIPT_PACKAGE_PATH))
                    .forEach(e ->
                    {
                        String className = e.getName()
                            .replace("/", ".")
                            .replace(".class", "");

                        try
                        {
                            // Load without initialising — annotations are readable
                            // without running static blocks.
                            Class<?> cls = Class.forName(
                                className, false, ScriptLoader.class.getClassLoader());

                            if (!BotScript.class.isAssignableFrom(cls)) return;
                            if (cls.isInterface())                        return;
                            if (Modifier.isAbstract(cls.getModifiers())) return;
                            if (!cls.isAnnotationPresent(ScriptManifest.class)) return;

                            // Instantiate (triggers static initialisation here).
                            BotScript instance = (BotScript) cls
                                .getDeclaredConstructor()
                                .newInstance();
                            scripts.add(instance);

                            ScriptManifest m = cls.getAnnotation(ScriptManifest.class);
                            log.info("ScriptLoader: loaded '{}' v{} [{}]",
                                m.name(), m.version(), m.category());
                        }
                        catch (ClassNotFoundException | NoClassDefFoundError ignored)
                        {
                            // Expected for entries that reference excluded dependencies
                        }
                        catch (Exception ex)
                        {
                            log.error("ScriptLoader: failed to instantiate {}: {}",
                                className, ex.getMessage(), ex);
                        }
                    });
            }
        }
        catch (Exception e)
        {
            log.error("ScriptLoader: JAR scan failed — no scripts loaded: {}", e.getMessage(), e);
        }

        log.info("ScriptLoader: discovered {} script(s)", scripts.size());
        return scripts;
    }
}
