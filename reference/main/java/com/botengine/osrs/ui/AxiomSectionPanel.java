package com.botengine.osrs.ui;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * A collapsible section panel used inside script config dialogs.
 *
 * Visual anatomy:
 *
 *   ┌─ SECTION TITLE ──────────────────── [▲] ─┐
 *   │  content goes here                        │
 *   │  (hidden when collapsed)                  │
 *   └───────────────────────────────────────────┘
 *
 * The header is styled with AxiomTheme.ACCENT text.
 * Content panel uses BoxLayout.Y_AXIS for vertical stacking of form rows.
 *
 * Usage:
 *   AxiomSectionPanel section = new AxiomSectionPanel("TARGETING");
 *   section.addRow(labeledRow("Target NPC", npcTextField));
 *   dialogPanel.add(section);
 */
public class AxiomSectionPanel extends JPanel
{
    private final JPanel contentPanel;
    private final JLabel toggleLabel;
    private boolean expanded = true;

    public AxiomSectionPanel(String title)
    {
        setLayout(new BorderLayout(0, 0));
        setBackground(AxiomTheme.BG_PANEL);
        setBorder(new CompoundBorder(
            new MatteBorder(0, 3, 0, 0, AxiomTheme.ACCENT),
            new EmptyBorder(0, 0, 6, 0)
        ));

        // ── Header ────────────────────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(AxiomTheme.BG_DEEP);
        header.setBorder(new EmptyBorder(5, 8, 5, 8));
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(AxiomTheme.fontSmall());
        titleLabel.setForeground(AxiomTheme.ACCENT);

        toggleLabel = new JLabel("▲");
        toggleLabel.setFont(AxiomTheme.fontSmall());
        toggleLabel.setForeground(AxiomTheme.TEXT_DIM);

        header.add(titleLabel, BorderLayout.WEST);
        header.add(toggleLabel, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // ── Content ───────────────────────────────────────────────────────────
        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(AxiomTheme.BG_PANEL);
        contentPanel.setBorder(new EmptyBorder(6, 8, 4, 8));
        add(contentPanel, BorderLayout.CENTER);

        // ── Toggle on header click ────────────────────────────────────────────
        header.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                expanded = !expanded;
                contentPanel.setVisible(expanded);
                toggleLabel.setText(expanded ? "▲" : "▼");
                revalidate();
            }
        });
    }

    /**
     * Adds a component row to this section's content panel.
     * Use {@link AxiomSectionPanel#row(String, javax.swing.JComponent)} to build
     * a standard label + control row.
     */
    public void addRow(JPanel row)
    {
        contentPanel.add(row);
    }

    /**
     * Builds and adds a label + single control row.
     *
     * @param labelText the left-side label
     * @param control   the right-side input component
     */
    public void addRow(String labelText, java.awt.Component control)
    {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBackground(AxiomTheme.BG_PANEL);
        row.setBorder(new EmptyBorder(2, 0, 2, 0));
        row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 28));

        JLabel label = new JLabel(labelText);
        label.setFont(AxiomTheme.fontSmall());
        label.setForeground(AxiomTheme.TEXT_DIM);
        label.setPreferredSize(new java.awt.Dimension(115, 22));

        row.add(label, BorderLayout.WEST);
        row.add(control, BorderLayout.CENTER);
        contentPanel.add(row);
    }

    /**
     * Adds a full-width component spanning both columns (e.g. a table panel).
     */
    public void addFull(java.awt.Component comp)
    {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(AxiomTheme.BG_PANEL);
        wrapper.setBorder(new EmptyBorder(2, 0, 2, 0));
        wrapper.add(comp, BorderLayout.CENTER);
        contentPanel.add(wrapper);
    }

    /**
     * Adds a row with a checkbox and its inline label (for boolean toggles).
     */
    public void addCheckRow(String labelText, javax.swing.JCheckBox checkbox)
    {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        row.setBackground(AxiomTheme.BG_PANEL);
        row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 28));

        checkbox.setBackground(AxiomTheme.BG_PANEL);
        checkbox.setForeground(AxiomTheme.TEXT);
        checkbox.setFont(AxiomTheme.fontSmall());
        checkbox.setText(labelText);

        row.add(checkbox);
        contentPanel.add(row);
    }

    /** Adds a thin horizontal spacer between rows. */
    public void addSpacer()
    {
        JPanel spacer = new JPanel();
        spacer.setBackground(AxiomTheme.BG_PANEL);
        spacer.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 4));
        contentPanel.add(spacer);
    }
}
