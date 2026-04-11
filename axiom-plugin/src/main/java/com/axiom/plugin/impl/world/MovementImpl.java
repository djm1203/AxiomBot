package com.axiom.plugin.impl.world;

import com.axiom.api.world.Movement;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;

import javax.inject.Inject;
import javax.inject.Singleton;

@Slf4j
@Singleton
public class MovementImpl implements Movement
{
    private final Client client;

    @Inject
    public MovementImpl(Client client) { this.client = client; }

    @Override
    public void walkTo(int worldX, int worldY)
    {
        // Click the minimap tile closest to destination
        // Fires WALK_HERE on the scene position derived from the world coords
        net.runelite.api.coords.LocalPoint local =
            net.runelite.api.coords.LocalPoint.fromWorld(client, worldX, worldY);
        if (local == null)
        {
            log.warn("MovementImpl: {} {} is off-scene", worldX, worldY);
            return;
        }

        int sceneX = local.getSceneX();
        int sceneY = local.getSceneY();

        client.menuAction(
            sceneX, sceneY,
            MenuAction.WALK,
            0, -1,
            "", ""
        );
    }

    @Override
    public boolean isMoving()
    {
        net.runelite.api.Player p = client.getLocalPlayer();
        if (p == null) return false;
        return p.getPoseAnimation() != 813 && p.getAnimation() == -1;
    }

    @Override
    public void setRunning(boolean enabled)
    {
        // Run toggle is varp 173: 0 = walk, 1 = run
        int current = client.getVarpValue(173);
        if ((current == 1) != enabled)
        {
            Widget runBtn = client.getWidget(WidgetInfo.MINIMAP_TOGGLE_RUN_ORB);
            if (runBtn != null)
            {
                client.menuAction(
                    1, runBtn.getId(),
                    MenuAction.CC_OP,
                    1, -1,
                    "", ""
                );
            }
        }
    }

    @Override
    public int getRunEnergy()
    {
        return client.getEnergy() / 100; // getEnergy() returns 0–10000
    }

    @Override
    public void logout()
    {
        Widget logoutBtn = client.getWidget(182, 8); // logout button
        if (logoutBtn != null)
        {
            client.menuAction(
                1, logoutBtn.getId(),
                MenuAction.CC_OP,
                1, -1,
                "Logout", ""
            );
        }
    }
}
