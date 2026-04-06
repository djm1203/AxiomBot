package com.botengine.osrs;

import com.botengine.osrs.overlay.BotOverlay;
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
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.PluginPanel;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Side panel UI for the Bot Engine plugin.
 *
 * Shows:
 *   - Script selector dropdown (all registered scripts)
 *   - Start / Pause / Stop control buttons
 *   - Current state label (updates each button press)
 *
 * Scripts are registered in the SCRIPTS map below.
 * To add a new script: add an entry to SCRIPTS with its Provider.
 *
 * Providers are used instead of direct instances so that scripts are created
 * fresh each time Start is pressed, resetting their internal state cleanly.
 *
 * All UI updates are dispatched on the Swing EDT via SwingUtilities.invokeLater().
 */
@Slf4j
public class BotEnginePanel extends PluginPanel
{
    private static final Color COLOR_START  = new Color(0, 180, 0);
    private static final Color COLOR_PAUSE  = new Color(200, 180, 0);
    private static final Color COLOR_STOP   = new Color(200, 0, 0);
    private static final Color COLOR_BG     = new Color(40, 40, 40);

    private final ScriptRunner scriptRunner;
    private final BotOverlay botOverlay;

    // Script registry: display name → Provider<BotScript>
    // Provider creates a fresh instance each time Start is clicked.
    private final Map<String, Provider<? extends BotScript>> scripts;

    // UI components
    private JComboBox<String> scriptSelector;
    private JButton btnStart;
    private JButton btnPause;
    private JButton btnStop;
    private JLabel stateLabel;

    @Inject
    public BotEnginePanel(
        ScriptRunner scriptRunner,
        BotOverlay botOverlay,
        Provider<WoodcuttingScript> woodcutting,
        Provider<AlchemyScript> alchemy,
        Provider<CraftingScript> crafting,
        Provider<FishingScript> fishing,
        Provider<MiningScript> mining,
        Provider<CookingScript> cooking,
        Provider<CombatScript> combat,
        Provider<SmithingScript> smithing,
        Provider<FletchingScript> fletching
    )
    {
        this.scriptRunner = scriptRunner;
        this.botOverlay = botOverlay;

        // Register all scripts in display order
        scripts = new LinkedHashMap<>();
        scripts.put("Woodcutting",  woodcutting);
        scripts.put("High Alchemy", alchemy);
        scripts.put("Gem Cutting",  crafting);
        scripts.put("Fishing",      fishing);
        scripts.put("Mining",       mining);
        scripts.put("Cooking",      cooking);
        scripts.put("Combat",       combat);
        scripts.put("Smithing",     smithing);
        scripts.put("Fletching",    fletching);

        buildUI();
    }

    // ── UI construction ───────────────────────────────────────────────────────

    private void buildUI()
    {
        setLayout(new BorderLayout(0, 8));
        setBackground(COLOR_BG);
        setBorder(new EmptyBorder(10, 10, 10, 10));

        // Title
        JLabel title = new JLabel("Bot Engine");
        title.setFont(new Font("Arial", Font.BOLD, 16));
        title.setForeground(Color.CYAN);
        title.setBorder(new EmptyBorder(0, 0, 8, 0));
        add(title, BorderLayout.NORTH);

        // Center panel
        JPanel center = new JPanel(new GridLayout(0, 1, 0, 6));
        center.setBackground(COLOR_BG);

        // Script selector
        JLabel selectorLabel = new JLabel("Select script:");
        selectorLabel.setForeground(Color.LIGHT_GRAY);
        center.add(selectorLabel);

        scriptSelector = new JComboBox<>(scripts.keySet().toArray(new String[0]));
        scriptSelector.setBackground(new Color(60, 60, 60));
        scriptSelector.setForeground(Color.WHITE);
        scriptSelector.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        center.add(scriptSelector);

        // State label
        stateLabel = new JLabel("State: Stopped");
        stateLabel.setForeground(Color.GRAY);
        stateLabel.setBorder(new EmptyBorder(4, 0, 4, 0));
        center.add(stateLabel);

        add(center, BorderLayout.CENTER);

        // Button panel
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        buttons.setBackground(COLOR_BG);
        buttons.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        btnStart = makeButton("Start", COLOR_START);
        btnPause = makeButton("Pause", COLOR_PAUSE);
        btnStop  = makeButton("Stop",  COLOR_STOP);

        btnPause.setEnabled(false);
        btnStop.setEnabled(false);

        btnStart.addActionListener(e -> onStart());
        btnPause.addActionListener(e -> onPause());
        btnStop.addActionListener(e -> onStop());

        buttons.add(btnStart);
        buttons.add(btnPause);
        buttons.add(btnStop);

        add(buttons, BorderLayout.SOUTH);
    }

    // ── Button actions ────────────────────────────────────────────────────────

    private void onStart()
    {
        String selected = (String) scriptSelector.getSelectedItem();
        if (selected == null) return;

        Provider<? extends BotScript> provider = scripts.get(selected);
        if (provider == null) return;

        BotScript script = provider.get();
        botOverlay.resetStartTime();
        scriptRunner.start(script);

        updateButtons(ScriptState.RUNNING);
        log.info("Panel: started script '{}'", selected);
    }

    private void onPause()
    {
        ScriptState current = scriptRunner.getState();
        if (current == ScriptState.RUNNING || current == ScriptState.BREAKING)
        {
            scriptRunner.pause();
            updateButtons(ScriptState.PAUSED);
        }
        else if (current == ScriptState.PAUSED)
        {
            scriptRunner.resume();
            updateButtons(ScriptState.RUNNING);
        }
    }

    private void onStop()
    {
        scriptRunner.stop();
        updateButtons(ScriptState.STOPPED);
    }

    // ── UI state ──────────────────────────────────────────────────────────────

    private void updateButtons(ScriptState state)
    {
        SwingUtilities.invokeLater(() -> {
            switch (state)
            {
                case RUNNING:
                    btnStart.setEnabled(false);
                    btnPause.setEnabled(true);
                    btnPause.setText("Pause");
                    btnStop.setEnabled(true);
                    stateLabel.setText("State: Running");
                    stateLabel.setForeground(new Color(0, 200, 0));
                    break;

                case PAUSED:
                    btnStart.setEnabled(false);
                    btnPause.setEnabled(true);
                    btnPause.setText("Resume");
                    btnStop.setEnabled(true);
                    stateLabel.setText("State: Paused");
                    stateLabel.setForeground(new Color(200, 200, 0));
                    break;

                case STOPPED:
                default:
                    btnStart.setEnabled(true);
                    btnPause.setEnabled(false);
                    btnPause.setText("Pause");
                    btnStop.setEnabled(false);
                    stateLabel.setText("State: Stopped");
                    stateLabel.setForeground(Color.GRAY);
                    break;
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private JButton makeButton(String text, Color bg)
    {
        JButton btn = new JButton(text);
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setPreferredSize(new Dimension(72, 28));
        return btn;
    }
}
