package com.axiom.plugin.ui;

import com.axiom.scripts.smithing.SmithingSettings;
import com.axiom.scripts.smithing.SmithingSettings.BarType;
import com.axiom.scripts.smithing.SmithingSettings.FurnaceProduct;
import com.axiom.scripts.smithing.SmithingSettings.LocationPreset;
import com.axiom.scripts.smithing.SmithingSettings.SmithingMethod;
import com.axiom.scripts.smithing.SmithingSettings.SmithItem;

import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration dialog for the Smithing script.
 *
 * Bar type and smith item are cascading: changing the bar type combo updates
 * the item combo to show only items that require that bar.
 */
public class SmithingConfigDialog extends ScriptConfigDialog<SmithingSettings>
{
    // ── Controls ──────────────────────────────────────────────────────────────
    private JComboBox<String> methodCombo;
    private JComboBox<String> barCombo;
    private JComboBox<String> itemCombo;
    private JComboBox<String> furnaceCombo;
    private JComboBox<String> locationCombo;
    private JCheckBox         bankForBarsBox;
    private JPanel            methodCardPanel;
    private JSpinner          breakIntervalSpinner;
    private JSpinner          breakDurationSpinner;

    public SmithingConfigDialog(JComponent parent)
    {
        super(parent);
    }

    @Override
    public String getScriptName() { return "Axiom Smithing"; }

