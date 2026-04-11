package com.axiom.plugin.impl.game;

import com.axiom.api.game.Npcs;
import com.axiom.api.game.SceneObject;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Singleton
public class NpcsImpl implements Npcs
{
    private final Client client;

    @Inject
    public NpcsImpl(Client client) { this.client = client; }

    @Override
    public SceneObject nearest(int id)
    {
        return nearest(o -> o.getId() == id);
    }

    @Override
    public SceneObject nearest(int... ids)
    {
        return nearest(o -> {
            for (int id : ids) if (o.getId() == id) return true;
            return false;
        });
    }

    @Override
    public SceneObject nearestByName(String name)
    {
        return nearest(o -> o.getName().toLowerCase().contains(name.toLowerCase()));
    }

    @Override
    public SceneObject nearest(Predicate<SceneObject> filter)
    {
        if (client.getLocalPlayer() == null) return null;
        WorldPoint playerPos = client.getLocalPlayer().getWorldLocation();
        return allWrapped().stream()
            .filter(filter)
            .min(Comparator.comparingInt(o ->
                Math.max(Math.abs(o.getWorldX() - playerPos.getX()),
                         Math.abs(o.getWorldY() - playerPos.getY()))))
            .orElse(null);
    }

    @Override
    public List<SceneObject> all(Predicate<SceneObject> filter)
    {
        if (client.getLocalPlayer() == null) return List.of();
        WorldPoint playerPos = client.getLocalPlayer().getWorldLocation();
        return allWrapped().stream()
            .filter(filter)
            .sorted(Comparator.comparingInt(o ->
                Math.max(Math.abs(o.getWorldX() - playerPos.getX()),
                         Math.abs(o.getWorldY() - playerPos.getY()))))
            .collect(Collectors.toList());
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private List<SceneObject> allWrapped()
    {
        return client.getNpcs().stream()
            .map(npc -> (SceneObject) new SceneObjectWrapper(client, npc))
            .collect(Collectors.toList());
    }
}
