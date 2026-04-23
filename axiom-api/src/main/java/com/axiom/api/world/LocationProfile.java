package com.axiom.api.world;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal runtime location profile for scripts that work from a known area and
 * need to return there after banking or recovery.
 */
public final class LocationProfile
{
    private final String        name;
    private final WorldAreaRect workArea;
    private final WorldTile     returnAnchor;
    private final WorldTile     bankAnchor;
    private final String[]      bankObjectNames;
    private final String[]      bankNpcNames;
    private final String[]      bankActions;
    private final WorldTile[]   resetPath;
    private final Map<String, String> interactionOverrides;

    public LocationProfile(String name, WorldAreaRect workArea, WorldTile returnAnchor, WorldTile bankAnchor)
    {
        this(name, workArea, returnAnchor, bankAnchor, new String[0], new String[0], new String[0], new WorldTile[0]);
    }

    public LocationProfile(
        String name,
        WorldAreaRect workArea,
        WorldTile returnAnchor,
        WorldTile bankAnchor,
        String[] bankObjectNames,
        String[] bankNpcNames,
        String[] bankActions,
        WorldTile[] resetPath)
    {
        this(name, workArea, returnAnchor, bankAnchor,
            bankObjectNames, bankNpcNames, bankActions, resetPath, Collections.emptyMap());
    }

    public LocationProfile(
        String name,
        WorldAreaRect workArea,
        WorldTile returnAnchor,
        WorldTile bankAnchor,
        String[] bankObjectNames,
        String[] bankNpcNames,
        String[] bankActions,
        WorldTile[] resetPath,
        Map<String, String> interactionOverrides)
    {
        this.name         = name;
        this.workArea     = workArea;
        this.returnAnchor = returnAnchor;
        this.bankAnchor   = bankAnchor;
        this.bankObjectNames = bankObjectNames != null ? bankObjectNames.clone() : new String[0];
        this.bankNpcNames    = bankNpcNames != null ? bankNpcNames.clone() : new String[0];
        this.bankActions     = bankActions != null ? bankActions.clone() : new String[0];
        this.resetPath       = resetPath != null ? resetPath.clone() : new WorldTile[0];
        this.interactionOverrides = interactionOverrides == null || interactionOverrides.isEmpty()
            ? Collections.emptyMap()
            : Collections.unmodifiableMap(new HashMap<>(interactionOverrides));
    }

    public static LocationProfile centered(String name, WorldTile center, int workAreaRadius)
    {
        return new LocationProfile(
            name,
            WorldAreaRect.centeredOn(center, workAreaRadius),
            center,
            null
        );
    }

    public String getName() { return name; }

    public WorldAreaRect getWorkArea() { return workArea; }

    public WorldTile getReturnAnchor() { return returnAnchor; }

    public WorldTile getBankAnchor() { return bankAnchor; }

    public String[] getBankObjectNames() { return bankObjectNames.clone(); }

    public String[] getBankNpcNames() { return bankNpcNames.clone(); }

    public String[] getBankActions() { return bankActions.clone(); }

    public WorldTile[] getResetPath() { return resetPath.clone(); }

    public Map<String, String> getInteractionOverrides() { return interactionOverrides; }

    public boolean hasBankAnchor()
    {
        return bankAnchor != null;
    }

    public boolean hasPreferredBankTargets()
    {
        return bankObjectNames.length > 0 || bankNpcNames.length > 0 || bankActions.length > 0;
    }

    public boolean hasResetPath()
    {
        return resetPath.length > 0;
    }

    public String resolveInteractionAction(String interactionKey, String fallbackAction)
    {
        if (interactionKey == null || interactionKey.isBlank())
        {
            return fallbackAction;
        }

        String override = interactionOverrides.get(interactionKey);
        return override != null && !override.isBlank() ? override : fallbackAction;
    }

    public boolean isInWorkArea(WorldTile tile)
    {
        return workArea != null && workArea.contains(tile);
    }

    public boolean shouldReturnToWorkArea(WorldTile tile)
    {
        return !isInWorkArea(tile);
    }

    public LocationProfile withBankAnchor(WorldTile newBankAnchor)
    {
        return new LocationProfile(name, workArea, returnAnchor, newBankAnchor,
            bankObjectNames, bankNpcNames, bankActions, resetPath, interactionOverrides);
    }

    public LocationProfile withBankTargets(String[] newBankObjectNames, String[] newBankNpcNames, String[] newBankActions)
    {
        return new LocationProfile(name, workArea, returnAnchor, bankAnchor,
            newBankObjectNames, newBankNpcNames, newBankActions, resetPath, interactionOverrides);
    }

    public LocationProfile withResetPath(WorldTile... newResetPath)
    {
        return new LocationProfile(name, workArea, returnAnchor, bankAnchor,
            bankObjectNames, bankNpcNames, bankActions, newResetPath, interactionOverrides);
    }

    public LocationProfile withInteractionOverride(String interactionKey, String action)
    {
        Map<String, String> overrides = new HashMap<>(interactionOverrides);
        if (interactionKey != null && !interactionKey.isBlank() && action != null && !action.isBlank())
        {
            overrides.put(interactionKey, action);
        }
        return new LocationProfile(name, workArea, returnAnchor, bankAnchor,
            bankObjectNames, bankNpcNames, bankActions, resetPath, overrides);
    }
}
