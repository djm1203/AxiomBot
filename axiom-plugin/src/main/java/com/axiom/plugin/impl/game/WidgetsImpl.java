package com.axiom.plugin.impl.game;

import com.axiom.api.game.Widgets;
import com.axiom.api.util.Antiban;
import com.axiom.plugin.util.RobotClick;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.widgets.Widget;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Implements common widget interactions — make-all dialogs, level-up dialogs.
 *
 * Widget IDs verified against the RuneLite widget inspector for OSRS 2024:
 *   Skill advance dialog (level-up): group 233
 *   Make/production dialog:          group 270 (NPC dialog + production)
 *   Make-all button child:           270,14
 */
@Slf4j
@Singleton
public class WidgetsImpl implements Widgets
{
    // Widget group IDs
    private static final int GROUP_MAKE_DIALOG    = 270;
    private static final int GROUP_LEVEL_UP       = 233;

    // Make dialog children
    private static final int CHILD_MAKE_ALL       = 14;  // "Make All" button
    private static final int CHILD_MAKE_QUANTITY  = 16;  // "Make X" button

    // Level-up dialog close
    private static final int CHILD_LEVEL_UP_CONTINUE = 4;

    private final Client  client;
    private final Antiban antiban;

    @Inject
    public WidgetsImpl(Client client, Antiban antiban)
    {
        this.client  = client;
        this.antiban = antiban;
    }

    @Override
    public boolean isMakeDialogOpen()
    {
        Widget root = client.getWidget(GROUP_MAKE_DIALOG, 0);
        return root != null && !root.isHidden();
    }

    @Override
    public void clickMakeAll()
    {
        Widget btn = client.getWidget(GROUP_MAKE_DIALOG, CHILD_MAKE_ALL);
        if (btn == null || btn.isHidden())
        {
            log.warn("WidgetsImpl: make-all button not found");
            return;
        }
        RobotClick.click(btn, client, antiban);
    }

    @Override
    public void clickMakeX(int quantity)
    {
        // OSRS fires a dialog after clicking Make-X; this sends the quantity directly
        // via the production interface's CC_OP action (op 9 = make-X).
        client.menuAction(
            quantity, client.getWidget(GROUP_MAKE_DIALOG, CHILD_MAKE_QUANTITY) != null
                ? client.getWidget(GROUP_MAKE_DIALOG, CHILD_MAKE_QUANTITY).getId() : 0,
            MenuAction.CC_OP,
            9, -1,
            "Make-X", ""
        );
    }

    @Override
    public boolean isDialogOpen(int groupId)
    {
        Widget root = client.getWidget(groupId, 0);
        return root != null && !root.isHidden();
    }

    @Override
    public void clickDialogOption(String text)
    {
        // Search common dialog groups (219 = NPC dialog, 228 = dialogue options)
        for (int groupId : new int[]{219, 228, 229, 230, 231})
        {
            Widget parent = client.getWidget(groupId, 0);
            if (parent == null || parent.isHidden()) continue;

            Widget[] children = parent.getDynamicChildren();
            if (children == null) continue;

            for (Widget child : children)
            {
                if (child != null && text.equalsIgnoreCase(child.getText()))
                {
                    RobotClick.click(child, client, antiban);
                    return;
                }
            }
        }
        log.warn("WidgetsImpl: dialog option '{}' not found", text);
    }

    @Override
    public boolean isLevelUpDialogOpen()
    {
        Widget root = client.getWidget(GROUP_LEVEL_UP, 0);
        return root != null && !root.isHidden();
    }

    @Override
    public void dismissLevelUpDialog()
    {
        Widget btn = client.getWidget(GROUP_LEVEL_UP, CHILD_LEVEL_UP_CONTINUE);
        if (btn != null && !btn.isHidden())
        {
            RobotClick.click(btn, client, antiban);
        }
    }

    @Override
    public boolean isWidgetVisible(int groupId, int childId)
    {
        Widget w = client.getWidget(groupId, childId);
        return w != null && !w.isHidden();
    }

    @Override
    public void clickWidget(int groupId, int childId)
    {
        Widget w = client.getWidget(groupId, childId);
        if (w == null || w.isHidden())
        {
            log.warn("WidgetsImpl.clickWidget: widget ({},{}) null or hidden", groupId, childId);
            return;
        }
        RobotClick.click(w, client, antiban);
    }

}