    @Override
    protected JPanel buildContent()
    {
        JPanel root = new JPanel();
        root.setLayout(new javax.swing.BoxLayout(root, javax.swing.BoxLayout.Y_AXIS));
        root.setBackground(AxiomTheme.BG_PANEL);
        root.setBorder(new javax.swing.border.EmptyBorder(
            AxiomTheme.PAD_MD, AxiomTheme.PAD_LG, AxiomTheme.PAD_MD, AxiomTheme.PAD_LG));

        AxiomSectionPanel methodSection = new AxiomSectionPanel("METHOD");
        methodCombo = makeCombo(methodDisplayNames());
        methodSection.addRow("Method", methodCombo);
        root.add(methodSection);
        root.add(Box.createRigidArea(new Dimension(0, AxiomTheme.PAD_SM)));

        // ── Section: Bar ─────────────────────────────────────────────────────
        AxiomSectionPanel barSection = new AxiomSectionPanel("BAR TYPE");
        barCombo = makeCombo(barDisplayNames());
        barCombo.setSelectedIndex(1); // default: Iron bar (index 1)
        barSection.addRow("Bar", barCombo);

        // ── Section: Item ─────────────────────────────────────────────────────
        AxiomSectionPanel itemSection = new AxiomSectionPanel("ITEM TO SMITH");
        itemCombo = makeCombo(itemDisplayNamesFor(selectedBarType()));
        itemSection.addRow("Item", itemCombo);

        JPanel anvilPanel = new JPanel();
        anvilPanel.setLayout(new javax.swing.BoxLayout(anvilPanel, javax.swing.BoxLayout.Y_AXIS));
        anvilPanel.setBackground(AxiomTheme.BG_PANEL);
        anvilPanel.add(barSection);
        anvilPanel.add(Box.createRigidArea(new Dimension(0, AxiomTheme.PAD_SM)));
        anvilPanel.add(itemSection);

        AxiomSectionPanel furnaceSection = new AxiomSectionPanel("FURNACE PRODUCT");
        furnaceCombo = makeCombo(furnaceDisplayNames());
        furnaceSection.addRow("Product", furnaceCombo);

        methodCardPanel = new JPanel(new CardLayout());
        methodCardPanel.setBackground(AxiomTheme.BG_PANEL);
        methodCardPanel.add(anvilPanel, "anvil");
        methodCardPanel.add(furnaceSection, "furnace");
        root.add(methodCardPanel);
        root.add(Box.createRigidArea(new Dimension(0, AxiomTheme.PAD_SM)));

        // Wire cascading: bar change → refresh item combo
        ActionListener cascadeListener = e -> refreshItemCombo();
        barCombo.addActionListener(cascadeListener);
        methodCombo.addActionListener(e -> refreshMethodCard());

        AxiomSectionPanel locationSection = new AxiomSectionPanel("LOCATION");
        locationCombo = makeCombo(locationDisplayNames());
        locationSection.addRow("Loop preset", locationCombo);
        root.add(locationSection);
        root.add(Box.createRigidArea(new Dimension(0, AxiomTheme.PAD_SM)));

        // ── Section: Banking ──────────────────────────────────────────────────
        AxiomSectionPanel bankSection = new AxiomSectionPanel("BANKING");
        bankForBarsBox = makeCheckBox("Bank when bars run out to get more", true);
        bankSection.addCheckRow("", bankForBarsBox);
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
    public SmithingSettings getSettings()
    {
        BarType   bar  = barTypeFromIndex(barCombo.getSelectedIndex());
        SmithItem item = smithItemFromIndex(bar, itemCombo.getSelectedIndex());
        return new SmithingSettings(
            methodFromIndex(methodCombo.getSelectedIndex()),
            bar, item,
            furnaceProductFromIndex(furnaceCombo.getSelectedIndex()),
            locationPresetFromIndex(locationCombo.getSelectedIndex()),
            bankForBarsBox.isSelected(),
            (Integer) breakIntervalSpinner.getValue(),
            (Integer) breakDurationSpinner.getValue()
        );
    }

    // ── Cascading helpers ─────────────────────────────────────────────────────

    private void refreshItemCombo()
    {
        BarType bar = selectedBarType();
        String[] names = itemDisplayNamesFor(bar);
        itemCombo.removeAllItems();
        for (String name : names) itemCombo.addItem(name);
        if (itemCombo.getItemCount() > 0) itemCombo.setSelectedIndex(0);
    }

    private void refreshMethodCard()
    {
        CardLayout layout = (CardLayout) methodCardPanel.getLayout();
        if (selectedMethod() == SmithingMethod.ANVIL)
        {
            layout.show(methodCardPanel, "anvil");
        }
        else
        {
            layout.show(methodCardPanel, "furnace");
        }
    }

    private BarType selectedBarType()
    {
        return barTypeFromIndex(barCombo == null ? 0 : barCombo.getSelectedIndex());
    }

    private SmithingMethod selectedMethod()
    {
        return methodFromIndex(methodCombo == null ? 0 : methodCombo.getSelectedIndex());
    }

    // ── Display name builders ─────────────────────────────────────────────────

    private static String[] methodDisplayNames()
    {
        SmithingMethod[] methods = SmithingMethod.values();
        String[] names = new String[methods.length];
        for (int i = 0; i < methods.length; i++)
        {
            names[i] = methods[i].displayName;
        }
        return names;
    }

    private static String[] barDisplayNames()
    {
        BarType[] bars = BarType.values();
        String[] names = new String[bars.length];
        for (int i = 0; i < bars.length; i++)
        {
            names[i] = bars[i].barName + " (Lv. " + bars[i].levelRequired + ")";
        }
        return names;
    }

    private static String[] itemDisplayNamesFor(BarType bar)
    {
        List<String> names = new ArrayList<>();
        for (SmithItem item : SmithItem.values())
        {
            if (item.requiredBar == bar)
            {
                names.add(item.displayName + " (" + item.barsPerItem + " bar"
                    + (item.barsPerItem > 1 ? "s" : "") + ")");
            }
        }
        return names.toArray(new String[0]);
    }

    private static String[] furnaceDisplayNames()
    {
        FurnaceProduct[] products = FurnaceProduct.values();
        String[] names = new String[products.length];
        for (int i = 0; i < products.length; i++)
        {
            names[i] = products[i].displayName;
        }
        return names;
    }

    // ── Index → enum ─────────────────────────────────────────────────────────

    private static SmithingMethod methodFromIndex(int idx)
    {
        SmithingMethod[] methods = SmithingMethod.values();
        return (idx >= 0 && idx < methods.length) ? methods[idx] : SmithingMethod.ANVIL;
    }

    private static BarType barTypeFromIndex(int idx)
    {
        BarType[] bars = BarType.values();
        return (idx >= 0 && idx < bars.length) ? bars[idx] : BarType.IRON;
    }

    private static SmithItem smithItemFromIndex(BarType bar, int idx)
    {
        List<SmithItem> items = new ArrayList<>();
        for (SmithItem item : SmithItem.values())
        {
            if (item.requiredBar == bar) items.add(item);
        }
        return (idx >= 0 && idx < items.size()) ? items.get(idx) : items.get(0);
    }

    private static String[] locationDisplayNames()
    {
        LocationPreset[] presets = LocationPreset.values();
        String[] names = new String[presets.length];
        for (int i = 0; i < presets.length; i++)
        {
            names[i] = presets[i].displayName;
        }
        return names;
    }

    private static LocationPreset locationPresetFromIndex(int idx)
    {
        LocationPreset[] presets = LocationPreset.values();
        return (idx >= 0 && idx < presets.length) ? presets[idx] : LocationPreset.CUSTOM_START;
    }

    private static FurnaceProduct furnaceProductFromIndex(int idx)
    {
        FurnaceProduct[] products = FurnaceProduct.values();
        return (idx >= 0 && idx < products.length) ? products[idx] : FurnaceProduct.IRON_BAR;
    }
}
