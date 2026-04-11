package com.axiom.api.interaction;

/**
 * Explicit menu action API for cases where the standard Interaction methods
 * are insufficient (e.g. specific option + target string combinations).
 * Implementation lives in axiom-plugin.
 */
public interface Menu
{
    /**
     * Fires a menu action by option text and target text.
     * Searches the current menu entries for a matching entry and fires it.
     *
     * @param option  the option text (e.g. "Attack", "Talk-to", "Use")
     * @param target  the target text (e.g. "Man", "Oak tree", "") — empty string matches any
     * @return true if a matching entry was found and fired
     */
    boolean clickOption(String option, String target);
}
