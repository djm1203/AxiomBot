package com.botengine.osrs.overlay;

import com.botengine.osrs.script.BotScript;
import com.botengine.osrs.script.ScriptRunner;
import com.botengine.osrs.script.ScriptState;
import com.botengine.osrs.util.Antiban;
import com.botengine.osrs.util.SessionStats;
import com.botengine.osrs.util.Time;
import net.runelite.api.Client;
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
    private final Antiban      antiban;
    private final Time         time;
    private final SessionStats sessionStats;
    private final Client       client;

    private long scriptStartTime = 0;

    @Inject
    public BotOverlay(ScriptRunner scriptRunner, Antiban antiban, Time time,
                      SessionStats sessionStats, Client client)
    {
        this.scriptRunner = scriptRunner;
        this.antiban      = antiban;
        this.time         = time;
        this.sessionStats = sessionStats;
        this.client       = client;

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

        // Track session start time — only initialise on first render after script starts
        if (scriptStartTime == 0)
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

        // XP/hr row — sample stats and show if there's data
        sessionStats.sample(client);
        int xpHr = sessionStats.getXpPerHour();
        if (xpHr > 0 && sessionStats.getTopSkill() != null)
        {
            String skillName = sessionStats.getTopSkill().getName();
            // Format: "75,420" style with comma separator
            String xpHrStr = String.format("%,d", xpHr);
            panel.getChildren().add(LineComponent.builder()
                .left(skillName + " XP/hr")
                .leftColor(COLOR_LABEL)
                .right(xpHrStr)
                .rightColor(Color.YELLOW)
                .build());

            int levels = sessionStats.getTopLevelsGained();
            if (levels > 0)
            {
                panel.getChildren().add(LineComponent.builder()
                    .left("Levels gained")
                    .leftColor(COLOR_LABEL)
                    .right("+" + levels)
                    .rightColor(COLOR_RUNNING)
                    .build());
            }
        }

        // Break info row
        if (state == ScriptState.BREAKING)
        {
            long remainingMs = Math.max(0, antiban.getBreakEndMs() - System.currentTimeMillis());
            long remainingMin = remainingMs / 1000 / 60;
            long remainingSec = (remainingMs / 1000) % 60;
            panel.getChildren().add(LineComponent.builder()
                .left("Break ends")
                .leftColor(COLOR_LABEL)
                .right(String.format("%dm %ds", remainingMin, remainingSec))
                .rightColor(COLOR_BREAKING)
                .build());
        }
        else
        {
            long untilBreakMs = Math.max(0, antiban.getNextBreakAtMs() - System.currentTimeMillis());
            long untilBreakMin = untilBreakMs / 1000 / 60;
            panel.getChildren().add(LineComponent.builder()
                .left("Next break")
                .leftColor(COLOR_LABEL)
                .right(untilBreakMin + " min")
                .rightColor(Color.LIGHT_GRAY)
                .build());
        }

        return panel.render(graphics);
    }

    /** Called by ScriptRunner when a script starts so the runtime clock resets. */
    public void resetStartTime()
    {
        scriptStartTime = 0;
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
