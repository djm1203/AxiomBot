package com.botengine.osrs.api;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;

import javax.inject.Inject;

/**
 * API for Grand Exchange interactions — open, buy, collect.
 *
 * Widget map (OSRS standard, RuneLite 1.12.x):
 *   Interface 465 = Grand Exchange main panel
 *     Child 0      = root container (isOpen check)
 *     Children 7–14 = 8 offer slot containers
 *     Each slot child 18 = item sprite (itemId == -1 if empty)
 *     Each slot child 22 = "Collect" button (present on completed offers)
 *   Interface 162 = Item search box (opens after clicking "Buy" on empty slot)
 *     Child 33 = search input widget
 *     Child 34 = search results list
 *   Interface 426 = Offer setup (quantity / price confirmation)
 *     Child 6–10   = quantity preset buttons (1, 10, 100, 1000, All)
 *     Child 24     = price modifier widget (+5%, -5%)
 *     Child 33     = Confirm offer button
 *
 * NOTE: Exact widget child IDs may need in-game verification for your specific
 * RuneLite build. The IDs above reflect the standard OSRS interface layout.
 */
@Slf4j
public class GrandExchange
{
    // ── Widget constants ──────────────────────────────────────────────────────

    private static final int GE_INTERFACE     = 465;
    private static final int GE_SEARCH_IFACE  = 162;
    private static final int GE_SETUP_IFACE   = 426;

    /** Child index of the first offer slot within interface 465. Slots are 7–14. */
    private static final int GE_SLOT_BASE_CHILD = 7;
    /** Number of offer slots in the GE (8 slots total). */
    private static final int GE_SLOT_COUNT = 8;
    /** Child within each slot container that holds the item sprite. */
    private static final int GE_SLOT_ITEM_CHILD = 18;
    /** Child within each slot container for the Buy/Sell button. */
    private static final int GE_BUY_BUTTON_CHILD = 23;

    /** Packed widget ID for the search text box (interface 162, child 33). */
    private static final int GE_SEARCH_BOX = WidgetUtil.packComponentId(GE_SEARCH_IFACE, 33);
    /** Packed widget ID for the search results container (interface 162, child 34). */
    private static final int GE_RESULTS_CONTAINER = WidgetUtil.packComponentId(GE_SEARCH_IFACE, 34);

    /** Quantity preset button packed IDs within the offer-setup interface (426). */
    private static final int GE_QTY_1    = WidgetUtil.packComponentId(GE_SETUP_IFACE, 6);
    private static final int GE_QTY_10   = WidgetUtil.packComponentId(GE_SETUP_IFACE, 7);
    private static final int GE_QTY_100  = WidgetUtil.packComponentId(GE_SETUP_IFACE, 8);
    private static final int GE_QTY_1000 = WidgetUtil.packComponentId(GE_SETUP_IFACE, 9);
    private static final int GE_QTY_ALL  = WidgetUtil.packComponentId(GE_SETUP_IFACE, 10);

    /**
     * Price modifier widget (op=1 for -5%, op=3 for +5%, op=5 for +10%).
     * Clicking +10% three times sets price well above guide price to ensure the offer fills.
     */
    private static final int GE_PRICE_MOD = WidgetUtil.packComponentId(GE_SETUP_IFACE, 24);

    /** Confirm offer button in setup interface. */
    private static final int GE_CONFIRM   = WidgetUtil.packComponentId(GE_SETUP_IFACE, 33);

    private static final String GE_CLERK_NAME = "Grand Exchange Clerk";

    // ── Dependencies ──────────────────────────────────────────────────────────

    private final Client client;
    private final Interaction interaction;
    private final Npcs npcs;

    @Inject
    public GrandExchange(Client client, Interaction interaction, Npcs npcs)
    {
        this.client = client;
        this.interaction = interaction;
        this.npcs = npcs;
    }

    // ── State queries ─────────────────────────────────────────────────────────

    /** Returns true if the Grand Exchange main interface is open. */
    public boolean isOpen()
    {
        Widget root = client.getWidget(GE_INTERFACE, 0);
        return root != null && !root.isHidden();
    }

