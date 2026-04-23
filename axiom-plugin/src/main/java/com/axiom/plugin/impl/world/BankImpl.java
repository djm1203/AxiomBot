package com.axiom.plugin.impl.world;

import com.axiom.api.game.SceneObject;
import com.axiom.api.util.Pathfinder;
import com.axiom.api.util.Antiban;
import com.axiom.api.world.Bank;
import com.axiom.api.world.LocationProfile;
import com.axiom.api.world.WorldTile;
import com.axiom.plugin.impl.PathfinderImpl;
import com.axiom.plugin.impl.game.GameObjectsImpl;
import com.axiom.plugin.impl.game.NpcsImpl;
import com.axiom.plugin.impl.game.PlayersImpl;
import com.axiom.plugin.impl.world.CameraImpl;
import com.axiom.plugin.util.RobotClick;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;

import javax.inject.Inject;
import javax.inject.Singleton;

@Slf4j
@Singleton
public class BankImpl implements Bank
{
    private static final int BANK_GROUP_ID  = 12;
    private static final int CLOSE_CHILD_ID = 2;
    private static final int ITEMS_CHILD_ID = 13;

    // Minimum ms between path-click attempts while still walking toward the bank.
    // Prevents spam-firing interact() every 3 ticks, which resets the walk queue.
    private static final long WALK_CLICK_COOLDOWN_MS = 6_000L;

    private final Client          client;
    private final GameObjectsImpl gameObjects;
    private final NpcsImpl        npcs;
    private final Antiban         antiban;
    private final PlayersImpl     players;
    private final CameraImpl      camera;
    private final PathfinderImpl  pathfinder;

    /** Wall-clock timestamp of the last path-click sent while the player was en route. */
    private long lastWalkClickMs = 0L;

    @Inject
    public BankImpl(Client client, GameObjectsImpl gameObjects, NpcsImpl npcs,
                    Antiban antiban, PlayersImpl players, CameraImpl camera,
                    PathfinderImpl pathfinder)
    {
        this.client      = client;
        this.gameObjects = gameObjects;
        this.npcs        = npcs;
        this.antiban     = antiban;
        this.players     = players;
        this.camera      = camera;
        this.pathfinder  = pathfinder;
    }

    @Override
    public boolean isOpen()
    {
        Widget root = client.getWidget(BANK_GROUP_ID, 0);
        return root != null && !root.isHidden();
    }

    @Override
    public boolean isNearBank()
    {
        // 25-tile Chebyshev radius — covers typical resource-to-bank distances
        // (Draynor fishing spots are ~16 tiles from the bank; mining, cooking, etc.
        // can be similarly spaced). The old threshold of 10 was too tight.
        SceneObject obj = findNearestBankObject();
        if (obj != null)
        {
            int dx = Math.abs(obj.getWorldX() - client.getLocalPlayer().getWorldLocation().getX());
            int dy = Math.abs(obj.getWorldY() - client.getLocalPlayer().getWorldLocation().getY());
            if (Math.max(dx, dy) <= 25) return true;
        }
        SceneObject banker = npcs.nearestByName("Banker");
        if (banker != null)
        {
            int dx = Math.abs(banker.getWorldX() - client.getLocalPlayer().getWorldLocation().getX());
            int dy = Math.abs(banker.getWorldY() - client.getLocalPlayer().getWorldLocation().getY());
            return Math.max(dx, dy) <= 25;
        }
        return false;
    }

    @Override
    public boolean openNearest()
    {
        return openNearest(null);
    }

