package com.botengine.osrs.overlay;

import com.botengine.osrs.script.BotScript;
import com.botengine.osrs.script.ScriptRunner;
import com.botengine.osrs.script.ScriptState;
import com.botengine.osrs.util.Antiban;
import com.botengine.osrs.util.Time;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

/**
 * On-screen status overlay displayed while a script is active.
 *
 * Renders a small panel in the top-left of the game viewport showing:
 *   - Script name
 *   - Current state (RUNNING / PAUSED / BREAKING)
 *   - Session runtime (HH:MM:SS)
 *   - Antiban break info (time until next break, or break countdown)
 *
 * The panel is hidden when no script is running (state == STOPPED).
 * Position can be dragged in-game by the player like any RuneLite overlay.
 */
public class BotOverlay extends Overlay
{
    private static final Color COLOR_RUNNING = new Color(0, 200, 0);
    private static final Color COLOR_PAUSED  = new Color(200, 200, 0);
    private static final Color COLOR_BREAKING = new Color(200, 100, 0);
    private static final Color COLOR_LABEL   = Color.WHITE;

    private final ScriptRunner scriptRunner;
    private final Antiban antiban;
    private final Time time;

    private long scriptStartTime = 0;

    @Inject
    public BotOverlay(ScriptRunner scriptRunner, Antiban antiban, Time time)
    {
        this.scriptRunner = scriptRunner;
        this.antiban = antiban;
        this.time = time;

        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        ScriptState state = scriptRunner.getState();

        // Don't render when no script is running
        if (state == ScriptState.STOPPED)
        {
            return null;
        }

        BotScript script = scriptRunner.getActiveScript();
        String scriptName = script != null ? script.getName() : "Unknown";

        // Track session start time
        if (scriptStartTime == 0 || state == ScriptState.STOPPED)
        {
            scriptStartTime = System.currentTimeMillis();
        }

        PanelComponent panel = new PanelComponent();
        panel.setPreferredSize(new Dimension(180, 0));

        // Title: script name
        panel.getChildren().add(TitleComponent.builder()
            .text(scriptName)
            .color(Color.CYAN)
            .build());

        // State row
        panel.getChildren().add(LineComponent.builder()
            .left("State")
            .leftColor(COLOR_LABEL)
            .right(stateLabel(state))
            .rightColor(stateColor(state))
            .build());

        // Runtime row
        panel.getChildren().add(LineComponent.builder()
            .left("Runtime")
            .leftColor(COLOR_LABEL)
            .right(time.formatElapsed(scriptStartTime))
            .rightColor(Color.WHITE)
            .build());

        // Break info row
        if (state == ScriptState.BREAKING)
        {
            long breakRemaining = antiban.sessionElapsedMs(); // placeholder
            panel.getChildren().add(LineComponent.builder()
                .left("Break ends")
                .leftColor(COLOR_LABEL)
                .right("soon")
                .rightColor(COLOR_BREAKING)
                .build());
        }
        else
        {
            long untilBreak = Math.max(0,
                (antiban.sessionElapsedMs() / 1000 / 60));
            panel.getChildren().add(LineComponent.builder()
                .left("Next break")
                .leftColor(COLOR_LABEL)
                .right(untilBreak + " min")
                .rightColor(Color.LIGHT_GRAY)
                .build());
        }

        return panel.render(graphics);
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private String stateLabel(ScriptState state)
    {
        switch (state)
        {
            case RUNNING:  return "Running";
            case PAUSED:   return "Paused";
            case BREAKING: return "Breaking";
            default:       return "Stopped";
        }
    }

    private Color stateColor(ScriptState state)
    {
        switch (state)
        {
            case RUNNING:  return COLOR_RUNNING;
            case PAUSED:   return COLOR_PAUSED;
            case BREAKING: return COLOR_BREAKING;
            default:       return Color.GRAY;
        }
    }
}
