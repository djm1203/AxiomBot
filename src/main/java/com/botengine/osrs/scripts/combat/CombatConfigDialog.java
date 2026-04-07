package com.botengine.osrs.scripts.combat;

import com.botengine.osrs.ui.AxiomButton;
import com.botengine.osrs.ui.AxiomSectionPanel;
import com.botengine.osrs.ui.AxiomTheme;
import com.botengine.osrs.ui.ScriptConfigDialog;

import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;

/**
 * Configuration dialog for CombatScript.
 *
 * Sections:
 *   TARGETING    — NPC name
 *   FOOD         — eat HP%, emergency logout
 *   PRAYERS      — protective/offensive prayer, pot threshold
 *   LOOT         — item ID list with add/remove
 *   ADVANCED     — spec, banking, sand crabs, cannon mode
 */
public class CombatConfigDialog extends ScriptConfigDialog<CombatSettings>
{
    // ── UI fields ─────────────────────────────────────────────────────────────
    private JTextField  tfTarget;
    private JSpinner    spEatPercent;
    private JCheckBox   cbEmergencyLogout;
    private JSpinner    spEmergencyHp;
    private JCheckBox   cbUsePrayer;
    private JComboBox<String> cbProtectivePrayer;
    private JComboBox<String> cbOffensivePrayer;
    private JSpinner    spPrayerPotPercent;
    private JCheckBox   cbLootEnabled;
    private DefaultListModel<String> lootModel;
    private JTextField  tfAddLoot;
    private JCheckBox   cbUseSpec;
    private JSpinner    spSpecThreshold;
    private JCheckBox   cbBankingMode;
    private JCheckBox   cbSandCrabs;
    private JCheckBox   cbCannon;
    private JSpinner    spCannonThreshold;

    public CombatConfigDialog(JComponent parent)
    {
        super(parent);
        // Load saved settings and populate fields
        CombatSettings saved = CombatSettings.load(CombatSettings.class, getScriptName());
        applySettings(saved);
    }

    @Override
    public String getScriptName() { return "Combat"; }

    @Override
    protected JPanel buildContent()
    {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(AxiomTheme.BG_PANEL);
        root.setBorder(new EmptyBorder(8, 8, 8, 8));

        // ── TARGETING ─────────────────────────────────────────────────────────
        AxiomSectionPanel targeting = new AxiomSectionPanel("TARGETING");
        tfTarget = makeTextField("e.g. Hill Giant");
        targeting.addRow("Target NPC", tfTarget);
        root.add(targeting);

        // ── FOOD & HEALTH ─────────────────────────────────────────────────────
        AxiomSectionPanel food = new AxiomSectionPanel("FOOD & HEALTH");
        spEatPercent = makeSpinner(10, 95, 50);
        food.addRow("Eat below HP %", spEatPercent);

        cbEmergencyLogout = makeCheckBox("Emergency logout", false);
        food.addCheckRow("", cbEmergencyLogout);

        spEmergencyHp = makeSpinner(1, 30, 10);
        food.addRow("Logout at HP %", spEmergencyHp);
        root.add(food);

        // ── PRAYERS ───────────────────────────────────────────────────────────
        AxiomSectionPanel prayers = new AxiomSectionPanel("PRAYERS");
        cbUsePrayer = makeCheckBox("Use prayer", false);
        prayers.addCheckRow("", cbUsePrayer);

        cbProtectivePrayer = makeCombo(
            "PROTECT_FROM_MELEE", "PROTECT_FROM_MISSILES", "PROTECT_FROM_MAGIC");
        prayers.addRow("Protective", cbProtectivePrayer);

        cbOffensivePrayer = makeCombo(
            "", "PIETY", "RIGOUR", "AUGURY", "CHIVALRY",
            "EAGLE_EYE", "MYSTIC_MIGHT", "HAWK_EYE");
        prayers.addRow("Offensive", cbOffensivePrayer);

        spPrayerPotPercent = makeSpinner(5, 80, 30);
        prayers.addRow("Drink pot at %", spPrayerPotPercent);
        root.add(prayers);

        // ── LOOT ──────────────────────────────────────────────────────────────
        AxiomSectionPanel loot = new AxiomSectionPanel("LOOT");
        cbLootEnabled = makeCheckBox("Pick up loot after kills", false);
        loot.addCheckRow("", cbLootEnabled);

        lootModel = new DefaultListModel<>();
        lootModel.addElement("995  (Coins)");
        JList<String> lootList = new JList<>(lootModel);
        lootList.setBackground(AxiomTheme.BG_INPUT);
        lootList.setForeground(AxiomTheme.TEXT);
        lootList.setFont(AxiomTheme.fontSmall());
        JScrollPane lootScroll = new JScrollPane(lootList);
        lootScroll.setPreferredSize(new Dimension(0, 80));
        lootScroll.setBorder(null);
        loot.addFull(lootScroll);

        JPanel lootControls = new JPanel(new BorderLayout(4, 0));
        lootControls.setBackground(AxiomTheme.BG_PANEL);
        tfAddLoot = makeTextField("Item ID");
        tfAddLoot.setPreferredSize(new Dimension(0, 26));
        AxiomButton btnAdd = new AxiomButton("+ Add", AxiomButton.Variant.NEUTRAL);
        AxiomButton btnRemove = new AxiomButton("Remove", AxiomButton.Variant.DANGER);

        btnAdd.addActionListener(e -> {
            String id = tfAddLoot.getText().trim();
            if (!id.isEmpty()) { lootModel.addElement(id); tfAddLoot.setText(""); }
        });
        btnRemove.addActionListener(e -> {
            int idx = lootList.getSelectedIndex();
            if (idx >= 0) lootModel.remove(idx);
        });

        JPanel lootBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        lootBtns.setBackground(AxiomTheme.BG_PANEL);
        lootBtns.add(btnAdd);
        lootBtns.add(btnRemove);

        lootControls.add(tfAddLoot, BorderLayout.CENTER);
        lootControls.add(lootBtns, BorderLayout.EAST);
        loot.addFull(lootControls);
        root.add(loot);

        // ── ADVANCED ──────────────────────────────────────────────────────────
        AxiomSectionPanel advanced = new AxiomSectionPanel("ADVANCED");
        cbUseSpec = makeCheckBox("Special attack", false);
        advanced.addCheckRow("", cbUseSpec);
        spSpecThreshold = makeSpinner(25, 100, 100);
        advanced.addRow("Spec at % energy", spSpecThreshold);
        advanced.addSpacer();

        cbBankingMode = makeCheckBox("Banking mode (restocks food)", false);
        advanced.addCheckRow("", cbBankingMode);
        cbSandCrabs = makeCheckBox("Sand Crabs aggro reset", false);
        advanced.addCheckRow("", cbSandCrabs);
        cbCannon = makeCheckBox("Dwarf cannon mode", false);
        advanced.addCheckRow("", cbCannon);
        spCannonThreshold = makeSpinner(10, 100, 20);
        advanced.addRow("Refill every N ticks", spCannonThreshold);
        root.add(advanced);

        return root;
    }