    @Override
    public boolean openNearest(LocationProfile locationProfile)
    {
        // Prefer a bank booth/chest; fall back to a Banker NPC.
        SceneObject target = findNearestBankTarget(locationProfile);
        if (target == null)
        {
            if (locationProfile != null && locationProfile.hasBankAnchor())
            {
                WorldTile bankAnchor = locationProfile.getBankAnchor();
                if (players.distanceTo(bankAnchor.getWorldX(), bankAnchor.getWorldY()) > 2)
                {
                    pathfinder.walkTo(bankAnchor.getWorldX(), bankAnchor.getWorldY(), bankAnchor.getPlane());
                }
            }
            return false;
        }

        if (locationProfile != null && locationProfile.hasBankAnchor())
        {
            WorldTile bankAnchor = locationProfile.getBankAnchor();
            if (bankAnchor != null
                && bankAnchor.getPlane() == client.getPlane()
                && players.distanceTo(bankAnchor.getWorldX(), bankAnchor.getWorldY()) > 4
                && players.distanceTo(target.getWorldX(), target.getWorldY()) > 3)
            {
                log.info("[BANK] Approaching bank anchor at ({},{},{}) before banking",
                    bankAnchor.getWorldX(), bankAnchor.getWorldY(), bankAnchor.getPlane());
                pathfinder.walkTo(bankAnchor.getWorldX(), bankAnchor.getWorldY(), bankAnchor.getPlane());
                return false;
            }
        }

        int distance = players.distanceTo(target.getWorldX(), target.getWorldY());

        if (distance > 3 || players.isMoving())
        {
            Pathfinder.ProgressState progressState =
                pathfinder.getProgressTo(target.getWorldX(), target.getWorldY(), target.getPlane());

            // Send a bounded pathing step toward the bank rather than repeatedly
            // clicking the booth itself while still walking. This keeps the walk
            // queue more stable and lets PathfinderImpl handle blocked target tiles.
            long now = System.currentTimeMillis();
            if (progressState == Pathfinder.ProgressState.STUCK || now - lastWalkClickMs >= WALK_CLICK_COOLDOWN_MS)
            {
                log.info("[BANK] Walking to bank (distance={} progress={}) — sending pathfinder step",
                    distance, progressState);
                pathfinder.walkTo(target.getWorldX(), target.getWorldY(), client.getPlane());
                lastWalkClickMs = now;
            }
            return false;
        }

        // Within 3 tiles and stationary — rotate camera toward the booth so it is
        // inside the visible viewport, then click to open.
        log.info("[BANK] In range (distance={}) — rotating camera toward bank booth", distance);
        camera.rotateTo(target.getWorldX(), target.getWorldY());
        try { Thread.sleep(300); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }

        log.info("[BANK] Camera aligned — clicking to open bank");
        target.interact(resolveBankAction(target, locationProfile));
        lastWalkClickMs = 0L;
        return true;
    }

    @Override
    public void depositAll()
    {
        Widget depositBtn = client.getWidget(WidgetInfo.BANK_DEPOSIT_INVENTORY);
        if (depositBtn == null || depositBtn.isHidden())
        {
            log.warn("[BANKING] deposit widget null/hidden — bank not fully open");
            return;
        }
        log.info("[BANKING] clicking deposit inventory button (bounds={})", depositBtn.getBounds());
        RobotClick.click(depositBtn, client, antiban);
    }

    @Override
    public boolean depositAllExcept(int... protectedIds)
    {
        Widget container = client.getWidget(WidgetInfo.BANK_INVENTORY_ITEMS_CONTAINER);
        if (container == null)
        {
            log.warn("[BANK] depositAllExcept: inventory widget null");
            return false;
        }

        Widget[] slots = container.getDynamicChildren();
        if (slots == null) return false;

        for (int i = 0; i < slots.length; i++)
        {
            Widget w = slots[i];
            if (w == null || w.getItemId() == -1) continue;
            if (isProtected(w.getItemId(), protectedIds)) continue;

            // Deposit-All for this item via its slot in the bank inventory panel.
            // op=4 = Deposit-All in the BANK_INVENTORY_ITEMS_CONTAINER context.
            client.menuAction(
                i, container.getId(),
                MenuAction.CC_OP,
                4, w.getItemId(),
                "Deposit-All", ""
            );
            log.info("[BANK] depositAllExcept: slot={} itemId={}", i, w.getItemId());
            return true; // one action per tick — caller retries next tick
        }
        return false; // inventory contains only protected items
    }

    @Override
    public void deposit(int itemId)
    {
        client.menuAction(
            -1, WidgetInfo.BANK_INVENTORY_ITEMS_CONTAINER.getId(),
            MenuAction.CC_OP,
            itemId, -1,
            "Deposit-All", ""
        );
    }

    @Override
    public void deposit(int itemId, int quantity)
    {
        int op = quantityToOp(quantity);
        client.menuAction(
            -1, WidgetInfo.BANK_INVENTORY_ITEMS_CONTAINER.getId(),
            MenuAction.CC_OP,
            op, itemId,
            "", ""
        );
    }

