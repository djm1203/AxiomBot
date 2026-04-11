package com.axiom.plugin.impl.game;

import com.axiom.api.game.SceneObject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Perspective;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
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
        // Follow the impostor chain — bank booths and other contextual objects store
        // their real actions (e.g. "Bank") on the impostor, not the raw definition.
        // getImpostor() can throw NPE internally (varbit lookup) for objects without
        // varbit state, so guard with try-catch rather than just a null check.
        if (def != null)
        {
            try
            {
                ObjectComposition impostor = def.getImpostor();
                if (impostor != null) def = impostor;
            }
            catch (Exception ignored) { /* no impostor — use raw def */ }
        }
        this.name    = (def != null && def.getName() != null) ? def.getName() : "";
        // Preserve the original array including null slots — the slot index maps directly
        // to GAME_OBJECT_FIRST/SECOND/THIRD_OPTION in menuAction. Filtering nulls would
        // shift non-null entries and send the wrong MenuAction type.
        // hasAction() and findActionIndex() perform null checks at use time.
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
            if (a != null && action.equalsIgnoreCase(a)) return true;
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

        log.info("interact: '{}' idx={} on {} (id={}) actions={}",
            action, actionIdx, name, id, java.util.Arrays.toString(actions));

        if (npc != null)
        {
            moveMouseTo(npc.getLocalLocation());
            client.menuAction(
                0, 0,
                npcMenuAction(actionIdx),
                npc.getIndex(), 0,
                action, name
            );
        }
        else if (tileObject != null)
        {
            // Scene tile coordinates:
            //   GameObject.getSceneMinLocation() returns net.runelite.api.Point (x=sceneX, y=sceneY)
            //   for the southwest tile corner — correct for multi-tile objects.
            //   Other TileObject types use LocalPoint.getSceneX/Y directly.
            int sceneX, sceneY;
            if (tileObject instanceof GameObject)
            {
                net.runelite.api.Point min = ((GameObject) tileObject).getSceneMinLocation();
                sceneX = min.getX();
                sceneY = min.getY();
            }
            else
            {
                LocalPoint lp = tileObject.getLocalLocation();
                sceneX = lp.getSceneX();
                sceneY = lp.getSceneY();
            }

            // Move mouse to the object's tile center before firing the action.
            moveMouseTo(tileObject.getLocalLocation());

            client.menuAction(
                sceneX, sceneY,
                objectMenuAction(actionIdx),
                tileObject.getId(), 0,
                action, name
            );
        }
    }

    /**
     * Moves the system mouse cursor to the canvas position of the given local point.
     * RuneLite's OSRS client validates that the cursor is plausible near the target
     * before processing a menuAction, so we position it on the object's tile first.
     */
    private void moveMouseTo(LocalPoint localPt)
    {
        try
        {
            net.runelite.api.Point pt = Perspective.localToCanvas(
                client, localPt, client.getPlane());
            if (pt == null) return;

            java.awt.Canvas canvas = client.getCanvas();
            if (canvas == null) return;

            java.awt.Point origin = canvas.getLocationOnScreen();
            new java.awt.Robot().mouseMove(origin.x + pt.getX(), origin.y + pt.getY());
        }
        catch (Exception e)
        {
            log.warn("SceneObjectWrapper: mouse move failed: {}", e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int findActionIndex(String action)
    {
        for (int i = 0; i < actions.length; i++)
        {
            if (actions[i] != null && action.equalsIgnoreCase(actions[i])) return i;
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
