package com.botengine.osrs.api;

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;

import javax.inject.Inject;
import java.util.List;

/**
 * Helpers for querying local player state.
 *
 * All methods read from the RuneLite Client and return simple values
 * scripts can act on without importing net.runelite.api directly.
 *
 * "Idle" in OSRS means animation ID == -1 AND the player is not moving.
 * Always check isIdle() before attempting an action — clicking while
 * already acting queues the action, which can cause timing issues.
 */
public class Players
{
    private final Client client;

    @Inject
    public Players(Client client)
    {
        this.client = client;
    }

    // ── Position ──────────────────────────────────────────────────────────────

    /** Returns the player's current world tile position. */
    public WorldPoint getLocation()
    {
        return client.getLocalPlayer().getWorldLocation();
    }

    /**
     * Returns the straight-line tile distance to a world point.
     * This is Chebyshev distance (max of dx, dy) matching OSRS's distance model.
     */
    public int distanceTo(WorldPoint point)
    {
        return getLocation().distanceTo(point);
    }

    // ── Animation & movement ─────────────────────────────────────────────────

    /**
     * Returns the player's current animation ID.
     * -1 means no animation is playing (idle).
     * Use the OSRS Wiki or RuneLite's Animation ID plugin to look up values.
     */
    public int getAnimation()
    {
        return client.getLocalPlayer().getAnimation();
    }

    /**
     * Returns true if the player is fully idle:
     *   - no animation playing
     *   - not currently walking/running to a destination
     */
    public boolean isIdle()
    {
        Player local = client.getLocalPlayer();
        if (local == null) return false;
        // getAnimation() == -1 means no active action animation (woodcutting, fishing,
        // combat, eating, etc.). Use Movement.isMoving() if you also need to check
        // that the player isn't walking.
        return local.getAnimation() == -1;
    }

    /**
     * Returns true if the player is currently moving (walking or running).
     */
    public boolean isMoving()
    {
        Player local = client.getLocalPlayer();
        int poseAnim = local.getPoseAnimation();
        // Walk and run pose animations differ from idle pose animation
        return poseAnim != 813 // idle pose (approximate — varies by equipment)
            && local.getAnimation() == -1; // not doing a skilling animation
    }

    // ── Health ────────────────────────────────────────────────────────────────

    /**
     * Returns the player's current health as a percentage (0–100).
     * Uses the health ratio/scale from the client.
     * Returns 100 if health data is unavailable.
     */
    public int getHealthPercent()
    {
        Player local = client.getLocalPlayer();
        int ratio = local.getHealthRatio();
        int scale = local.getHealthScale();
        if (ratio < 0 || scale <= 0) return 100;
        return (int) Math.ceil((double) ratio / scale * 100);
    }

    /**
     * Returns true if health is at or below the given percentage threshold.
     * Example: shouldEat(50) returns true when HP is 50% or lower.
     */
    public boolean shouldEat(int thresholdPercent)
    {
        return getHealthPercent() <= thresholdPercent;
    }

    // ── Combat ────────────────────────────────────────────────────────────────

    /**
     * Returns true if the player is currently in combat
     * (has an interacting target that is also interacting back).
     */
    public boolean isInCombat()
    {
        Actor target = client.getLocalPlayer().getInteracting();
        if (target == null) return false;
        return target.getInteracting() == client.getLocalPlayer();
    }

    /**
     * Returns the actor the player is currently interacting with, or null.
     * This includes both combat targets and NPC dialogue.
     */
    public Actor getTarget()
    {
        return client.getLocalPlayer().getInteracting();
    }

    // ── Player reference ──────────────────────────────────────────────────────

    /** Returns the raw RuneLite Player object for advanced use cases. */
    public Player getLocalPlayer()
    {
        return client.getLocalPlayer();
    }

    /**
     * Returns the number of other players within the given tile radius.
     * Does not count the local player.
     */
    public int nearbyCount(int radius)
    {
        WorldPoint local = getLocation();
        int count = 0;
        for (net.runelite.api.Player p : client.getPlayers())
        {
            if (p == client.getLocalPlayer()) continue;
            if (p.getWorldLocation().distanceTo(local) <= radius) count++;
        }
        return count;
    }
}
