package com.axiom.plugin.ui;

import com.axiom.api.script.BotScript;
import com.axiom.api.script.ScriptManifest;
import com.axiom.api.script.ScriptSettings;
import com.axiom.plugin.ScriptLoader;
import com.axiom.plugin.ScriptRunner;
import com.axiom.plugin.ScriptState;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.PluginPanel;

import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.List;

/**
 * The Axiom side panel in RuneLite.
 *
 * Script list is populated dynamically from ScriptLoader — never hardcoded.
 * Adding a new script = add the JAR to the classpath. Zero changes here.
 */
@Slf4j
public class AxiomPanel extends PluginPanel
{
    private final ScriptRunner runner;

    // ── Script list ───────────────────────────────────────────────────────────
    private List<BotScript> scripts;
    private JList<String>   scriptList;

    // ── Controls ──────────────────────────────────────────────────────────────
    private AxiomButton startBtn;
    private AxiomButton pauseBtn;
    private AxiomButton stopBtn;

    // ── Status bar ────────────────────────────────────────────────────────────
    private JLabel statusLabel;
    private Timer  statusTimer;

    // ── Runtime ──────────────────────────────────────────────────────────────
    private long scriptStartMs = 0;

    @Inject
    public AxiomPanel(ScriptRunner runner)
    {
        this.runner = runner;
        setLayout(new BorderLayout(0, 0));
        setBackground(AxiomTheme.BG_PANEL);
        buildPanel();
        startStatusTimer();
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    private void buildPanel()
    {
        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(AxiomTheme.BG_DEEP);
        header.setBorder(new EmptyBorder(AxiomTheme.PAD_LG, AxiomTheme.PAD_LG,
            AxiomTheme.PAD_LG, AxiomTheme.PAD_LG));

        JLabel title = new JLabel("⚡ AXIOM");
        title.setFont(AxiomTheme.fontHeading());
        title.setForeground(AxiomTheme.ACCENT);
        header.add(title, BorderLayout.WEST);
        add(header, BorderLayout.NORTH);

        // Script list
        scripts = ScriptLoader.loadScripts();
        String[] names = scripts.stream()
            .map(BotScript::getName)
            .toArray(String[]::new);

        scriptList = new JList<>(names);
        scriptList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        scriptList.setBackground(AxiomTheme.BG_CARD);
        scriptList.setForeground(AxiomTheme.TEXT);
        scriptList.setFont(AxiomTheme.fontBody());
        scriptList.setSelectionBackground(AxiomTheme.BG_SELECTED);
        scriptList.setSelectionForeground(AxiomTheme.TEXT);
        scriptList.setBorder(new EmptyBorder(4, 8, 4, 8));
        scriptList.setFixedCellHeight(28);

        JScrollPane listScroll = new JScrollPane(scriptList);
        listScroll.setBackground(AxiomTheme.BG_CARD);
        listScroll.getViewport().setBackground(AxiomTheme.BG_CARD);
        listScroll.setBorder(BorderFactory.createEmptyBorder());

        JPanel center = new JPanel(new BorderLayout());
        center.setBackground(AxiomTheme.BG_PANEL);
        center.setBorder(new EmptyBorder(AxiomTheme.PAD_MD, AxiomTheme.PAD_MD,
            0, AxiomTheme.PAD_MD));
        center.add(listScroll, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        // Bottom: buttons + status
        JPanel bottom = new JPanel();
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.setBackground(AxiomTheme.BG_PANEL);
        bottom.setBorder(new EmptyBorder(AxiomTheme.PAD_SM, AxiomTheme.PAD_MD,
            AxiomTheme.PAD_MD, AxiomTheme.PAD_MD));

        // Button row
        JPanel btnRow = new JPanel(new java.awt.GridLayout(1, 3, 4, 0));
        btnRow.setBackground(AxiomTheme.BG_PANEL);
        btnRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        startBtn = new AxiomButton("Start",  AxiomButton.Variant.PRIMARY);
        pauseBtn = new AxiomButton("Pause",  AxiomButton.Variant.NEUTRAL);
        stopBtn  = new AxiomButton("Stop",   AxiomButton.Variant.DANGER);

        startBtn.addActionListener(e -> onStartClicked());
        pauseBtn.addActionListener(e -> onPauseClicked());
        stopBtn.addActionListener(e  -> onStopClicked());

        btnRow.add(startBtn);
        btnRow.add(pauseBtn);
        btnRow.add(stopBtn);
        bottom.add(btnRow);
        bottom.add(javax.swing.Box.createRigidArea(new Dimension(0, AxiomTheme.PAD_SM)));

        // Status bar
        statusLabel = new JLabel("STOPPED");
        statusLabel.setFont(AxiomTheme.fontSmall());
        statusLabel.setForeground(AxiomTheme.STATUS_STOPPED);
        statusLabel.setBorder(new EmptyBorder(2, 0, 2, 0));
        bottom.add(statusLabel);

        add(bottom, BorderLayout.SOUTH);

        updateButtons(ScriptState.STOPPED);
    }

    // ── Button handlers ───────────────────────────────────────────────────────

    private void onStartClicked()
    {
        int idx = scriptList.getSelectedIndex();
        if (idx < 0 || idx >= scripts.size()) return;

        BotScript script = scripts.get(idx);

        // Open config dialog if the script has one
        ScriptSettings settings = openConfigDialog(script);
        // settings may be null for scripts without a dialog — that's fine

        scriptStartMs = System.currentTimeMillis();
        runner.start(script, settings);
        updateButtons(ScriptState.RUNNING);
    }

    private void onPauseClicked()
    {
        if (runner.getState() == ScriptState.RUNNING)
        {
            runner.pause();
            pauseBtn.setText("Resume");
        }
        else if (runner.getState() == ScriptState.PAUSED)
        {
            runner.resume();
            pauseBtn.setText("Pause");
        }
    }

    private void onStopClicked()
    {
        runner.stop();
        pauseBtn.setText("Pause");
        updateButtons(ScriptState.STOPPED);
    }

    // ── Config dialog ─────────────────────────────────────────────────────────

    /**
     * Opens the config dialog for the given script by naming convention.
     *
     * Convention: for a script class named XxxScript, look for XxxConfigDialog
     * in the com.axiom.plugin.ui package. If found, instantiate it and show it.
     * If not found (ClassNotFoundException), start the script with default settings.
     *
     * Adding a new script never requires modifying this method — just create the
     * dialog class in com.axiom.plugin.ui following the naming convention.
     *
     * Returns the populated ScriptSettings, or null if the user cancelled or the
     * script has no config dialog.
     */
    @SuppressWarnings("unchecked")
    private ScriptSettings openConfigDialog(BotScript script)
    {
        String simpleName = script.getClass().getSimpleName(); // e.g. "FishingScript"
        if (!simpleName.endsWith("Script"))
        {
            log.warn("AxiomPanel: script class {} does not follow XxxScript convention", simpleName);
            return null;
        }

        String baseName  = simpleName.substring(0, simpleName.length() - "Script".length());
        String dialogFqn = "com.axiom.plugin.ui." + baseName + "ConfigDialog";

        try
        {
            Class<?> cls = Class.forName(dialogFqn);
            if (!ScriptConfigDialog.class.isAssignableFrom(cls))
            {
                log.warn("AxiomPanel: {} does not extend ScriptConfigDialog", dialogFqn);
                return null;
            }

            ScriptConfigDialog<ScriptSettings> dialog =
                (ScriptConfigDialog<ScriptSettings>) cls
                    .getConstructor(JComponent.class)
                    .newInstance(this);

            return dialog.showDialog() ? dialog.getSettings() : null;
        }
        catch (ClassNotFoundException e)
        {
            return null; // no config dialog — start with defaults
        }
        catch (Exception e)
        {
            log.warn("AxiomPanel: could not open config dialog {}: {}", dialogFqn, e.getMessage());
            return null;
        }
    }

    // ── Status polling ────────────────────────────────────────────────────────

    private void startStatusTimer()
    {
        statusTimer = new Timer(500, e -> SwingUtilities.invokeLater(this::refreshStatus));
        statusTimer.start();
    }

    private void refreshStatus()
    {
        ScriptState state  = runner.getState();
        BotScript   active = runner.getActiveScript();

        updateButtons(state);

        String name    = active != null ? active.getName() : "";
        String elapsed = scriptStartMs > 0 ? formatElapsed(scriptStartMs) : "";

        switch (state)
        {
            case RUNNING:
                statusLabel.setForeground(AxiomTheme.STATUS_RUNNING);
                statusLabel.setText("RUNNING  " + name + (elapsed.isEmpty() ? "" : "  " + elapsed));
                break;
            case PAUSED:
                statusLabel.setForeground(AxiomTheme.STATUS_PAUSED);
                statusLabel.setText("PAUSED  " + name);
                break;
            case BREAKING:
                statusLabel.setForeground(AxiomTheme.STATUS_BREAKING);
                statusLabel.setText("BREAK  " + name);
                break;
            default:
                statusLabel.setForeground(AxiomTheme.STATUS_STOPPED);
                statusLabel.setText("STOPPED");
                scriptStartMs = 0;
                break;
        }
    }

    private void updateButtons(ScriptState state)
    {
        boolean running = state == ScriptState.RUNNING || state == ScriptState.BREAKING;
        startBtn.setEnabled(!running && state == ScriptState.STOPPED);
        pauseBtn.setEnabled(running || state == ScriptState.PAUSED);
        stopBtn.setEnabled(state != ScriptState.STOPPED);
    }

    private static String formatElapsed(long startMs)
    {
        long s = (System.currentTimeMillis() - startMs) / 1000;
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }
}
