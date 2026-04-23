package com.axiom.plugin.ui;

import com.axiom.scripts.fletching.FletchingSettings;
import com.axiom.scripts.fletching.FletchingSettings.BowType;
import com.axiom.scripts.fletching.FletchingSettings.DartType;
import com.axiom.scripts.fletching.FletchingSettings.FletchingMethod;
import com.axiom.scripts.fletching.FletchingSettings.KnifeProduct;
import com.axiom.scripts.fletching.FletchingSettings.LogType;

import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;

/**
 * Configuration dialog for the Fletching script.
 *
 * The item-type section uses a CardLayout to show only the relevant
 * combo (logs / bows / darts) for the selected method. Switching the
 * method combo instantly swaps the card.
 */
public class FletchingConfigDialog extends ScriptConfigDialog<FletchingSettings>
{
    // ── Controls ──────────────────────────────────────────────────────────────
    private JComboBox<String> methodCombo;
    private JComboBox<String> logCombo;
    private JComboBox<String> knifeProductCombo;
    private JComboBox<String> bowCombo;
    private JComboBox<String> dartCombo;
    private JPanel            itemCardPanel;
    private JCheckBox         bankBox;
    private JSpinner          breakIntervalSpinner;
    private JSpinner          breakDurationSpinner;

    public FletchingConfigDialog(JComponent parent)
    {
        super(parent);
    }

    @Override
    public String getScriptName() { return "Axiom Fletching"; }