    @Override
    public CombatSettings getSettings()
    {
        CombatSettings s = new CombatSettings();
        s.targetNpcName           = tfTarget.getText().trim();
        s.eatThresholdPercent     = (int) spEatPercent.getValue();
        s.emergencyLogoutEnabled  = cbEmergencyLogout.isSelected();
        s.emergencyLogoutHpPercent = (int) spEmergencyHp.getValue();
        s.usePrayer               = cbUsePrayer.isSelected();
        s.protectivePrayer        = (String) cbProtectivePrayer.getSelectedItem();
        s.offensivePrayer         = (String) cbOffensivePrayer.getSelectedItem();
        s.prayerPotPercent        = (int) spPrayerPotPercent.getValue();
        s.lootEnabled             = cbLootEnabled.isSelected();
        s.lootItemIds             = buildLootIds();
        s.useSpec                 = cbUseSpec.isSelected();
        s.specThreshold           = (int) spSpecThreshold.getValue();
        s.bankingMode             = cbBankingMode.isSelected();
        s.sandCrabsMode           = cbSandCrabs.isSelected();
        s.cannonMode              = cbCannon.isSelected();
        s.cannonRefillThreshold   = (int) spCannonThreshold.getValue();
        settings = s;
        return s;
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void applySettings(CombatSettings s)
    {
        if (s == null || tfTarget == null) return;
        tfTarget.setText(s.targetNpcName);
        spEatPercent.setValue(s.eatThresholdPercent);
        cbEmergencyLogout.setSelected(s.emergencyLogoutEnabled);
        spEmergencyHp.setValue(s.emergencyLogoutHpPercent);
        cbUsePrayer.setSelected(s.usePrayer);
        setComboSelection(cbProtectivePrayer, s.protectivePrayer);
        setComboSelection(cbOffensivePrayer, s.offensivePrayer);
        spPrayerPotPercent.setValue(s.prayerPotPercent);
        cbLootEnabled.setSelected(s.lootEnabled);
        if (s.lootItemIds != null && !s.lootItemIds.isEmpty())
        {
            lootModel.clear();
            for (String id : s.lootItemIds.split(","))
            {
                String t = id.trim();
                if (!t.isEmpty()) lootModel.addElement(t);
            }
        }
        cbUseSpec.setSelected(s.useSpec);
        spSpecThreshold.setValue(s.specThreshold);
        cbBankingMode.setSelected(s.bankingMode);
        cbSandCrabs.setSelected(s.sandCrabsMode);
        cbCannon.setSelected(s.cannonMode);
        spCannonThreshold.setValue(s.cannonRefillThreshold);
    }

    private String buildLootIds()
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lootModel.size(); i++)
        {
            if (i > 0) sb.append(",");
            // Strip any description part (e.g. "995  (Coins)" → "995")
            sb.append(lootModel.get(i).split(" ")[0]);
        }
        return sb.toString();
    }

    private void setComboSelection(JComboBox<String> combo, String value)
    {
        if (value == null) return;
        for (int i = 0; i < combo.getItemCount(); i++)
        {
            if (value.equals(combo.getItemAt(i))) { combo.setSelectedIndex(i); return; }
        }
    }
}
