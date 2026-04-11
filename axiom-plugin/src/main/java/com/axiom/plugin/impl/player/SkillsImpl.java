package com.axiom.plugin.impl.player;

import com.axiom.api.player.Skills;
import net.runelite.api.Client;
import net.runelite.api.Experience;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class SkillsImpl implements Skills
{
    private final Client client;

    @Inject
    public SkillsImpl(Client client) { this.client = client; }

    @Override
    public int getLevel(Skill skill)
    {
        return client.getBoostedSkillLevel(toRuneLite(skill));
    }

    @Override
    public int getBaseLevel(Skill skill)
    {
        return client.getRealSkillLevel(toRuneLite(skill));
    }

    @Override
    public int getXp(Skill skill)
    {
        return client.getSkillExperience(toRuneLite(skill));
    }

    @Override
    public int getXpToNextLevel(Skill skill)
    {
        int currentXp = getXp(skill);
        int currentLevel = getBaseLevel(skill);
        if (currentLevel >= 99) return 0;
        int nextLevelXp = Experience.getXpForLevel(currentLevel + 1);
        return Math.max(0, nextLevelXp - currentXp);
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    /**
     * Converts axiom-api Skill enum to RuneLite's Skill enum.
     * Names are identical so valueOf() works directly.
     */
    private static net.runelite.api.Skill toRuneLite(Skill skill)
    {
        return net.runelite.api.Skill.valueOf(skill.name());
    }
}
