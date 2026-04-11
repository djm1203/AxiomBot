package com.axiom.plugin.impl.game;

import com.axiom.api.game.Players;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class PlayersImpl implements Players
{
    private final Client client;

    @Inject
    public PlayersImpl(Client client) { this.client = client; }

    @Override
    public boolean isIdle()
    {
        Player p = client.getLocalPlayer();
        if (p == null) return false;
        return p.getAnimation() == -1 && !isMoving();
    }

    @Override
    public boolean isMoving()
    {
        Player p = client.getLocalPlayer();
        if (p == null) return false;
        // idle pose anim ID 813 (varies with equipment; checking != idle is sufficient)
        return p.getPoseAnimation() != 813 && p.getAnimation() == -1;
    }

    @Override
    public boolean isAnimating()
    {
        Player p = client.getLocalPlayer();
        return p != null && p.getAnimation() != -1;
    }

    @Override
    public boolean isInCombat()
    {
        Player p = client.getLocalPlayer();
        if (p == null) return false;
        Actor target = p.getInteracting();
        if (target == null) return false;
        return target.getInteracting() == p;
    }

    @Override
    public int getHealthPercent()
    {
        Player p = client.getLocalPlayer();
        if (p == null) return 100;
        int ratio = p.getHealthRatio();
        int scale = p.getHealthScale();
        if (ratio < 0 || scale <= 0) return 100;
        return (int) Math.ceil((double) ratio / scale * 100);
    }

    @Override
    public int getWorldX()
    {
        Player p = client.getLocalPlayer();
        return p != null ? p.getWorldLocation().getX() : 0;
    }

    @Override
    public int getWorldY()
    {
        Player p = client.getLocalPlayer();
        return p != null ? p.getWorldLocation().getY() : 0;
    }

    @Override
    public int getPlane()
    {
        return client.getPlane();
    }

    @Override
    public int distanceTo(int worldX, int worldY)
    {
        Player p = client.getLocalPlayer();
        if (p == null) return Integer.MAX_VALUE;
        WorldPoint loc = p.getWorldLocation();
        return Math.max(Math.abs(loc.getX() - worldX), Math.abs(loc.getY() - worldY));
    }

    @Override
    public boolean isAt(int worldX, int worldY)
    {
        return getWorldX() == worldX && getWorldY() == worldY;
    }
}
