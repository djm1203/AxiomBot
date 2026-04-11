package com.axiom.plugin.impl.game;

import com.axiom.api.game.SceneObject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;

/**
 * Wraps a RuneLite TileObject or NPC into the axiom-api SceneObject interface.
 * All interact() calls go through client.menuAction() — no mouse movement.
 */
@Slf4j
public class SceneObjectWrapper implements SceneObject
{
    private final Client client;

    // One of these will be set (never both)
    private final TileObject tileObject;
    private final NPC        npc;

    // Cached data so we don't call client methods off the game thread
    private final int    id;
    private final String name;
    private final int    worldX;
    private final int    worldY;
    private final int    plane;
    private final String[] actions;

    /** Wrap a TileObject (game object, wall, decorative, ground). */
    public SceneObjectWrapper(Client client, TileObject tileObject)
    {
        this.client     = client;
        this.tileObject = tileObject;
        this.npc        = null;

        this.id    = tileObject.getId();
        WorldPoint wp = tileObject.getWorldLocation();
        this.worldX = wp.getX();
        this.worldY = wp.getY();
        this.plane  = wp.getPlane();

        ObjectComposition def = client.getObjectDefinition(id);
        this.name    = (def != null && def.getName() != null) ? def.getName() : "";
        this.actions = (def != null && def.getActions() != null) ? def.getActions() : new String[0];
    }

    /** Wrap an NPC. */
    public SceneObjectWrapper(Client client, NPC npc)
    {
        this.client     = client;
        this.tileObject = null;
        this.npc        = npc;

        this.id    = npc.getId();
        WorldPoint wp = npc.getWorldLocation();
        this.worldX = wp.getX();
        this.worldY = wp.getY();
        this.plane  = wp.getPlane();
        this.name    = npc.getName() != null ? npc.getName() : "";

        net.runelite.api.NPCComposition def = client.getNpcDefinition(id);
        this.actions = (def != null && def.getActions() != null) ? def.getActions() : new String[0];
    }

    @Override public int    getId()     { return id; }
    @Override public String getName()   { return name; }
    @Override public int    getWorldX() { return worldX; }
    @Override public int    getWorldY() { return worldY; }
    @Override public int    getPlane()  { return plane; }

    @Override
    public String[] getActions() { return actions; }

    @Override
    public boolean hasAction(String action)
    {
        for (String a : actions)
        {
            if (action.equalsIgnoreCase(a)) return true;
        }
        return false;
    }

    @Override
    public void interact(String action)
    {
        int actionIdx = findActionIndex(action);
        if (actionIdx < 0)
        {
            log.warn("SceneObjectWrapper: action '{}' not found on {} (id={})", action, name, id);
            return;
        }

        if (npc != null)
        {
            // NPC interaction
            client.menuAction(
                0, 0,
                npcMenuAction(actionIdx),
                npc.getIndex(), -1,
                action, name
            );
        }
        else if (tileObject != null)
        {
            // Game object / TileObject interaction
            client.menuAction(
                tileObject.getLocalLocation().getSceneX(),
                tileObject.getLocalLocation().getSceneY(),
                objectMenuAction(actionIdx),
                tileObject.getId(), -1,
                action, name
            );
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int findActionIndex(String action)
    {
        for (int i = 0; i < actions.length; i++)
        {
            if (action.equalsIgnoreCase(actions[i])) return i;
        }
        return -1;
    }

    /** Maps action index (0-based) to RuneLite NPC MenuAction. */
    private static MenuAction npcMenuAction(int idx)
    {
        switch (idx)
        {
            case 0: return MenuAction.NPC_FIRST_OPTION;
            case 1: return MenuAction.NPC_SECOND_OPTION;
            case 2: return MenuAction.NPC_THIRD_OPTION;
            case 3: return MenuAction.NPC_FOURTH_OPTION;
            case 4: return MenuAction.NPC_FIFTH_OPTION;
            default: return MenuAction.NPC_FIRST_OPTION;
        }
    }

    /** Maps action index (0-based) to RuneLite GameObject MenuAction. */
    private static MenuAction objectMenuAction(int idx)
    {
        switch (idx)
        {
            case 0: return MenuAction.GAME_OBJECT_FIRST_OPTION;
            case 1: return MenuAction.GAME_OBJECT_SECOND_OPTION;
            case 2: return MenuAction.GAME_OBJECT_THIRD_OPTION;
            case 3: return MenuAction.GAME_OBJECT_FOURTH_OPTION;
            case 4: return MenuAction.GAME_OBJECT_FIFTH_OPTION;
            default: return MenuAction.GAME_OBJECT_FIRST_OPTION;
        }
    }
}
