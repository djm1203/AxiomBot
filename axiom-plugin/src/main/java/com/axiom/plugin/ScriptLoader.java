package com.axiom.plugin;

import com.axiom.api.script.BotScript;
import com.axiom.api.script.ScriptManifest;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Discovers available scripts at startup.
 *
 * Phase 1: scans the classpath for classes annotated with @ScriptManifest.
 * All scripts are compiled into the plugin JAR so they are on the classpath.
 *
 * Phase 2 (future): will also scan ~/.axiom/scripts/*.jar via URLClassLoader,
 * enabling scripts to be deployed without recompiling the plugin.
 *
 * Usage:
 *   List<Class<? extends BotScript>> classes = ScriptLoader.discoverScripts();
 *   for (Class<? extends BotScript> cls : classes) {
 *       BotScript instance = cls.getDeclaredConstructor().newInstance();
 *       // register with panel
 *   }
 */
@Slf4j
public class ScriptLoader
{
    /**
     * All known script classes — populated at build time.
     *
     * Phase 1: explicit list since we compile scripts into the plugin JAR.
     * Phase 2: replace this with full classpath scanning + URLClassLoader JARs.
     */
    @SuppressWarnings("unchecked")
    private static final String[] KNOWN_SCRIPT_CLASSES = {
        "com.axiom.scripts.woodcutting.WoodcuttingScript",
    };

    /**
     * Returns a list of instantiated BotScript objects for all discovered scripts.
     * Each call returns fresh instances.
     */
    public static List<BotScript> loadScripts()
    {
        List<BotScript> scripts = new ArrayList<>();

        for (String className : KNOWN_SCRIPT_CLASSES)
        {
            try
            {
                Class<?> cls = Class.forName(className);

                if (!BotScript.class.isAssignableFrom(cls))
                {
                    log.warn("ScriptLoader: {} does not extend BotScript — skipping", className);
                    continue;
                }

                if (!cls.isAnnotationPresent(ScriptManifest.class))
                {
                    log.warn("ScriptLoader: {} has no @ScriptManifest — skipping", className);
                    continue;
                }

                BotScript instance = (BotScript) cls.getDeclaredConstructor().newInstance();
                scripts.add(instance);

                ScriptManifest manifest = cls.getAnnotation(ScriptManifest.class);
                log.info("ScriptLoader: loaded '{}' v{} [{}]",
                    manifest.name(), manifest.version(), manifest.category());
            }
            catch (ClassNotFoundException e)
            {
                log.warn("ScriptLoader: class not found: {} — is the script JAR on the classpath?", className);
            }
            catch (Exception e)
            {
                log.error("ScriptLoader: failed to instantiate {}: {}", className, e.getMessage(), e);
            }
        }

        log.info("ScriptLoader: loaded {} script(s)", scripts.size());
        return scripts;
    }
}