    /** Returns true if the item search box is currently open. */
    public boolean isSearchOpen()
    {
        Widget root = client.getWidget(GE_SEARCH_IFACE, 0);
        return root != null && !root.isHidden();
    }

    /** Returns true if the offer-setup panel (quantity/price) is currently open. */
    public boolean isOfferSetupOpen()
    {
        Widget root = client.getWidget(GE_SETUP_IFACE, 0);
        return root != null && !root.isHidden();
    }

    // ── Opening ───────────────────────────────────────────────────────────────

    /**
     * Attempts to open the Grand Exchange by clicking the nearest GE clerk NPC.
     *
     * @return true if a clerk was found and clicked, false if none nearby
     */
    public boolean openNearest()
    {
        NPC clerk = npcs.nearest(GE_CLERK_NAME);
        if (clerk == null)
        {
            log.warn("openNearest: no Grand Exchange Clerk found nearby");
            return false;
        }
        interaction.click(clerk, "Exchange");
        log.debug("Clicked GE clerk to open exchange");
        return true;
    }

    // ── Offer slot management ─────────────────────────────────────────────────

    /**
     * Returns the 0-based index of the first empty offer slot, or -1 if all are occupied.
     * A slot is considered empty when its item sprite child has item ID -1.
     */
    public int findEmptySlot()
    {
        for (int i = 0; i < GE_SLOT_COUNT; i++)
        {
            int child = GE_SLOT_BASE_CHILD + i;
            Widget slotContainer = client.getWidget(GE_INTERFACE, child);
            if (slotContainer == null) continue;

            Widget[] dynamics = slotContainer.getDynamicChildren();
            if (dynamics == null)
            {
                // No dynamic children → slot is empty
                return i;
            }

            // Check item sprite child
            if (GE_SLOT_ITEM_CHILD < dynamics.length)
            {
                Widget itemSprite = dynamics[GE_SLOT_ITEM_CHILD];
                if (itemSprite != null && itemSprite.getItemId() <= 0) return i;
            }
            else
            {
                return i; // slot has fewer children than expected → treat as empty
            }
        }
        return -1;
    }

    /**
     * Clicks a GE offer slot by 0-based index (0–7) to select it.
     * After clicking, the Buy/Sell buttons appear on the right side of the GE panel.
     */
    public void clickSlot(int slotIndex)
    {
        int child = GE_SLOT_BASE_CHILD + slotIndex;
        interaction.clickWidget(WidgetUtil.packComponentId(GE_INTERFACE, child));
        log.debug("Clicked GE slot {}", slotIndex);
    }

    // ── Buy flow ──────────────────────────────────────────────────────────────

    /**
     * Clicks the "Buy" button that appears after selecting an empty slot.
     * Opens the item search interface.
     */
    public void clickBuy()
    {
        int buyWidget = WidgetUtil.packComponentId(GE_INTERFACE, GE_BUY_BUTTON_CHILD);
        interaction.clickWidget(buyWidget);
        log.debug("Clicked GE Buy button");
    }

    /**
     * Clicks the search box in the item search interface.
     * After this, keyboard input populates the search field — the caller should
     * send key events or use client.setVar to inject the search term, then wait
     * for results to appear before calling {@link #clickFirstResult()}.
     */
    public void clickSearchBox()
    {
        interaction.clickWidget(GE_SEARCH_BOX);
        log.debug("Clicked GE search box");
    }

    /**
     * Clicks the first result in the item search result list.
     * Call this after the search results have populated (one tick after searching).
     */
    public void clickFirstResult()
    {
        Widget results = client.getWidget(GE_SEARCH_IFACE, 34);
        if (results == null)
        {
            log.warn("clickFirstResult: search results widget not found");
            return;
        }
        Widget[] children = results.getDynamicChildren();
        if (children == null || children.length == 0)
        {
            log.warn("clickFirstResult: no search results");
            return;
        }
        Widget first = children[0];
        if (first == null) return;

        client.menuAction(
            0, results.getId(),
            MenuAction.CC_OP,
            1, first.getItemId(),
            "Buy", ""
        );
        log.debug("Clicked first GE search result itemId={}", first.getItemId());
    }

