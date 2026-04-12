package com.axiom.plugin.ui;

import com.axiom.scripts.combat.CombatSettings;

import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration dialog for the Combat script.
 *
 * NPC name and item IDs are entered as free text. Food and loot ID fields
 * accept comma-separated integers (e.g. "385,379,361"). Invalid tokens
 * are silently skipped during parsing.
 */
public class CombatConfigDialog extends ScriptConfigDialog<CombatSettings>
{
    // ── Controls ──────────────────────────────────────────────────────────────
    private JTextField npcNameField;
    private JSpinner   eatAtHpSpinner;
    private JTextField foodIdsField;
    private JTextField lootIdsField;
    private JCheckBox  stopOnFoodBox;
    private JSpinner   breakIntervalSpinner;
    private JSpinner   breakDurationSpinner;

    public CombatConfigDialog(JComponent parent)
    {
        super(parent);
    }

    @Override
    public String getScriptName() { return "Axiom Combat"; }

    @Override
    protected JPanel buildContent()
    {
        JPanel root = new JPanel();
        root.setLayout(new javax.swing.BoxLayout(root, javax.swing.BoxLayout.Y_AXIS));
        root.setBackground(AxiomTheme.BG_PANEL);
        root.setBorder(new javax.swing.border.EmptyBorder(
            AxiomTheme.PAD_MD, AxiomTheme.PAD_LG, AxiomTheme.PAD_MD, AxiomTheme.PAD_LG));

        // ── Section: Target ───────────────────────────────────────────────────
        AxiomSectionPanel targetSection = new AxiomSectionPanel("TARGET NPC");
        npcNameField = makeTextField("Hill Giant");
        targetSection.addRow("NPC name", npcNameField);
        root.add(targetSection);
        root.add(Box.createRigidArea(new Dimension(0, AxiomTheme.PAD_SM)));

        // ── Section: Food / HP ────────────────────────────────────────────────
        AxiomSectionPanel foodSection = new AxiomSectionPanel("FOOD & HEALTH");
        eatAtHpSpinner = makeSpinner(10, 90, 50);
        foodSection.addRow("Eat at HP %", eatAtHpSpinner);
        foodIdsField = makeTextField("379");
        foodSection.addRow("Food IDs (comma-separated)", foodIdsField);
        stopOnFoodBox = makeCheckBox("Stop when out of food", true);
        foodSection.addCheckRow("", stopOnFoodBox);
        root.add(foodSection);
        root.add(Box.createRigidArea(new Dimension(0, AxiomTheme.PAD_SM)));

        // ── Section: Looting ──────────────────────────────────────────────────
        AxiomSectionPanel lootSection = new AxiomSectionPanel("LOOTING");
        lootIdsField = makeTextField("Leave blank to skip looting");
        lootSection.addRow("Loot IDs (comma-separated)", lootIdsField);
        root.add(lootSection);
        root.add(Box.createRigidArea(new Dimension(0, AxiomTheme.PAD_SM)));

        // ── Section: Antiban ──────────────────────────────────────────────────
        AxiomSectionPanel antibanSection = new AxiomSectionPanel("ANTIBAN / BREAKS");
        breakIntervalSpinner = makeSpinner(10, 240, 60);
        antibanSection.addRow("Break every (min)", breakIntervalSpinner);
        breakDurationSpinner = makeSpinner(1, 30, 5);
        antibanSection.addRow("Break length (min)", breakDurationSpinner);
        root.add(antibanSection);
        root.add(Box.createRigidArea(new Dimension(0, AxiomTheme.PAD_SM)));

        return root;
    }

    @Override
    public CombatSettings getSettings()
    {
        String npcName = npcNameField.getText().trim();
        if (npcName.isEmpty()) npcName = "Hill Giant";

        return new CombatSettings(
            npcName,
            (Integer) eatAtHpSpinner.getValue(),
            parseIds(foodIdsField.getText()),
            parseIds(lootIdsField.getText()),
            stopOnFoodBox.isSelected(),
            (Integer) breakIntervalSpinner.getValue(),
            (Integer) breakDurationSpinner.getValue()
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Parses a comma-separated string of integers. Invalid/empty tokens are skipped. */
    private static int[] parseIds(String text)
    {
        if (text == null || text.trim().isEmpty()) return new int[0];
        List<Integer> ids = new ArrayList<>();
        for (String part : text.split(","))
        {
            try { ids.add(Integer.parseInt(part.trim())); }
            catch (NumberFormatException ignored) {}
        }
        return ids.stream().mapToInt(Integer::intValue).toArray();
    }
}
