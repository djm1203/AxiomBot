package com.botengine.osrs.ui;

import com.botengine.osrs.script.BotScript;
import com.botengine.osrs.script.ScriptRunner;
import com.botengine.osrs.script.ScriptState;
import com.botengine.osrs.scripts.alchemy.AlchemyScript;
import com.botengine.osrs.scripts.combat.CombatScript;
import com.botengine.osrs.scripts.cooking.CookingScript;
import com.botengine.osrs.scripts.crafting.CraftingScript;
import com.botengine.osrs.scripts.fishing.FishingScript;
import com.botengine.osrs.scripts.fletching.FletchingScript;
import com.botengine.osrs.scripts.mining.MiningScript;
import com.botengine.osrs.scripts.smithing.SmithingScript;
import com.botengine.osrs.scripts.woodcutting.WoodcuttingScript;
import net.runelite.client.ui.PluginPanel;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;

/**
 * Axiom side panel — the primary control surface for the bot engine.
 *
 * Layout (top to bottom):
 *   1. Dark header bar with "⚡ AXIOM" branding
 *   2. Scrollable script list — one row per script, each with a 2-letter icon badge
 *      and a ▶ button that opens the per-script config dialog
 *   3. Status bar — current state (RUNNING / STOPPED / PAUSED) + script name
 *   4. Action bar — Configure & Start | Stop | Pause/Resume buttons
 *
 * The panel polls ScriptRunner state every 500ms via a Swing Timer and refreshes
 * the status indicator without requiring any event callbacks from the engine.
 */
@Singleton
public class AxiomPanel extends PluginPanel
{
    private final ScriptRunner runner;
    private final List<BotScript> scripts;

    // ── Status bar widgets (updated by timer) ─────────────────────────────────
    private JLabel statusDot;
    private JLabel statusText;
    private JLabel activeScriptLabel;

    // ── Control buttons ───────────────────────────────────────────────────────
    private AxiomButton btnConfigStart;
    private AxiomButton btnStop;
    private AxiomButton btnPauseResume;

    // ── Selected script ───────────────────────────────────────────────────────
    private BotScript selectedScript;
    private JPanel    selectedRow;

    @Inject
    public AxiomPanel(
        ScriptRunner runner,
        CombatScript    combat,
        WoodcuttingScript woodcutting,
        MiningScript    mining,
        FishingScript   fishing,
        CookingScript   cooking,
        AlchemyScript   alchemy,
        SmithingScript  smithing,
        FletchingScript fletching,
        CraftingScript  crafting
    )
    {
        super(false); // false = no default wrapping scroll pane
        this.runner  = runner;
        this.scripts = Arrays.asList(
            combat, woodcutting, mining, fishing, cooking,
            alchemy, smithing, fletching, crafting
        );

        setLayout(new BorderLayout(0, 0));
        setBackground(AxiomTheme.BG_PANEL);

        add(buildHeader(),     BorderLayout.NORTH);
        add(buildScriptList(), BorderLayout.CENTER);
        add(buildBottom(),     BorderLayout.SOUTH);

        // Auto-select first script
        if (!scripts.isEmpty()) selectScript(scripts.get(0), null);

        // Poll state every 500ms and refresh the status bar
        new Timer(500, e -> SwingUtilities.invokeLater(this::refreshStatus)).start();
    }

    // ── Section builders ──────────────────────────────────────────────────────

    private JPanel buildHeader()
    {
        JPanel header = new JPanel(new BorderLayout(0, 0));
        header.setBackground(AxiomTheme.BG_DEEP);
        header.setBorder(new EmptyBorder(
            AxiomTheme.PAD_LG, AxiomTheme.PAD_LG,
            AxiomTheme.PAD_LG, AxiomTheme.PAD_LG));

        // Logo text
        JLabel logo = new JLabel("⚡ AXIOM");
        logo.setFont(new Font("SansSerif", Font.BOLD, 15));
        logo.setForeground(AxiomTheme.ACCENT);
        header.add(logo, BorderLayout.WEST);

        // Version badge
        JLabel version = new JLabel("v1.0");
        version.setFont(AxiomTheme.fontSmall());
        version.setForeground(AxiomTheme.TEXT_DIM);
        header.add(version, BorderLayout.EAST);

        // Bottom separator
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(AxiomTheme.BG_DEEP);
        wrapper.add(header, BorderLayout.CENTER);
        wrapper.add(makeSeparator(), BorderLayout.SOUTH);
        return wrapper;
    }

    private JScrollPane buildScriptList()
    {
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(AxiomTheme.BG_PANEL);
        list.setBorder(new EmptyBorder(AxiomTheme.PAD_SM, 0, AxiomTheme.PAD_SM, 0));

        // Section label
        JLabel sectionLabel = new JLabel("SCRIPTS");
        sectionLabel.setFont(AxiomTheme.fontSmall());
        sectionLabel.setForeground(AxiomTheme.TEXT_DIM);
        sectionLabel.setBorder(new EmptyBorder(
            AxiomTheme.PAD_MD, AxiomTheme.PAD_LG, AxiomTheme.PAD_XS, AxiomTheme.PAD_LG));
        list.add(sectionLabel);

        for (BotScript script : scripts)
        {
            list.add(buildScriptRow(script));
        }

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBackground(AxiomTheme.BG_PANEL);
        scroll.getViewport().setBackground(AxiomTheme.BG_PANEL);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.getVerticalScrollBar().setBackground(AxiomTheme.BG_DEEP);
        scroll.getVerticalScrollBar().setUnitIncrement(12);
        return scroll;
    }

