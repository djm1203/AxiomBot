package com.botengine.osrs.api;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.WidgetInfo;

import javax.inject.Inject;

/**
 * API for querying and controlling player movement.
 *
 * <p>Walking in OSRS is triggered by a "Walk here" menu action. RuneLite
 * represents a destination as a {@link WorldPoint} (absolute tile coordinates),
 * but the client expects scene-local coordinates when invoking walk actions.
 * {@link LocalPoint#fromWorld(Client, WorldPoint)} handles that conversion.
 *
 * <p>Run mode is controlled by the run-energy varbit (173). The run orb widget
 * ({@link WidgetInfo#MINIMAP_RUN_ORB}) can be clicked to toggle it on or off.
 *
 * <p>Run energy is returned by {@link Client#getEnergy()} on a 0–10000 scale
 * (divide by 100 to get a 0–100 percentage).
 *
 * <p>All methods must be called on the client thread.
 */
@Slf4j
public class Movement
{
    /**
     * Varbit ID for the run-energy toggle.
     * Value 1 = run enabled, 0 = walk enabled.
     */
    private static final int RUN_VARP_ID = 173;

    /**
     * Approximate idle pose animation ID for a player with default equipment.
     * This value varies slightly by equipped items; pose 813 is the bare minimum.
     */
    private static final int IDLE_POSE_ANIMATION = 813;

    /**
     * Walking pose animation ID (default equipment, no weight).
     */
    private static final int WALK_POSE_ANIMATION = 819;

    /**
     * Running pose animation ID (default equipment, no weight).
     */
    private static final int RUN_POSE_ANIMATION = 824;

    private final Client client;

    @Inject
    public Movement(Client client)
    {
        this.client = client;
    }

    // ── Walking ───────────────────────────────────────────────────────────────

    /**
     * Instructs the player to walk to the given world tile.
     *
     * <p>Converts the {@link WorldPoint} to scene-local coordinates via
     * {@link LocalPoint#fromWorld(Client, WorldPoint)} before invoking the
     * "Walk here" menu action. If the destination is outside the loaded scene
     * (more than ~52 tiles from the player), {@code fromWorld} returns null
     * and the walk is skipped with a warning.
     *
     * <p>Walking in OSRS is tile-by-tile; the player will path-find to the
     * nearest reachable tile if the exact destination is blocked.
     *
     * @param point the world tile to walk to
     */
    public void walkTo(WorldPoint point)
    {
        LocalPoint local = LocalPoint.fromWorld(client, point);
        if (local == null)
        {
            log.warn("walkTo: destination {} is outside the loaded scene", point);
            return;
        }
        log.debug("Walking to world={} scene=({},{})", point, local.getSceneX(), local.getSceneY());
        client.menuAction(
            local.getSceneX(), local.getSceneY(),
            MenuAction.WALK,
            0, -1,
            "Walk here", ""
        );
    }

    // ── Movement state ────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the player is currently walking or running.
     *
     * <p>Checks the local player's pose animation against the known walk and
     * run animation IDs. A player doing a skilling animation (e.g. woodcutting)
     * while standing still will not be considered moving because their pose
     * animation will be the idle pose rather than a locomotion one.
     *
     * <p>Note: pose animation IDs can differ slightly based on equipped gear.
     * The constants used here ({@value #WALK_POSE_ANIMATION} and
     * {@value #RUN_POSE_ANIMATION}) are correct for the default (bare) player
     * model. Equipment such as graceful or spotted cape does not change them.
     *
     * @return {@code true} if walking or running
     */
    public boolean isMoving()
    {
        int poseAnim = client.getLocalPlayer().getPoseAnimation();
        return poseAnim == WALK_POSE_ANIMATION || poseAnim == RUN_POSE_ANIMATION;
    }

    /**
     * Returns {@code true} if the player currently has run mode enabled.
     *
     * <p>Reads varbit 173 (the run-energy toggle). Returns {@code true} when
     * value is 1 (run on), {@code false} when 0 (walk on) or not set.
     *
     * @return {@code true} if run mode is active
     */
    public boolean isRunning()
    {
        return client.getVarpValue(RUN_VARP_ID) == 1;
    }

    /**
     * Enables or disables run mode by clicking the minimap run orb widget.
     *
     * <p>If the requested state already matches the current state (e.g. calling
     * {@code setRunning(true)} when run is already on), the click is suppressed
     * to avoid unnecessary interaction noise.
     *
     * @param run {@code true} to enable running, {@code false} to walk
     */
    public void setRunning(boolean run)
    {
        if (isRunning() == run)
        {
            log.debug("setRunning({}): already in requested state, skipping", run);
            return;
        }
        log.debug("Toggling run mode to {}", run);
        client.menuAction(
            -1, WidgetInfo.MINIMAP_RUN_ORB.getId(),
            MenuAction.CC_OP,
            1, -1,
            "Toggle run", ""
        );
    }

    // ── Distance & position helpers ────────────────────────────────────────────

    /**
     * Returns the straight-line tile distance between the player and a world tile.
     *
     * <p>Uses Chebyshev distance (max of |dx|, |dy|), which is how OSRS
     * calculates tile adjacency and interaction range.
     *
     * @param point the target world tile
     * @return tile distance to {@code point}
     */
    public int distanceTo(WorldPoint point)
    {
        return client.getLocalPlayer().getWorldLocation().distanceTo(point);
    }

    /**
     * Returns {@code true} if the player is standing on exactly the given tile.
     *
     * @param point the world tile to compare against
     * @return {@code true} if the player's world location equals {@code point}
     */
    public boolean isAt(WorldPoint point)
    {
        return client.getLocalPlayer().getWorldLocation().equals(point);
    }

    /**
     * Returns {@code true} if the player is within {@code distance} tiles of
     * the given world point (inclusive).
     *
     * <p>Example: {@code isWithin(bankTile, 3)} returns true when the player
     * is 3 or fewer tiles away, which is close enough for most interactions.
     *
     * @param point    the reference world tile
     * @param distance the maximum allowed tile distance (inclusive)
     * @return {@code true} if {@code distanceTo(point) <= distance}
     */
    public boolean isWithin(WorldPoint point, int distance)
    {
        return distanceTo(point) <= distance;
    }

    // ── Run energy ────────────────────────────────────────────────────────────

    /**
     * Returns the player's current run energy on a 0–10000 scale.
     *
     * <p>Divide by 100 to convert to a human-readable percentage.
     * Example: 7550 = 75.5% run energy remaining.
     *
     * @return run energy in the range [0, 10000]
     */
    public int getRunEnergy()
    {
        return client.getEnergy();
    }

    /**
     * Returns the player's current run energy as a percentage (0–100).
     *
     * <p>This is a convenience wrapper around {@link #getRunEnergy()} that
     * divides by 100 and returns an integer percentage.
     *
     * @return run energy percentage in the range [0, 100]
     */
    public int getRunEnergyPercent()
    {
        return client.getEnergy() / 100;
    }

    /**
     * Logs out of the game by clicking the logout button widget.
     * Use for emergency logout when the player is about to die.
     * Interface 182 component 8 is the standard logout button in OSRS.
     */
    public void logout()
    {
        log.info("Logging out");
        client.menuAction(
            -1, net.runelite.api.widgets.WidgetUtil.packComponentId(182, 8),
            net.runelite.api.MenuAction.CC_OP,
            1, -1, "Logout", ""
        );
    }
}
