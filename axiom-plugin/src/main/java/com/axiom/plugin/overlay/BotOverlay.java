package com.axiom.plugin.overlay;

import com.axiom.api.util.Antiban;
import com.axiom.plugin.ScriptRunner;
import com.axiom.plugin.ScriptState;
import com.axiom.plugin.util.SessionStats;
import net.runelite.api.Skill;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * RuneLite overlay that renders live script stats in the top-left corner.
 *
 * Displayed while a script is running (RUNNING or BREAKING or PAUSED):
 *   - Script name (title bar, cyan)
 *   - State (color-coded)
 *   - Runtime (HH:MM:SS)
 *   - XP/hr for the top skill
 *   - Levels gained this session
 *   - Break countdown (BREAKING state only)
 *
 * All data is read from ScriptRunner and SessionStats — no separate data store.
 * The overlay does NOT call sessionStats.onTick(); that is ScriptRunner's job.
 *
 * Width is fixed at 185px to prevent layout thrash.
 */
@Singleton
public class BotOverlay extends Overlay
{
    private static final int   OVERLAY_WIDTH = 185;
    private static final Color COLOR_RUNNING  = new Color(0, 200, 80);
    private static final Color COLOR_BREAKING = new Color(255, 165, 0);
    private static final Color COLOR_PAUSED   = new Color(160, 160, 160);
    private static final Color COLOR_XP       = new Color(255, 215, 0);
    private static final Color COLOR_LEVELS   = new Color(100, 210, 255);
    private static final Color COLOR_LABEL    = new Color(180, 180, 180);
    private static final Color COLOR_TITLE    = new Color(0, 220, 220);  // cyan

    private static final NumberFormat NUMBER_FORMAT =
        NumberFormat.getNumberInstance(Locale.US);

    private final ScriptRunner scriptRunner;
    private final SessionStats sessionStats;
    private final Antiban      antiban;
    private final PanelComponent panel = new PanelComponent();

    @Inject
    public BotOverlay(ScriptRunner scriptRunner, SessionStats sessionStats, Antiban antiban)
    {
        this.scriptRunner = scriptRunner;
        this.sessionStats = sessionStats;
        this.antiban      = antiban;

        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_SCENE);
        panel.setPreferredSize(new Dimension(OVERLAY_WIDTH, 0));
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        ScriptState state = scriptRunner.getState();

        // Only render when a script is active
        if (state == ScriptState.STOPPED) return null;

        panel.getChildren().clear();

        // ── Title ──────────────────────────────────────────────────────────────
        String scriptName = scriptRunner.getActiveScript() != null
            ? scriptRunner.getActiveScript().getName()
            : "Axiom";

        panel.getChildren().add(TitleComponent.builder()
            .text(scriptName)
            .color(COLOR_TITLE)
            .build());

        // ── State ──────────────────────────────────────────────────────────────
        Color stateColor;
        String stateLabel;
        switch (state)
        {
            case RUNNING:
                stateColor = COLOR_RUNNING;
                stateLabel = "Running";
                break;
            case BREAKING:
                stateColor = COLOR_BREAKING;
                stateLabel = "Breaking";
                break;
            case PAUSED:
                stateColor = COLOR_PAUSED;
                stateLabel = "Paused";
                break;
            default:
                stateColor = COLOR_LABEL;
                stateLabel = state.name();
        }
        addRow("State", stateLabel, stateColor);

        // ── Runtime ────────────────────────────────────────────────────────────
        long elapsedMs = sessionStats.isActive()
            ? sessionStats.getElapsedMs()
            : System.currentTimeMillis() - scriptRunner.getScriptStartMs();
        addRow("Runtime", formatElapsed(elapsedMs), COLOR_LABEL);

        // ── XP / hr ────────────────────────────────────────────────────────────
        if (sessionStats.isActive())
        {
            int xpHr = sessionStats.getXpPerHour();
            Skill topSkill = sessionStats.getTopSkill();
            String skillLabel = (topSkill != null) ? topSkill.getName() + " XP/hr" : "XP/hr";
            String xpHrText   = xpHr > 0 ? NUMBER_FORMAT.format(xpHr) : "-";
            addRow(skillLabel, xpHrText, COLOR_XP);

            // ── Levels gained ──────────────────────────────────────────────────
            int levels = sessionStats.getTotalLevelsGained();
            addRow("Levels", levels > 0 ? "+" + levels : "0", levels > 0 ? COLOR_LEVELS : COLOR_LABEL);
        }

        // ── Break countdown ────────────────────────────────────────────────────
        if (state == ScriptState.BREAKING)
        {
            long remainingMs = antiban.getBreakEndMs() - System.currentTimeMillis();
            if (remainingMs > 0)
            {
                addRow("Break ends", formatElapsed(remainingMs), COLOR_BREAKING);
            }
        }

        return panel.render(graphics);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void addRow(String left, String right, Color rightColor)
    {
        panel.getChildren().add(LineComponent.builder()
            .left(left)
            .leftColor(COLOR_LABEL)
            .right(right)
            .rightColor(rightColor)
            .build());
    }

    /** Formats a duration in milliseconds as HH:MM:SS. */
    private static String formatElapsed(long ms)
    {
        long s = Math.max(0, ms / 1000);
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }
}
