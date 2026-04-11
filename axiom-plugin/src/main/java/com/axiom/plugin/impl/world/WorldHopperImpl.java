package com.axiom.plugin.impl.world;

import com.axiom.api.world.WorldHopper;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.World;
import net.runelite.api.WorldType;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

@Slf4j
@Singleton
public class WorldHopperImpl implements WorldHopper
{
    private final Client client;
    private final Random random = new Random();

    @Inject
    public WorldHopperImpl(Client client)
    {
        this.client = client;
    }

    @Override
    public void hopTo(int worldId)
    {
        World[] worldList = client.getWorldList();
        if (worldList == null)
        {
            log.warn("WorldHopperImpl: world list unavailable");
            return;
        }

        for (World world : worldList)
        {
            if (world.getId() == worldId)
            {
                log.info("WorldHopperImpl: hopping to world {}", worldId);
                client.hopToWorld(world);
                return;
            }
        }
        log.warn("WorldHopperImpl: world {} not found in world list", worldId);
    }

    @Override
    public void hopToLowPop()
    {
        World[] worldList = client.getWorldList();
        if (worldList == null)
        {
            log.warn("WorldHopperImpl: world list unavailable");
            return;
        }

        int current = getCurrentWorld();

        // Collect member worlds that are not PvP — population data not available
        // via net.runelite.api.World; pick a random eligible world instead.
        List<World> candidates = new ArrayList<>();
        for (World world : worldList)
        {
            if (world.getId() == current) continue;
            EnumSet<WorldType> types = world.getTypes();
            if (!types.contains(WorldType.MEMBERS)) continue;
            if (types.contains(WorldType.PVP))      continue;
            if (types.contains(WorldType.LAST_MAN_STANDING)) continue;
            candidates.add(world);
        }

        if (candidates.isEmpty())
        {
            log.warn("WorldHopperImpl: no suitable member world found");
            return;
        }

        World target = candidates.get(random.nextInt(candidates.size()));
        log.info("WorldHopperImpl: hopping to world {}", target.getId());
        client.hopToWorld(target);
    }

    @Override
    public int getCurrentWorld()
    {
        return client.getWorld();
    }
}
