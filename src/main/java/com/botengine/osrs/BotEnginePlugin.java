package com.botengine.osrs;

import com.botengine.osrs.overlay.BotOverlay;
import com.botengine.osrs.overlay.DebugOverlay;
import com.botengine.osrs.script.ScriptRunner;
import com.botengine.osrs.util.Antiban;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import javax.swing.ImageIcon;
import java.awt.image.BufferedImage;

/**
 * Main entry point for the Bot Engine plugin.
 *
 * This class is the only one RuneLite knows about directly.
 * On startUp():
 *   1. Applies config values to Antiban
 *   2. Registers ScriptRunner with the EventBus (so it receives GameTick)
 *   3. Registers overlays with OverlayManager
 *   4. Adds the control panel to the RuneLite toolbar
 *
 * On shutDown():
 *   1. Stops any running script gracefully
 *   2. Unregisters ScriptRunner from EventBus
 *   3. Removes overlays and panel
 *
 * All dependencies are injected by RuneLite's Guice injector.
 * ScriptRunner, api/, and util/ classes are all @Inject-constructable.
 */
@Slf4j
@PluginDescriptor(
    name = "Bot Engine",
    description = "AFK scripting framework for skill automation",
    tags = {"bot", "afk", "script", "automation"}
)
public class BotEnginePlugin extends Plugin
{
    @Inject private BotEngineConfig config;
    @Inject private EventBus eventBus;
    @Inject private OverlayManager overlayManager;
    @Inject private ClientToolbar clientToolbar;

    @Inject private ScriptRunner scriptRunner;
    @Inject private Antiban antiban;

    @Inject private BotEnginePanel panel;
    @Inject private BotOverlay botOverlay;
    @Inject private DebugOverlay debugOverlay;

    private NavigationButton navButton;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void startUp()
    {
        log.info("Bot Engine starting up");

        // Apply config to antiban
        antiban.setBreakIntervalMinutes(config.breakInterval());
        antiban.setBreakDurationMinutes(config.breakDuration());
        antiban.setJitterRadius(config.mouseJitter());

        // Register ScriptRunner to receive GameTick events
        eventBus.register(scriptRunner);

        // Register overlays
        overlayManager.add(botOverlay);
        if (config.debugOverlay())
        {
            overlayManager.add(debugOverlay);
        }

        // Add control panel to the RuneLite side toolbar
        navButton = NavigationButton.builder()
            .tooltip("Bot Engine")
            .icon(loadIcon())
            .priority(10)
            .panel(panel)
            .build();
        clientToolbar.addNavigation(navButton);

        log.info("Bot Engine ready");
    }

    @Override
    protected void shutDown()
    {
        log.info("Bot Engine shutting down");

        // Stop script cleanly
        scriptRunner.stop();

        // Unregister from event bus
        eventBus.unregister(scriptRunner);

        // Remove overlays
        overlayManager.remove(botOverlay);
        overlayManager.remove(debugOverlay);

        // Remove panel
        if (navButton != null)
        {
            clientToolbar.removeNavigation(navButton);
        }

        log.info("Bot Engine stopped");
    }

    // ── Config binding ────────────────────────────────────────────────────────

    /**
     * Provides the config instance to Guice's injector.
     * RuneLite calls this to bind BotEngineConfig with persisted values.
     */
    @Provides
    BotEngineConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(BotEngineConfig.class);
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    /**
     * Loads the panel icon for the navigation button.
     * Falls back to a blank 16x16 icon if the resource is missing.
     */
    private BufferedImage loadIcon()
    {
        try
        {
            java.awt.Image img = new ImageIcon(
                BotEnginePlugin.class.getResource("/com/botengine/osrs/icon.png")
            ).getImage();
            return img instanceof BufferedImage ? (BufferedImage) img : blankIcon();
        }
        catch (Exception e)
        {
            log.warn("Could not load plugin icon, using blank", e);
            return blankIcon();
        }
    }

    private BufferedImage blankIcon()
    {
        return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    }
}
