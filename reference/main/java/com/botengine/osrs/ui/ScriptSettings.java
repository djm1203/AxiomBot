package com.botengine.osrs.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Abstract base class for all per-script settings POJOs.
 *
 * Each script ships a concrete subclass (e.g. {@code CombatSettings}) that
 * contains all of that script's configuration fields as plain Java properties.
 *
 * Settings are persisted as JSON to:
 *   {@code ~/.runelite/axiom/<scriptName>.json}
 *
 * All fields must have sensible defaults so scripts work correctly even when
 * no profile has ever been saved (first run, or deleted profile).
 *
 * Usage:
 *   // Save after dialog "Start" is clicked:
 *   settings.save("Combat");
 *
 *   // Load at dialog open:
 *   CombatSettings s = ScriptSettings.load(CombatSettings.class, "Combat");
 */
@Slf4j
public abstract class ScriptSettings
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String SAVE_DIR =
        System.getProperty("user.home")
            + File.separator + ".runelite"
            + File.separator + "axiom"
            + File.separator;

    // ── Save ──────────────────────────────────────────────────────────────────

    /**
     * Serializes this settings object to JSON and writes it to disk.
     * Creates the {@code ~/.runelite/axiom/} directory if it does not exist.
     *
     * @param scriptName the script's display name (e.g. "Combat", "Woodcutting")
     */
    public void save(String scriptName)
    {
        File dir = new File(SAVE_DIR);
        if (!dir.exists() && !dir.mkdirs())
        {
            log.error("ScriptSettings: could not create save directory: {}", dir.getAbsolutePath());
            return;
        }

        File file = settingsFile(scriptName);
        try (FileWriter writer = new FileWriter(file))
        {
            GSON.toJson(this, writer);
            log.debug("ScriptSettings: saved {} → {}", scriptName, file.getAbsolutePath());
        }
        catch (IOException e)
        {
            log.error("ScriptSettings: failed to save {}: {}", scriptName, e.getMessage());
        }
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    /**
     * Deserializes a settings object from disk.
     * Returns a freshly constructed default instance if no file exists.
     *
     * @param type       the concrete settings class (e.g. {@code CombatSettings.class})
     * @param scriptName the script's display name
     * @param <T>        the settings type
     * @return the loaded or default settings instance
     */
    public static <T extends ScriptSettings> T load(Class<T> type, String scriptName)
    {
        File file = settingsFile(scriptName);
        if (!file.exists())
        {
            log.debug("ScriptSettings: no saved profile for {} — using defaults", scriptName);
            return createDefault(type);
        }

        try (FileReader reader = new FileReader(file))
        {
            T loaded = GSON.fromJson(reader, type);
            if (loaded == null) return createDefault(type);
            log.debug("ScriptSettings: loaded {} from {}", scriptName, file.getAbsolutePath());
            return loaded;
        }
        catch (IOException e)
        {
            log.error("ScriptSettings: failed to load {}: {}", scriptName, e.getMessage());
            return createDefault(type);
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private static File settingsFile(String scriptName)
    {
        String safeName = scriptName.replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase();
        return new File(SAVE_DIR + safeName + ".json");
    }

    private static <T extends ScriptSettings> T createDefault(Class<T> type)
    {
        try
        {
            return type.getDeclaredConstructor().newInstance();
        }
        catch (Exception e)
        {
            log.error("ScriptSettings: could not create default instance of {}", type.getSimpleName(), e);
            return null;
        }
    }
}
