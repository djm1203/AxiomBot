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
import java.util.Locale;

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
    private static final int GROUP_SMITHING       = 312;
    private static final int[] CONTINUE_DIALOG_GROUPS = {193, 217, 219, 229, 231};

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
    public boolean clickMakeOption(String text)
    {
        Widget root = client.getWidget(GROUP_MAKE_DIALOG, 0);
        Widget option = findWidgetContainingText(root, text);
        if (option == null)
        {
            log.warn("WidgetsImpl: make option '{}' not found", text);
            return false;
        }

        RobotClick.click(option, client, antiban);
        return true;
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
    public boolean isContinueDialogOpen()
    {
        return findContinueWidget() != null;
    }

    @Override
    public boolean dismissContinueDialog()
    {
        Widget continueWidget = findContinueWidget();
        if (continueWidget == null)
        {
            return false;
        }

        RobotClick.click(continueWidget, client, antiban);
        return true;
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

    @Override
    public boolean clickWidgetContainingText(int groupId, String text)
    {
        Widget root = client.getWidget(groupId, 0);
        Widget match = findWidgetContainingText(root, text);
        if (match == null)
        {
            return false;
        }

        RobotClick.click(match, client, antiban);
        return true;
    }

    @Override
    public boolean isSmithingDialogOpen()
    {
        Widget root = client.getWidget(GROUP_SMITHING, 0);
        return root != null && !root.isHidden();
    }

    @Override
    public void clickSmithingItem(int itemId)
    {
        if (!isSmithingDialogOpen())
        {
            log.warn("WidgetsImpl.clickSmithingItem: smithing dialog (group {}) not open", GROUP_SMITHING);
            return;
        }

        // Scan all fixed children (and their dynamic children) for the target item ID.
        // The smithing interface uses a grid of item slots; exact child indices vary
        // by RS version, so we scan broadly and log diagnostics.
        for (int childIdx = 0; childIdx <= 60; childIdx++)
        {
            Widget w = client.getWidget(GROUP_SMITHING, childIdx);
            if (w == null || w.isHidden()) continue;

            if (w.getItemId() == itemId)
            {
                log.info("WidgetsImpl: clicking smithing item id={} at child={}", itemId, childIdx);
                RobotClick.click(w, client, antiban);
                return;
            }

            Widget[] dynamic = w.getDynamicChildren();
            if (dynamic == null) continue;
            for (int di = 0; di < dynamic.length; di++)
            {
                Widget d = dynamic[di];
                if (d == null || d.isHidden()) continue;
                if (d.getItemId() == itemId)
                {
                    log.info("WidgetsImpl: clicking smithing item id={} at child={} dynIdx={}", itemId, childIdx, di);
                    RobotClick.click(d, client, antiban);
                    return;
                }
            }
        }

        // Item not found — dump visible items to help diagnose widget IDs in-game.
        log.warn("WidgetsImpl: smithing item id={} not found in group {} — dumping visible items:", itemId, GROUP_SMITHING);
        for (int childIdx = 0; childIdx <= 60; childIdx++)
        {
            Widget w = client.getWidget(GROUP_SMITHING, childIdx);
            if (w == null || w.isHidden()) continue;
            if (w.getItemId() > 0)
            {
                log.warn("  child={} itemId={} name={}", childIdx, w.getItemId(), w.getName());
            }
            Widget[] dynamic = w.getDynamicChildren();
            if (dynamic == null) continue;
            for (int di = 0; di < dynamic.length; di++)
            {
                Widget d = dynamic[di];
                if (d != null && !d.isHidden() && d.getItemId() > 0)
                {
                    log.warn("  child={} dynIdx={} itemId={} name={}", childIdx, di, d.getItemId(), d.getName());
                }
            }
        }
    }

    private Widget findContinueWidget()
    {
        for (int groupId : CONTINUE_DIALOG_GROUPS)
        {
            Widget root = client.getWidget(groupId, 0);
            Widget match = findContinueWidget(root);
            if (match != null)
            {
                return match;
            }
        }
        return null;
    }

    private Widget findWidgetContainingText(Widget widget, String text)
    {
        if (widget == null || widget.isHidden() || text == null || text.isBlank())
        {
            return null;
        }

        String normalizedNeedle = normalizeWidgetText(text);
        if (normalizedNeedle.isEmpty())
        {
            return null;
        }

        return findWidgetContainingText(widget, normalizedNeedle, true);
    }

    private Widget findWidgetContainingText(Widget widget, String normalizedNeedle, boolean allowActionsCheck)
    {
        if (widget == null || widget.isHidden())
        {
            return null;
        }

        String widgetText = normalizeWidgetText(widget.getText());
        if (!widgetText.isEmpty() && widgetText.contains(normalizedNeedle))
        {
            return widget;
        }

        if (allowActionsCheck && widget.getActions() != null)
        {
            for (String action : widget.getActions())
            {
                if (normalizeWidgetText(action).contains(normalizedNeedle))
                {
                    return widget;
                }
            }
        }

        Widget[] children = widget.getChildren();
        if (children != null)
        {
            for (Widget child : children)
            {
                Widget match = findWidgetContainingText(child, normalizedNeedle, allowActionsCheck);
                if (match != null)
                {
                    return match;
                }
            }
        }

        Widget[] dynamicChildren = widget.getDynamicChildren();
        if (dynamicChildren != null)
        {
            for (Widget child : dynamicChildren)
            {
                Widget match = findWidgetContainingText(child, normalizedNeedle, allowActionsCheck);
                if (match != null)
                {
                    return match;
                }
            }
        }

        return null;
    }

    private Widget findContinueWidget(Widget widget)
    {
        if (widget == null || widget.isHidden())
        {
            return null;
        }

        if (hasContinueText(widget.getText()))
        {
            return widget;
        }

        Widget[] children = widget.getChildren();
        if (children != null)
        {
            for (Widget child : children)
            {
                Widget match = findContinueWidget(child);
                if (match != null)
                {
                    return match;
                }
            }
        }

        Widget[] dynamicChildren = widget.getDynamicChildren();
        if (dynamicChildren != null)
        {
            for (Widget child : dynamicChildren)
            {
                Widget match = findContinueWidget(child);
                if (match != null)
                {
                    return match;
                }
            }
        }

        return null;
    }

    private static boolean hasContinueText(String text)
    {
        if (text == null || text.isBlank())
        {
            return false;
        }

        String normalized = text.toLowerCase();
        return normalized.contains("click here to continue")
            || normalized.contains("press space to continue");
    }

    private static String normalizeWidgetText(String text)
    {
        if (text == null || text.isBlank())
        {
            return "";
        }

        return text
            .replaceAll("<[^>]*>", "")
            .toLowerCase(Locale.ROOT)
            .trim();
    }

}
