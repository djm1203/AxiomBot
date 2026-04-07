package com.botengine.osrs;

import com.botengine.osrs.overlay.BotOverlay;
import com.botengine.osrs.overlay.DebugOverlay;
import com.botengine.osrs.script.ScriptRunner;
import com.botengine.osrs.ui.AxiomPanel;
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
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
    name = "Axiom",
    description = "AFK scripting framework for skill automation",
    tags = {"axiom", "bot", "afk", "script", "automation"}
)
public class BotEnginePlugin extends Plugin
{
    @Inject private BotEngineConfig config;
    @Inject private EventBus eventBus;
    @Inject private OverlayManager overlayManager;
    @Inject private ClientToolbar clientToolbar;

    @Inject private ScriptRunner scriptRunner;
    @Inject private Antiban antiban;

    @Inject private AxiomPanel panel;
    @Inject private BotOverlay botOverlay;
    @Inject private DebugOverlay debugOverlay;

    private NavigationButton navButton;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void startUp()
    {
        log.info("Axiom starting up");

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
            .tooltip("Axiom")
            .icon(loadIcon())
            .priority(10)
            .panel(panel)
            .build();
        clientToolbar.addNavigation(navButton);

        log.info("Axiom ready");
    }

    @Override
    protected void shutDown()
    {
        log.info("Axiom shutting down");

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

        log.info("Axiom stopped");
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
     * Generates the plugin icon programmatically — a green "B" on dark background.
     * No resource file required; always renders correctly.
     */
    private BufferedImage loadIcon()
    {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Dark background
        g.setColor(new Color(30, 30, 30, 220));
        g.fillRoundRect(0, 0, 16, 16, 4, 4);

        // Orange "A" label (Axiom brand color)
        g.setColor(new Color(220, 138, 0));
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        g.drawString("A", 3, 12);

        g.dispose();
        return img;
    }
}
