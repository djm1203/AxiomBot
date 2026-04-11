package com.axiom.api.game;

/**
 * Widget / dialog utilities.
 *
 * Centralizes all dialog handling so scripts never deal with widget IDs directly.
 * This is the fix for the old Fletching/Cooking breakage where every script had
 * its own ad-hoc widget handling.
 *
 * Implementation lives in axiom-plugin (wraps net.runelite.api.Client widget tree).
 *
 * Key widget group IDs (for reference — scripts should not import these):
 *   270 — production dialog (Make X / Make All)
 *   162 — chat dialog
 *   309 — dialogue box
 */
public interface Widgets
{
    /**
     * Returns true if the production dialog (Make X / Make All) is open.
     * This is the dialog shown when using a skill that produces items
     * (Fletching, Cooking, Herblore, Crafting, Smithing).
     */
    boolean isMakeDialogOpen();

    /**
     * Clicks "Make All" on the production dialog.
     * No-op if the dialog is not open.
     */
    void clickMakeAll();

    /**
     * Clicks "Make X" on the production dialog and types the given quantity.
     * No-op if the dialog is not open.
     */
    void clickMakeX(int quantity);

    /**
     * Returns true if a widget group with the given group ID is visible.
     * For custom dialog detection when isMakeDialogOpen() is not sufficient.
     */
    boolean isDialogOpen(int groupId);

    /**
     * Clicks the first dialog option whose text contains the given string
     * (case-insensitive). Use for NPC dialog choices.
     */
    void clickDialogOption(String text);

    /**
     * Returns true if the level-up dialog is currently shown.
     * Scripts can use this to detect skill level-ups without subscribing to events.
     */
    boolean isLevelUpDialogOpen();

    /** Dismisses the level-up dialog if it is open. */
    void dismissLevelUpDialog();
}