    private JPanel buildScriptRow(BotScript script)
    {
        JPanel row = new JPanel(new BorderLayout(AxiomTheme.PAD_MD, 0));
        row.setBackground(AxiomTheme.BG_PANEL);
        row.setBorder(new EmptyBorder(
            AxiomTheme.PAD_SM, AxiomTheme.PAD_LG,
            AxiomTheme.PAD_SM, AxiomTheme.PAD_MD));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Icon badge
        String iconText  = AxiomTheme.scriptIconText(script.getName());
        Color  iconColor = AxiomTheme.scriptIconColor(script.getName());

        JLabel badge = new JLabel(iconText, JLabel.CENTER);
        badge.setOpaque(true);
        badge.setBackground(iconColor);
        badge.setForeground(Color.WHITE);
        badge.setFont(new Font("SansSerif", Font.BOLD, 10));
        badge.setPreferredSize(new Dimension(28, 28));
        badge.setMinimumSize(new Dimension(28, 28));
        badge.setMaximumSize(new Dimension(28, 28));
        row.add(badge, BorderLayout.WEST);

        // Script name
        JLabel nameLabel = new JLabel(script.getName());
        nameLabel.setFont(AxiomTheme.fontBody());
        nameLabel.setForeground(AxiomTheme.TEXT);
        row.add(nameLabel, BorderLayout.CENTER);

        // Configure button (▶)
        JLabel configBtn = new JLabel("▶");
        configBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        configBtn.setForeground(AxiomTheme.ACCENT);
        configBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        configBtn.setBorder(new EmptyBorder(0, AxiomTheme.PAD_SM, 0, AxiomTheme.PAD_SM));
        configBtn.addMouseListener(new MouseAdapter()
        {
            @Override public void mouseClicked(MouseEvent e)  { openConfigAndStart(script, row); }
            @Override public void mouseEntered(MouseEvent e)  { configBtn.setForeground(Color.WHITE); }
            @Override public void mouseExited(MouseEvent e)   { configBtn.setForeground(AxiomTheme.ACCENT); }
        });
        row.add(configBtn, BorderLayout.EAST);

        // Row selection on click anywhere
        row.addMouseListener(new MouseAdapter()
        {
            @Override public void mouseClicked(MouseEvent e)  { selectScript(script, row); }
            @Override public void mouseEntered(MouseEvent e)
            {
                if (selectedScript != script)
                    row.setBackground(new Color(45, 45, 45));
            }
            @Override public void mouseExited(MouseEvent e)
            {
                if (selectedScript != script)
                    row.setBackground(AxiomTheme.BG_PANEL);
            }
        });

        return row;
    }

    private JPanel buildBottom()
    {
        JPanel bottom = new JPanel(new BorderLayout(0, 0));
        bottom.setBackground(AxiomTheme.BG_DEEP);
        bottom.add(makeSeparator(), BorderLayout.NORTH);
        bottom.add(buildStatusBar(), BorderLayout.CENTER);
        bottom.add(buildActionBar(), BorderLayout.SOUTH);
        return bottom;
    }

    private JPanel buildStatusBar()
    {
        JPanel bar = new JPanel(new BorderLayout(AxiomTheme.PAD_SM, 0));
        bar.setBackground(AxiomTheme.BG_DEEP);
        bar.setBorder(new EmptyBorder(
            AxiomTheme.PAD_MD, AxiomTheme.PAD_LG,
            AxiomTheme.PAD_MD, AxiomTheme.PAD_LG));

        // State indicator
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        left.setBackground(AxiomTheme.BG_DEEP);

        statusDot = new JLabel("●");
        statusDot.setFont(new Font("SansSerif", Font.PLAIN, 10));
        statusDot.setForeground(AxiomTheme.STATUS_STOPPED);

        statusText = new JLabel("STOPPED");
        statusText.setFont(AxiomTheme.fontSmall());
        statusText.setForeground(AxiomTheme.TEXT_DIM);

        left.add(statusDot);
        left.add(statusText);
        bar.add(left, BorderLayout.WEST);

        // Active script name
        activeScriptLabel = new JLabel("—");
        activeScriptLabel.setFont(AxiomTheme.fontSmall());
        activeScriptLabel.setForeground(AxiomTheme.TEXT_DIM);
        bar.add(activeScriptLabel, BorderLayout.EAST);

        return bar;
    }

