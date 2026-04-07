package com.botengine.osrs.ui;

import javax.swing.JButton;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Pre-styled JButton following the Axiom design language.
 *
 * Variants:
 *   PRIMARY   — orange accent background; for primary actions (Start, Confirm)
 *   SECONDARY — dark gray background;    for secondary actions (Cancel, Save Profile)
 *   DANGER    — red background;          for destructive actions (Stop, Remove)
 *   NEUTRAL   — medium gray background;  for supplementary actions (Pause, +Add)
 */
public class AxiomButton extends JButton
{
    public enum Variant { PRIMARY, SECONDARY, DANGER, NEUTRAL }

    private final Color bgNormal;
    private final Color bgHover;
    private final Color bgPress;

    public AxiomButton(String text, Variant variant)
    {
        super(text);

        switch (variant)
        {
            case PRIMARY:
                bgNormal = AxiomTheme.BTN_PRIMARY;
                bgHover  = AxiomTheme.ACCENT_DARK.brighter();
                bgPress  = AxiomTheme.ACCENT_DARK;
                break;
            case DANGER:
                bgNormal = AxiomTheme.BTN_DANGER;
                bgHover  = new Color(210, 70, 70);
                bgPress  = new Color(150, 40, 40);
                break;
            case NEUTRAL:
                bgNormal = AxiomTheme.BTN_NEUTRAL;
                bgHover  = new Color(70, 70, 70);
                bgPress  = new Color(45, 45, 45);
                break;
            default: // SECONDARY
                bgNormal = AxiomTheme.BTN_SECONDARY;
                bgHover  = new Color(85, 85, 85);
                bgPress  = new Color(55, 55, 55);
                break;
        }

        setBackground(bgNormal);
        setForeground(AxiomTheme.TEXT_ON_ACCENT);
        setFont(AxiomTheme.fontSmall());
        setFocusPainted(false);
        setBorderPainted(false);
        setOpaque(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(new EmptyBorder(6, 14, 6, 14));

        addMouseListener(new MouseAdapter()
        {
            @Override public void mouseEntered(MouseEvent e)
            { if (isEnabled()) setBackground(bgHover); }

            @Override public void mouseExited(MouseEvent e)
            { setBackground(isEnabled() ? bgNormal : AxiomTheme.SEPARATOR); }

            @Override public void mousePressed(MouseEvent e)
            { if (isEnabled()) setBackground(bgPress); }

            @Override public void mouseReleased(MouseEvent e)
            { if (isEnabled()) setBackground(bgHover); }
        });
    }

    /** Convenience: full-width button for footer rows. */
    public AxiomButton fullWidth(String text, Variant variant)
    {
        AxiomButton btn = new AxiomButton(text, variant);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        return btn;
    }

    @Override
    public void setEnabled(boolean enabled)
    {
        super.setEnabled(enabled);
        setBackground(enabled ? bgNormal : AxiomTheme.SEPARATOR);
        setForeground(enabled ? AxiomTheme.TEXT_ON_ACCENT : AxiomTheme.TEXT_DIM);
    }
}