    @Override
    public void withdraw(int itemId, int quantity)
    {
        Widget container = client.getWidget(BANK_GROUP_ID, ITEMS_CHILD_ID);
        if (container == null) { log.warn("BankImpl: bank items container not found"); return; }

        Widget[] children = container.getDynamicChildren();
        if (children == null) return;

        for (int slot = 0; slot < children.length; slot++)
        {
            Widget child = children[slot];
            if (child == null || child.getItemId() != itemId) continue;

            int op = quantityToOp(quantity);
            client.menuAction(
                slot, container.getId(),
                MenuAction.CC_OP,
                op, itemId,
                "", ""
            );
            return;
        }
        log.warn("BankImpl: item id={} not found in bank", itemId);
    }

    @Override
    public void withdrawAll(int itemId)
    {
        withdraw(itemId, Integer.MAX_VALUE);
    }

    @Override
    public boolean contains(int itemId)
    {
        Widget container = client.getWidget(BANK_GROUP_ID, ITEMS_CHILD_ID);
        if (container == null) return false;
        Widget[] children = container.getDynamicChildren();
        if (children == null) return false;
        for (Widget child : children)
        {
            if (child != null && child.getItemId() == itemId) return true;
        }
        return false;
    }

    @Override
    public void close()
    {
        Widget closeBtn = client.getWidget(BANK_GROUP_ID, CLOSE_CHILD_ID);
        if (closeBtn != null && !closeBtn.isHidden()) RobotClick.click(closeBtn, client, antiban);
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private SceneObject findNearestBankObject()
    {
        // hasAction() uses the impostor-corrected actions cached in SceneObjectWrapper
        return gameObjects.nearest(o -> o.hasAction("Bank"));
    }

    private SceneObject findNearestBankTarget(LocationProfile locationProfile)
    {
        if (locationProfile != null)
        {
            SceneObject preferredObject = findPreferredBankObject(locationProfile);
            if (preferredObject != null)
            {
                return preferredObject;
            }

            SceneObject preferredNpc = findPreferredBankNpc(locationProfile);
            if (preferredNpc != null)
            {
                return preferredNpc;
            }
        }

        SceneObject target = findNearestBankObject();
        if (target != null)
        {
            return target;
        }

        return npcs.nearestByName("Banker");
    }

    private SceneObject findPreferredBankObject(LocationProfile locationProfile)
    {
        String[] preferredNames = locationProfile.getBankObjectNames();
        String[] preferredActions = locationProfile.getBankActions();
        if (preferredNames.length == 0 && preferredActions.length == 0)
        {
            return null;
        }

        return gameObjects.nearest(o ->
            matchesAnyName(o.getName(), preferredNames)
                && matchesAnyAction(o, preferredActions));
    }

    private SceneObject findPreferredBankNpc(LocationProfile locationProfile)
    {
        String[] preferredNames = locationProfile.getBankNpcNames();
        if (preferredNames.length == 0)
        {
            return null;
        }

        return npcs.nearest(o -> matchesAnyName(o.getName(), preferredNames));
    }

    private String resolveBankAction(SceneObject target, LocationProfile locationProfile)
    {
        if (locationProfile != null)
        {
            for (String action : locationProfile.getBankActions())
            {
                if (action != null && !action.isBlank() && target.hasAction(action))
                {
                    return action;
                }
            }
        }
        String fallbackAction = target.hasAction("Bank") ? "Bank" : firstAvailableAction(target);
        return locationProfile != null
            ? locationProfile.resolveInteractionAction("bank", fallbackAction)
            : fallbackAction;
    }

    private static String firstAvailableAction(SceneObject target)
    {
        for (String action : target.getActions())
        {
            if (action != null && !action.isBlank())
            {
                return action;
            }
        }
        return "Bank";
    }

    private static boolean matchesAnyName(String name, String[] preferredNames)
    {
        if (preferredNames == null || preferredNames.length == 0)
        {
            return true;
        }

        for (String preferredName : preferredNames)
        {
            if (preferredName != null && !preferredName.isBlank()
                && name != null && name.equalsIgnoreCase(preferredName))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAnyAction(SceneObject target, String[] preferredActions)
    {
        if (preferredActions == null || preferredActions.length == 0)
        {
            return target.hasAction("Bank");
        }

        for (String action : preferredActions)
        {
            if (action != null && !action.isBlank() && target.hasAction(action))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean isProtected(int itemId, int[] protectedIds)
    {
        for (int id : protectedIds) if (id == itemId) return true;
        return false;
    }

    private static int quantityToOp(int quantity)
    {
        if (quantity == 1)  return 1;
        if (quantity == 5)  return 2;
        if (quantity == 10) return 3;
        return 7; // Withdraw-All
    }
}
