package com.botengine.osrs.overlay;

import com.botengine.osrs.BotEngineConfig;
import com.botengine.osrs.script.BotScript;
import com.botengine.osrs.script.ScriptRunner;
import com.botengine.osrs.script.ScriptState;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;

import javax.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Shape;
import java.awt.Stroke;

/**
 * Developer debug overlay — only shown when debug mode is enabled in config.
 *
 * Renders:
 *   - Player tile outline (green)
 *   - NPC bounding boxes for visible NPCs within 10 tiles (yellow)
 *   - Current script state name on screen
 *   - Session debug info panel (top-right)
 *
 * This overlay has zero impact when debug mode is off (returns null immediately).
 * Enable it in Bot Engine config → Debug → "Show debug overlay".
 */
public class DebugOverlay extends Overlay
{
    private static final Color COLOR_PLAYER_TILE = new Color(0, 255, 0, 100);
    private static final Color COLOR_NPC_BOX     = new Color(255, 255, 0, 120);
    private static final Color COLOR_NPC_OUTLINE  = new Color(255, 200, 0);
    private static final Stroke OUTLINE_STROKE    = new BasicStroke(1.5f);

    private final Client client;
    private final BotEngineConfig config;
    private final ScriptRunner scriptRunner;

    @Inject
    public DebugOverlay(Client client, BotEngineConfig config, ScriptRunner scriptRunner)
    {
        this.client = client;
        this.config = config;
        this.scriptRunner = scriptRunner;

        setPosition(OverlayPosition.TOP_RIGHT);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        // Only render when debug overlay is enabled in config
        if (!config.debugOverlay())
        {
            return null;
        }

        renderPlayerTile(graphics);
        renderNearbyNpcs(graphics);
        return renderInfoPanel(graphics);
    }

    // ── Tile rendering ────────────────────────────────────────────────────────

    /**
     * Highlights the tile the local player is standing on (green fill).
     */
    private void renderPlayerTile(Graphics2D graphics)
    {
        WorldPoint playerPos = client.getLocalPlayer().getWorldLocation();
        LocalPoint local = LocalPoint.fromWorld(client, playerPos);
        if (local == null) return;

        Polygon tilePoly = Perspective.getCanvasTilePoly(client, local);
        if (tilePoly == null) return;

        graphics.setColor(COLOR_PLAYER_TILE);
        graphics.fillPolygon(tilePoly);
        graphics.setColor(Color.GREEN);
        graphics.setStroke(OUTLINE_STROKE);
        graphics.drawPolygon(tilePoly);
    }

    /**
     * Draws bounding boxes around nearby NPCs (within 10 tiles).
     */
    private void renderNearbyNpcs(Graphics2D graphics)
    {
        WorldPoint playerPos = client.getLocalPlayer().getWorldLocation();

        for (NPC npc : client.getNpcs())
        {
            if (npc == null || npc.isDead()) continue;
            if (npc.getWorldLocation().distanceTo(playerPos) > 10) continue;

            // Draw hull (outline of the NPC's model)
            Shape hull = npc.getConvexHull();
            if (hull != null)
            {
                graphics.setColor(COLOR_NPC_BOX);
                graphics.fill(hull);
                graphics.setColor(COLOR_NPC_OUTLINE);
                graphics.setStroke(OUTLINE_STROKE);
                graphics.draw(hull);
            }

            // Draw name above NPC
            String name = npc.getName() != null ? npc.getName() : "NPC#" + npc.getId();
            OverlayUtil.renderActorOverlay(graphics, npc, name, COLOR_NPC_OUTLINE);
        }
    }

    // ── Info panel ────────────────────────────────────────────────────────────

    /**
     * Renders a small debug info panel in the top-right corner.
     */
    private Dimension renderInfoPanel(Graphics2D graphics)
    {
        PanelComponent panel = new PanelComponent();
        panel.setPreferredSize(new Dimension(160, 0));

        ScriptState state = scriptRunner.getState();
        BotScript script = scriptRunner.getActiveScript();

        panel.getChildren().add(LineComponent.builder()
            .left("[DEBUG]")
            .leftColor(Color.ORANGE)
            .build());

        panel.getChildren().add(LineComponent.builder()
            .left("Script")
            .right(script != null ? script.getName() : "none")
            .rightColor(Color.WHITE)
            .build());

        panel.getChildren().add(LineComponent.builder()
            .left("State")
            .right(state.name())
            .rightColor(Color.YELLOW)
            .build());

        WorldPoint pos = client.getLocalPlayer().getWorldLocation();
        panel.getChildren().add(LineComponent.builder()
            .left("Pos")
            .right(pos.getX() + "," + pos.getY())
            .rightColor(Color.LIGHT_GRAY)
            .build());

        panel.getChildren().add(LineComponent.builder()
            .left("NPCs")
            .right(String.valueOf(client.getNpcs().size()))
            .rightColor(Color.LIGHT_GRAY)
            .build());

        return panel.render(graphics);
    }
}