    private JPanel buildActionBar()
    {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(AxiomTheme.BG_DEEP);
        outer.add(makeSeparator(), BorderLayout.NORTH);

        JPanel bar = new JPanel(new BorderLayout(AxiomTheme.PAD_SM, AxiomTheme.PAD_SM));
        bar.setBackground(AxiomTheme.BG_DEEP);
        bar.setBorder(new EmptyBorder(
            AxiomTheme.PAD_MD, AxiomTheme.PAD_LG,
            AxiomTheme.PAD_MD, AxiomTheme.PAD_LG));

        // Primary row: [Configure & Start]  [■ Stop]
        JPanel topRow = new JPanel(new BorderLayout(AxiomTheme.PAD_SM, 0));
        topRow.setBackground(AxiomTheme.BG_DEEP);

        btnConfigStart = new AxiomButton("▶  Configure & Start", AxiomButton.Variant.PRIMARY);
        btnStop        = new AxiomButton("■  Stop",              AxiomButton.Variant.DANGER);

        btnConfigStart.addActionListener(e -> openConfigAndStart(selectedScript, selectedRow));
        btnStop.addActionListener(e -> runner.stop());

        topRow.add(btnConfigStart, BorderLayout.CENTER);
        topRow.add(btnStop,        BorderLayout.EAST);
        bar.add(topRow, BorderLayout.NORTH);

        // Secondary row: [⏸ Pause / ▶ Resume]  centered
        JPanel bottomRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        bottomRow.setBackground(AxiomTheme.BG_DEEP);

        btnPauseResume = new AxiomButton("⏸  Pause", AxiomButton.Variant.NEUTRAL);
        btnPauseResume.addActionListener(e -> togglePauseResume());
        bottomRow.add(btnPauseResume);
        bar.add(bottomRow, BorderLayout.CENTER);

        outer.add(bar, BorderLayout.CENTER);
        return outer;
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void selectScript(BotScript script, JPanel row)
    {
        // Deselect previous
        if (selectedRow != null)
        {
            selectedRow.setBackground(AxiomTheme.BG_PANEL);
            for (java.awt.Component c : selectedRow.getComponents())
                c.setBackground(AxiomTheme.BG_PANEL);
        }

        selectedScript = script;
        selectedRow    = row;

        if (row != null)
        {
            row.setBackground(AxiomTheme.BG_SELECTED);
            for (java.awt.Component c : row.getComponents())
                c.setBackground(AxiomTheme.BG_SELECTED);
        }

        // Update button label to selected script name
        if (script != null && btnConfigStart != null)
        {
            btnConfigStart.setText("▶  Configure " + script.getName());
        }
    }

    private void openConfigAndStart(BotScript script, JPanel row)
    {
        if (script == null) return;
        selectScript(script, row);

        ScriptConfigDialog<?> dialog = script.createConfigDialog(this);
        if (dialog == null) return;

        boolean confirmed = dialog.showDialog();
        if (confirmed)
        {
            ScriptSettings settings = dialog.getSettings();
            runner.start(script, settings);
        }
    }

    private void togglePauseResume()
    {
        ScriptState state = runner.getState();
        if (state == ScriptState.RUNNING)
        {
            runner.pause();
        }
        else if (state == ScriptState.PAUSED)
        {
            runner.resume();
        }
    }

    // ── Status refresh ────────────────────────────────────────────────────────

    private void refreshStatus()
    {
        ScriptState state = runner.getState();
        BotScript   active = runner.getActiveScript();

        switch (state)
        {
            case RUNNING:
                statusDot.setForeground(AxiomTheme.STATUS_RUNNING);
                statusText.setText("RUNNING");
                statusText.setForeground(AxiomTheme.STATUS_RUNNING);
                break;
            case PAUSED:
                statusDot.setForeground(AxiomTheme.STATUS_PAUSED);
                statusText.setText("PAUSED");
                statusText.setForeground(AxiomTheme.STATUS_PAUSED);
                break;
            case BREAKING:
                statusDot.setForeground(AxiomTheme.STATUS_BREAKING);
                statusText.setText("BREAK");
                statusText.setForeground(AxiomTheme.STATUS_BREAKING);
                break;
            default:
                statusDot.setForeground(AxiomTheme.STATUS_STOPPED);
                statusText.setText("STOPPED");
                statusText.setForeground(AxiomTheme.TEXT_DIM);
                break;
        }

        activeScriptLabel.setText(active != null ? active.getName() : "—");

        // Enable/disable controls based on state
        boolean isRunning = (state == ScriptState.RUNNING || state == ScriptState.PAUSED || state == ScriptState.BREAKING);
        btnStop.setEnabled(isRunning);
        btnPauseResume.setEnabled(isRunning && state != ScriptState.BREAKING);
        btnPauseResume.setText(state == ScriptState.PAUSED ? "▶  Resume" : "⏸  Pause");
        btnConfigStart.setEnabled(!isRunning || state == ScriptState.STOPPED);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static JPanel makeSeparator()
    {
        JPanel sep = new JPanel();
        sep.setBackground(AxiomTheme.BG_DEEP);
        sep.setBorder(new MatteBorder(1, 0, 0, 0, AxiomTheme.BORDER));
        sep.setPreferredSize(new Dimension(0, 1));
        return sep;
    }
}
