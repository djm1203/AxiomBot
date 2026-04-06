package com.botengine.osrs.util;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.WorldType;
import net.runelite.client.game.WorldService;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Utility for hopping between OSRS worlds.
 *
 * Uses RuneLite's {@link WorldService} to obtain the current world list, then
 * calls {@link Client#hopToWorld} to perform the hop. World list is fetched from
 * the Jagex world servers via WorldService.
 *
 * Includes a hop cooldown (default 10 seconds) to prevent ban-risking rapid hops.
 *
 * Common use cases:
 *   - Hop when a competing player is on the same resource (rocks, trees)
 *   - Hop to a low-population members world on startup
 */
@Slf4j
@Singleton
public class WorldHopper
{
    /** Minimum milliseconds between hops to avoid triggering rate limits. */
    private static final long HOP_COOLDOWN_MS = 10_000;

    private final Client client;
    private final WorldService worldService;
    private final Random random = new Random();

    private long lastHopTime = 0;

    @Inject
    public WorldHopper(Client client, WorldService worldService)
    {
        this.client = client;
        this.worldService = worldService;
    }

    // ── Hop to specific world ─────────────────────────────────────────────────

    /**
     * Hops to a specific world by number.
     * Returns false if on cooldown or the world cannot be found.
     */
    public boolean hopTo(int worldNumber)
    {
        if (!canHop())
        {
            log.debug("World hop on cooldown — {} ms remaining", timeUntilNextHop());
            return false;
        }

        World world = findWorld(worldNumber);
        if (world == null)
        {
            log.warn("World {} not found in world list", worldNumber);
            return false;
        }

        performHop(world);
        return true;
    }

    // ── Hop to low-pop worlds ─────────────────────────────────────────────────

    /**
     * Hops to a random low-population members world (P2P, non-PvP, non-skill, non-seasonal).
     * Picks from the bottom third of worlds by population to avoid busy worlds.
     * Returns false if on cooldown or no eligible world found.
     */
    public boolean hopToMembers()
    {
        if (!canHop()) return false;

        List<World> candidates = getMembersWorlds();
        if (candidates.isEmpty())
        {
            log.warn("No eligible members worlds found");
            return false;
        }

        World target = pickLowPopWorld(candidates);
        performHop(target);
        return true;
    }

    /**
     * Hops to a random low-population free-to-play world.
     * Returns false if on cooldown or no eligible world found.
     */
    public boolean hopToFree()
    {
        if (!canHop()) return false;

        List<World> candidates = getFreeWorlds();
        if (candidates.isEmpty())
        {
            log.warn("No eligible free worlds found");
            return false;
        }

        World target = pickLowPopWorld(candidates);
        performHop(target);
        return true;
    }

    // ── State ─────────────────────────────────────────────────────────────────

    /**
     * Returns true if enough time has passed since the last hop.
     */
    public boolean canHop()
    {
        return System.currentTimeMillis() - lastHopTime >= HOP_COOLDOWN_MS;
    }

    /**
     * Returns the current world number the player is on.
     */
    public int getCurrentWorld()
    {
        return client.getWorld();
    }

    /**
     * Returns milliseconds until the next hop is allowed (0 if ready).
     */
    public long timeUntilNextHop()
    {
        long elapsed = System.currentTimeMillis() - lastHopTime;
        return Math.max(0, HOP_COOLDOWN_MS - elapsed);
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private void performHop(World httpWorld)
    {
        log.info("Hopping to world {} (players: {})", httpWorld.getId(), httpWorld.getPlayers());
        lastHopTime = System.currentTimeMillis();

        // client.hopToWorld() requires net.runelite.api.World from the in-game world list
        net.runelite.api.World[] apiWorlds = client.getWorldList();
        if (apiWorlds != null)
        {
            for (net.runelite.api.World apiWorld : apiWorlds)
            {
                if (apiWorld.getId() == httpWorld.getId())
                {
                    client.hopToWorld(apiWorld);
                    return;
                }
            }
        }
        log.warn("World {} not found in client world list — cannot hop", httpWorld.getId());
    }

    private List<World> getMembersWorlds()
    {
        WorldResult worldResult = worldService.getWorlds();
        if (worldResult == null) return List.of();

        return worldResult.getWorlds().stream()
            .filter(w -> w.getTypes().contains(WorldType.MEMBERS))
            .filter(w -> !w.getTypes().contains(WorldType.PVP))
            .filter(w -> !w.getTypes().contains(WorldType.HIGH_RISK))
            .filter(w -> !w.getTypes().contains(WorldType.SKILL_TOTAL))
            .filter(w -> !w.getTypes().contains(WorldType.SEASONAL))
            .filter(w -> w.getId() != client.getWorld())
            .filter(w -> w.getPlayers() >= 0)
            .collect(Collectors.toList());
    }

    private List<World> getFreeWorlds()
    {
        WorldResult worldResult = worldService.getWorlds();
        if (worldResult == null) return List.of();

        return worldResult.getWorlds().stream()
            .filter(w -> !w.getTypes().contains(WorldType.MEMBERS))
            .filter(w -> !w.getTypes().contains(WorldType.PVP))
            .filter(w -> w.getId() != client.getWorld())
            .filter(w -> w.getPlayers() >= 0)
            .collect(Collectors.toList());
    }

    private World pickLowPopWorld(List<World> worlds)
    {
        worlds.sort((a, b) -> Integer.compare(a.getPlayers(), b.getPlayers()));
        int poolSize = Math.max(1, worlds.size() / 3);
        return worlds.get(random.nextInt(poolSize));
    }

    private World findWorld(int worldNumber)
    {
        WorldResult worldResult = worldService.getWorlds();
        if (worldResult == null) return null;

        return worldResult.getWorlds().stream()
            .filter(w -> w.getId() == worldNumber)
            .findFirst()
            .orElse(null);
    }
}