    @Override
    protected JPanel buildContent()
    {
        JPanel root = new JPanel();
        root.setLayout(new javax.swing.BoxLayout(root, javax.swing.BoxLayout.Y_AXIS));
        root.setBackground(AxiomTheme.BG_PANEL);
        root.setBorder(new javax.swing.border.EmptyBorder(
            AxiomTheme.PAD_MD, AxiomTheme.PAD_LG, AxiomTheme.PAD_MD, AxiomTheme.PAD_LG));

        // ── Section: Method ──────────────────────────────────────────────────
        AxiomSectionPanel methodSection = new AxiomSectionPanel("METHOD");
        methodCombo = makeCombo(methodDisplayNames());
        methodSection.addRow("Method", methodCombo);
        root.add(methodSection);
        root.add(Box.createRigidArea(new Dimension(0, AxiomTheme.PAD_SM)));

        // ── Dynamic item section (CardLayout switches on method change) ──────
        itemCardPanel = new JPanel(new CardLayout());
        itemCardPanel.setBackground(AxiomTheme.BG_PANEL);
        itemCardPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        AxiomSectionPanel logSection = new AxiomSectionPanel("LOG TYPE");
        logCombo = makeCombo(logDisplayNames());
        logCombo.setSelectedItem(logDisplayName(LogType.OAK));
        logSection.addRow("Log type", logCombo);
        knifeProductCombo = makeCombo(knifeProductDisplayNames());
        logSection.addRow("Knife product", knifeProductCombo);
        itemCardPanel.add(logSection, "knife");

        AxiomSectionPanel bowSection = new AxiomSectionPanel("BOW TYPE");
        bowCombo = makeCombo(bowDisplayNames());
        bowSection.addRow("Bow type", bowCombo);
        itemCardPanel.add(bowSection, "string");

        AxiomSectionPanel dartSection = new AxiomSectionPanel("DART TYPE");
        dartCombo = makeCombo(dartDisplayNames());
        dartSection.addRow("Dart type", dartCombo);
        itemCardPanel.add(dartSection, "dart");

        AxiomSectionPanel shaftSection = new AxiomSectionPanel("ARROW SHAFTS");
        shaftSection.addFull(new JLabel("Uses a knife on normal logs to make arrow shafts."));
        itemCardPanel.add(shaftSection, "shafts");

        AxiomSectionPanel headlessSection = new AxiomSectionPanel("HEADLESS ARROWS");
        headlessSection.addFull(new JLabel("Combines arrow shafts with feathers using the fast no-dialog path."));
        itemCardPanel.add(headlessSection, "headless");

        root.add(itemCardPanel);
        root.add(Box.createRigidArea(new Dimension(0, AxiomTheme.PAD_SM)));

        // Wire method combo → card switch
        methodCombo.addActionListener(e ->
        {
            CardLayout cl = (CardLayout) itemCardPanel.getLayout();
            switch (methodFromIndex(methodCombo.getSelectedIndex()))
            {
                case KNIFE_LOGS:      cl.show(itemCardPanel, "knife");    break;
                case STRING_BOW:      cl.show(itemCardPanel, "string");   break;
                case DARTS:           cl.show(itemCardPanel, "dart");     break;
                case ARROW_SHAFTS:    cl.show(itemCardPanel, "shafts");   break;
                case HEADLESS_ARROWS: cl.show(itemCardPanel, "headless"); break;
            }
        });

        // ── Section: Banking ──────────────────────────────────────────────────
        AxiomSectionPanel bankSection = new AxiomSectionPanel("BANKING");
        bankBox = makeCheckBox("Bank for more materials when inventory is empty", false);
        bankSection.addCheckRow("", bankBox);
        root.add(bankSection);
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
    public FletchingSettings getSettings()
    {
        return new FletchingSettings(
            methodFromIndex(methodCombo.getSelectedIndex()),
            logTypeFromIndex(logCombo.getSelectedIndex()),
            knifeProductFromIndex(knifeProductCombo.getSelectedIndex()),
            bowTypeFromIndex(bowCombo.getSelectedIndex()),
            dartTypeFromIndex(dartCombo.getSelectedIndex()),
            bankBox.isSelected(),
            (Integer) breakIntervalSpinner.getValue(),
            (Integer) breakDurationSpinner.getValue()
        );
    }

    // ── Display name builders ─────────────────────────────────────────────────

    private static String[] methodDisplayNames()
    {
        FletchingMethod[] ms = FletchingMethod.values();
        String[] names = new String[ms.length];
        for (int i = 0; i < ms.length; i++) names[i] = ms[i].displayName;
        return names;
    }

    private static String[] logDisplayNames()
    {
        LogType[] types = LogType.values();
        String[] names = new String[types.length];
        for (int i = 0; i < types.length; i++)
            names[i] = logDisplayName(types[i]);
        return names;
    }

    private static String logDisplayName(LogType t)
    {
        return t.logName + " (Lv. " + t.levelRequired + ")";
    }

    private static String[] bowDisplayNames()
    {
        BowType[] types = BowType.values();
        String[] names = new String[types.length];
        for (int i = 0; i < types.length; i++)
            names[i] = types[i].bowName + " (Lv. " + types[i].levelRequired + ")";
        return names;
    }

    private static String[] knifeProductDisplayNames()
    {
        KnifeProduct[] types = KnifeProduct.values();
        String[] names = new String[types.length];
        for (int i = 0; i < types.length; i++)
            names[i] = types[i].displayName;
        return names;
    }

    private static String[] dartDisplayNames()
    {
        DartType[] types = DartType.values();
        String[] names = new String[types.length];
        for (int i = 0; i < types.length; i++)
            names[i] = types[i].dartName + " (Lv. " + types[i].levelRequired + ")";
        return names;
    }

    // ── Index → enum ─────────────────────────────────────────────────────────

    private static FletchingMethod methodFromIndex(int idx)
    {
        FletchingMethod[] ms = FletchingMethod.values();
        return (idx >= 0 && idx < ms.length) ? ms[idx] : FletchingMethod.KNIFE_LOGS;
    }

    private static LogType logTypeFromIndex(int idx)
    {
        LogType[] types = LogType.values();
        return (idx >= 0 && idx < types.length) ? types[idx] : LogType.OAK;
    }

    private static BowType bowTypeFromIndex(int idx)
    {
        BowType[] types = BowType.values();
        return (idx >= 0 && idx < types.length) ? types[idx] : BowType.OAK_SHORTBOW_U;
    }

    private static KnifeProduct knifeProductFromIndex(int idx)
    {
        KnifeProduct[] types = KnifeProduct.values();
        return (idx >= 0 && idx < types.length) ? types[idx] : KnifeProduct.SHORTBOW;
    }

    private static DartType dartTypeFromIndex(int idx)
    {
        DartType[] types = DartType.values();
        return (idx >= 0 && idx < types.length) ? types[idx] : DartType.BRONZE;
    }
}
