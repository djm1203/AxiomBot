package com.axiom.plugin;

import com.axiom.api.script.BotScript;
import com.axiom.api.script.ScriptSettings;
import com.axiom.plugin.overlay.BotOverlay;
import com.axiom.plugin.util.AutoUpdater;
import com.axiom.plugin.ui.AxiomPanel;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
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
 * Main RuneLite plugin entry point for Axiom.
 *
 * On startUp():
 *   1. Registers ScriptRunner with the EventBus (receives GameTick, GameStateChanged)
 *   2. Adds the Axiom side panel to the RuneLite toolbar
 *
 * On shutDown():
 *   1. Stops any running script gracefully
 *   2. Unregisters ScriptRunner from EventBus
 *   3. Removes the panel from the toolbar
 *
 * All dependencies are injected by RuneLite's Guice injector.
 * All impl/* classes are @Singleton and @Inject-constructable.
 */
@Slf4j
@PluginDescriptor(
    name        = "Axiom",
    description = "AFK scripting framework for skill automation",
    tags        = {"axiom", "bot", "afk", "script", "automation"}
)
public class AxiomPlugin extends Plugin
{
    @Inject private EventBus        eventBus;
    @Inject private ClientToolbar   clientToolbar;
    @Inject private OverlayManager  overlayManager;
    @Inject private ScriptRunner    scriptRunner;
    @Inject private AxiomPanel      panel;
    @Inject private BotOverlay      botOverlay;
    @Inject private LauncherBridge  launcherBridge;

    private NavigationButton navButton;
    private boolean          autoStartFired = false;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void startUp()
    {
        log.info("Axiom starting up");

        // Check for plugin updates in the background (non-blocking)
        AutoUpdater.checkAsync();

        // Register ScriptRunner and this plugin to receive game events
        eventBus.register(scriptRunner);
        eventBus.register(this);

        // Register the in-game overlay
        overlayManager.add(botOverlay);

        // Build and add the navigation button for the side panel
        navButton = NavigationButton.builder()
            .tooltip("Axiom")
            .icon(buildIcon())
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

        scriptRunner.stop();
        eventBus.unregister(scriptRunner);
        eventBus.unregister(this);
        overlayManager.remove(botOverlay);

        if (navButton != null)
        {
            clientToolbar.removeNavigation(navButton);
            navButton = null;
        }

        log.info("Axiom stopped");
    }

    // ── Launcher auto-start ───────────────────────────────────────────────────

    /**
     * Fires once on the first LOGGED_IN event after the plugin starts.
     * If the launcher passed {@code -Daxiom.script=<name>}, finds the matching
     * script, retrieves its default settings, and starts it automatically.
     */
    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (autoStartFired || event.getGameState() != GameState.LOGGED_IN) return;
        if (!launcherBridge.isLauncherManaged()) return;

        autoStartFired = true;
        String targetName = launcherBridge.getScriptName();

        BotScript match = ScriptLoader.loadScripts().stream()
            .filter(s -> s.getName().equalsIgnoreCase(targetName))
            .findFirst()
            .orElse(null);

        if (match == null)
        {
            log.warn("LauncherBridge: no script found matching '{}'", targetName);
            return;
        }

        ScriptSettings defaults = match.getDefaultSettings();
        if (defaults == null)
        {
            log.warn("LauncherBridge: '{}' has no default settings — cannot auto-start", targetName);
            return;
        }

        log.info("LauncherBridge: auto-starting '{}'", targetName);
        scriptRunner.start(match, defaults);
    }

    // ── Icon ──────────────────────────────────────────────────────────────────

    /**
     * Programmatic 16×16 toolbar icon — orange "A" on a dark background.
     * No resource file required.
     */
    private static BufferedImage buildIcon()
    {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(30, 30, 30, 220));
        g.fillRoundRect(0, 0, 16, 16, 4, 4);

        g.setColor(new Color(220, 138, 0)); // Axiom orange
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        g.drawString("A", 3, 12);

        g.dispose();
        return img;
    }
}
