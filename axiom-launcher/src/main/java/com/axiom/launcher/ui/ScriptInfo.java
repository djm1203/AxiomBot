package com.axiom.launcher.ui;

/**
 * Lightweight descriptor for an Axiom script, populated either by scanning a
 * script JAR's @ScriptManifest annotation or from the built-in fallback list.
 */
public class ScriptInfo
{
    public final String name;
    public final String category;    // ScriptCategory.name() — "SKILLING", "COMBAT", etc.
    public final String version;
    public final String author;
    public final String description;
    public final String emoji;       // Display icon for the script card

    public ScriptInfo(String name, String category, String version,
                      String author, String description, String emoji)
    {
        this.name        = name;
        this.category    = category;
        this.version     = version;
        this.author      = author;
        this.description = description;
        this.emoji       = emoji;
    }

    @Override
    public String toString() { return name; }
}