    /**
     * Sets the offer quantity using the preset buttons.
     * Pass Integer.MAX_VALUE to click the "All" button.
     * Valid rounded values: 1, 10, 100, 1000. Other values click the nearest preset below.
     */
    public void setQuantity(int quantity)
    {
        int widget;
        if      (quantity >= Integer.MAX_VALUE) widget = GE_QTY_ALL;
        else if (quantity >= 1000)              widget = GE_QTY_1000;
        else if (quantity >= 100)               widget = GE_QTY_100;
        else if (quantity >= 10)                widget = GE_QTY_10;
        else                                    widget = GE_QTY_1;

        interaction.clickWidget(widget);
        log.debug("Set GE quantity preset for qty={}", quantity);
    }

    /**
     * Bumps the offer price up by 10% (clicks the +5% button twice) so the
     * buy offer is virtually guaranteed to fill at guide price or below.
     */
    public void setPriceAboveGuide()
    {
        // op=3 on the price modifier widget = "+5%" per click — click twice for +10%
        for (int i = 0; i < 2; i++)
        {
            client.menuAction(-1, GE_PRICE_MOD, MenuAction.CC_OP, 3, -1, "", "");
        }
        log.debug("Set GE price to guide+10%");
    }

    /**
     * Confirms the current offer in the setup interface.
     * Call this as the final step of the buy flow.
     */
    public void confirmOffer()
    {
        interaction.clickWidget(GE_CONFIRM);
        log.debug("Confirmed GE offer");
    }

    // ── Collecting ────────────────────────────────────────────────────────────

    /**
     * Iterates all offer slots and clicks the collect button on any completed ones.
     * A slot has items to collect when its inner collect button widget is visible.
     * Collects to inventory (op=1) rather than bank note (op=2).
     */
    public void collectAll()
    {
        for (int i = 0; i < GE_SLOT_COUNT; i++)
        {
            int slotChild = GE_SLOT_BASE_CHILD + i;
            Widget slotContainer = client.getWidget(GE_INTERFACE, slotChild);
            if (slotContainer == null) continue;

            Widget[] dynamics = slotContainer.getDynamicChildren();
            if (dynamics == null) continue;

            // Child 22 within each slot is the "Collect" button
            if (22 < dynamics.length)
            {
                Widget collectBtn = dynamics[22];
                if (collectBtn != null && !collectBtn.isHidden())
                {
                    client.menuAction(
                        -1, slotContainer.getId(),
                        MenuAction.CC_OP,
                        1, -1,
                        "Collect", ""
                    );
                    log.debug("Collected items from GE slot {}", i);
                }
            }
        }
    }

    /**
     * Returns true if any offer slot has completed items waiting to be collected.
     */
    public boolean hasItemsToCollect()
    {
        for (int i = 0; i < GE_SLOT_COUNT; i++)
        {
            Widget slotContainer = client.getWidget(GE_INTERFACE, GE_SLOT_BASE_CHILD + i);
            if (slotContainer == null) continue;
            Widget[] dynamics = slotContainer.getDynamicChildren();
            if (dynamics == null) continue;
            if (22 < dynamics.length)
            {
                Widget collectBtn = dynamics[22];
                if (collectBtn != null && !collectBtn.isHidden()) return true;
            }
        }
        return false;
    }

    /**
     * Returns true if there is an active (unfilled) buy offer in any slot.
     * Used to avoid creating duplicate offers for the same item.
     */
    public boolean hasActiveOffer()
    {
        for (int i = 0; i < GE_SLOT_COUNT; i++)
        {
            Widget slotContainer = client.getWidget(GE_INTERFACE, GE_SLOT_BASE_CHILD + i);
            if (slotContainer == null) continue;
            Widget[] dynamics = slotContainer.getDynamicChildren();
            if (dynamics == null) continue;
            if (GE_SLOT_ITEM_CHILD < dynamics.length)
            {
                Widget itemSprite = dynamics[GE_SLOT_ITEM_CHILD];
                if (itemSprite != null && itemSprite.getItemId() > 0) return true;
            }
        }
        return false;
    }
}
