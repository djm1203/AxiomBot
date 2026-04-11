package com.axiom.plugin.impl.player;

import com.axiom.api.player.Prayer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.Skill;
import net.runelite.api.widgets.Widget;

import javax.inject.Inject;
import javax.inject.Singleton;

@Slf4j
@Singleton
public class PrayerImpl implements Prayer
{
    // Prayer widget group
    private static final int PRAYER_GROUP = 541;

    private final Client client;

    @Inject
    public PrayerImpl(Client client) { this.client = client; }

    @Override
    public boolean isActive(PrayerType prayer)
    {
        net.runelite.api.Prayer rlPrayer = toRuneLite(prayer);
        if (rlPrayer == null) return false;
        return client.isPrayerActive(rlPrayer);
    }

    @Override
    public void activate(PrayerType prayer)
    {
        if (isActive(prayer)) return;
        togglePrayer(prayer);
    }

    @Override
    public void deactivate(PrayerType prayer)
    {
        if (!isActive(prayer)) return;
        togglePrayer(prayer);
    }

    @Override
    public void deactivateAll()
    {
        for (PrayerType p : PrayerType.values())
        {
            if (isActive(p)) togglePrayer(p);
        }
    }

    @Override
    public int getPoints()
    {
        return client.getBoostedSkillLevel(Skill.PRAYER);
    }

    @Override
    public boolean hasPoints(int minimum)
    {
        return getPoints() >= minimum;
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private void togglePrayer(PrayerType prayer)
    {
        net.runelite.api.Prayer rlPrayer = toRuneLite(prayer);
        if (rlPrayer == null)
        {
            log.warn("PrayerImpl: no RuneLite mapping for {}", prayer);
            return;
        }
        Widget widget = client.getWidget(PRAYER_GROUP, rlPrayer.ordinal() + 1);
        if (widget == null)
        {
            log.warn("PrayerImpl: prayer widget not found for {}", prayer);
            return;
        }
        client.menuAction(
            1, widget.getId(),
            MenuAction.CC_OP,
            1, -1,
            "", ""
        );
    }

    /** Maps axiom PrayerType to RuneLite Prayer by enum name. */
    private static net.runelite.api.Prayer toRuneLite(PrayerType prayer)
    {
        try
        {
            return net.runelite.api.Prayer.valueOf(prayer.name());
        }
        catch (IllegalArgumentException e)
        {
            return null;
        }
    }
}
