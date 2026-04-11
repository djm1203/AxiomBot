package com.axiom.plugin.impl.interaction;

import com.axiom.api.interaction.Menu;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;

import javax.inject.Inject;
import javax.inject.Singleton;

@Slf4j
@Singleton
public class MenuImpl implements Menu
{
    private final Client client;

    @Inject
    public MenuImpl(Client client) { this.client = client; }

    @Override
    public boolean clickOption(String option, String target)
    {
        MenuEntry[] entries = client.getMenuEntries();
        if (entries == null) return false;

        for (MenuEntry entry : entries)
        {
            String entryOption = net.runelite.client.util.Text.removeTags(entry.getOption());
            String entryTarget = net.runelite.client.util.Text.removeTags(entry.getTarget());

            boolean optionMatch = option.equalsIgnoreCase(entryOption);
            boolean targetMatch = target.isEmpty() || target.equalsIgnoreCase(entryTarget);

            if (optionMatch && targetMatch)
            {
                log.debug("MenuImpl: clicking '{}' on '{}'", entryOption, entryTarget);
                client.menuAction(
                    entry.getParam0(), entry.getParam1(),
                    entry.getType(),
                    entry.getIdentifier(), -1,
                    entry.getOption(), entry.getTarget()
                );
                return true;
            }
        }
        log.debug("MenuImpl: no entry found for '{}' on '{}'", option, target);
        return false;
    }
}
