package com.axiom.scripts.smithing;

import com.axiom.api.game.GameObjects;
import com.axiom.api.game.Players;
import com.axiom.api.game.SceneObject;
import com.axiom.api.game.Widgets;
import com.axiom.api.player.Equipment;
import com.axiom.api.player.Inventory;
import com.axiom.api.script.BotScript;
import com.axiom.api.script.ScriptCategory;
import com.axiom.api.script.ScriptManifest;
import com.axiom.api.script.ScriptSettings;
import com.axiom.api.util.Antiban;
import com.axiom.api.util.Log;
import com.axiom.api.util.Pathfinder;
import com.axiom.api.util.ProductionTickTracker;
import com.axiom.api.world.Bank;
import com.axiom.api.world.LocationProfile;
import com.axiom.api.world.WorldTile;
import com.axiom.scripts.smithing.SmithingSettings.FurnaceProduct;
import com.axiom.scripts.smithing.SmithingSettings.SmithingMethod;

@ScriptManifest(
    name        = "Axiom Smithing",
    version     = "1.1",
    category    = ScriptCategory.SKILLING,
    author      = "Axiom",
    description = "Smiths items at an anvil or smelts at a furnace, including cannonballs."
)
public class SmithingScript extends BotScript
{
    private GameObjects gameObjects;
    private Players     players;
    private Inventory   inventory;
    private Equipment   equipment;
    private Widgets     widgets;
    private Bank        bank;
    private Pathfinder  pathfinder;
    private Antiban     antiban;
    private Log         log;

    private SmithingSettings settings;

    private boolean startTileRecorded = false;
    private LocationProfile locationProfile;

    private enum State { FIND_STATION, HANDLE_DIALOG, WAITING, BLAST_COLLECT, BANKING }
    private State state = State.FIND_STATION;

    private static final int DIALOG_TIMEOUT_TICKS = 10;
    private static final int ANIMATION_TIMEOUT_TICKS = 10;
    private static final int MAX_BANK_OPEN_ATTEMPTS = 20;
    private static final int[] ANVIL_IDS = { 2097, 2098 };
    private static final int ICE_GLOVES_ID = 1580;

    private final ProductionTickTracker productionTracker = new ProductionTickTracker();

    private boolean bankJustOpened = false;
    private boolean bankDepositDone = false;
    private int bankWithdrawStep = 0;
    private int bankOpenAttempts = 0;

    @Override
    public String getName() { return "Axiom Smithing"; }

    @Override
    public void onStart(ScriptSettings raw)
    {
        this.settings = (raw instanceof SmithingSettings)
            ? (SmithingSettings) raw
            : SmithingSettings.defaults();

        antiban.setBreakIntervalMinutes(settings.breakIntervalMinutes);
        antiban.setBreakDurationMinutes(settings.breakDurationMinutes);
        antiban.reset();

        startTileRecorded = false;
        state = State.FIND_STATION;
        productionTracker.resetDialog();
        productionTracker.resetAnimation();
        resetBankState();

        if (isAnvilMode())
        {
            log.info("Smithing started: method={} bar={} item={} bankForBars={}",
                settings.method.displayName,
                settings.barType.barName,
                settings.smithItem.displayName,
                settings.bankForBars);
        }
        else
        {
            log.info("Smithing started: method={} product={} bankForBars={}",
                settings.method.displayName,
                settings.furnaceProduct.displayName,
                settings.bankForBars);
        }

        if (isBlastFurnaceMode() && !equipment.hasItemEquipped(ICE_GLOVES_ID))
        {
            log.warn("Blast Furnace mode requires ice gloves equipped — stopping");
            stop();
        }
    }

    @Override
    public void onLoop()
    {
        if (!startTileRecorded)
        {
            WorldTile startTile = new WorldTile(players.getWorldX(), players.getWorldY(), players.getPlane());
            locationProfile = LocationProfile.centered(
                "smithing-" + settings.locationPreset.name().toLowerCase(),
                startTile,
                settings.locationPreset.workAreaRadius
            ).withBankTargets(
                settings.locationPreset.bankObjectNames,
                settings.locationPreset.bankNpcNames,
                settings.locationPreset.bankActions
            );
            startTileRecorded = true;
            log.info("[INIT] Start tile recorded: ({},{},{})",
                startTile.getWorldX(), startTile.getWorldY(), startTile.getPlane());
            log.info("[INIT] Location preset: {}", settings.locationPreset.displayName);
        }

        switch (state)
        {
            case FIND_STATION: handleFindStation(); break;
            case HANDLE_DIALOG: handleDialog(); break;
            case WAITING: handleWaiting(); break;
            case BLAST_COLLECT: handleBlastCollect(); break;
            case BANKING: handleBanking(); break;
        }
    }

    @Override
    public void onStop()
    {
        log.info("Smithing stopped");
    }

    private void handleFindStation()
    {
        if (isBlastFurnaceMode())
        {
            handleBlastFindStation();
            return;
        }

        if (isDialogAlreadyOpen())
        {
            log.info("[FIND_STATION] Production dialog already open — proceeding");
            state = State.HANDLE_DIALOG;
            return;
        }

        if (!hasRequiredWorkingMaterials())
        {
            if (settings.bankForBars)
            {
                log.info("[FIND_STATION] Missing materials — transitioning to BANKING");
                state = State.BANKING;
            }
            else
            {
                log.info("[FIND_STATION] Missing materials and bankForBars=false — stopping");
                stop();
            }
            return;
        }

        SceneObject station = findNearestStation();
        if (station == null)
        {
            log.info("[FIND_STATION] No {} found nearby — waiting", getStationLabel());
            setTickDelay(3);
            return;
        }

        String action = isAnvilMode() ? "Smith" : "Smelt";
        log.info("[FIND_STATION] Clicking {} '{}' at ({},{})",
            action, station.getName(), station.getWorldX(), station.getWorldY());
        station.interact(action);

        productionTracker.resetDialog();
        productionTracker.resetAnimation();
        setTickDelay(Math.max(antiban.reactionTicks(), 2));
        state = State.HANDLE_DIALOG;
    }

    private void handleBlastFindStation()
    {
        if (!hasRequiredWorkingMaterials())
        {
            if (settings.bankForBars)
            {
                log.info("[FIND_STATION] Missing Blast Furnace materials — transitioning to BANKING");
                state = State.BANKING;
            }
            else
            {
                log.info("[FIND_STATION] Missing Blast Furnace materials and bankForBars=false — stopping");
                stop();
            }
            return;
        }

        SceneObject conveyor = findNearestStation();
        if (conveyor == null)
        {
            log.info("[FIND_STATION] No blast furnace conveyor found nearby — waiting");
            setTickDelay(3);
            return;
        }

        String action = conveyor.hasAction("Put-ore-on") ? "Put-ore-on" : "Use";
        log.info("[FIND_STATION] Loading Blast Furnace conveyor");
        conveyor.interact(action);
        setTickDelay(4);
        state = State.BLAST_COLLECT;
    }

    private void handleDialog()
    {
        boolean dialogOpen = isAnvilMode() ? widgets.isSmithingDialogOpen() : widgets.isMakeDialogOpen();
        ProductionTickTracker.DialogStatus dialogStatus =
            productionTracker.observeDialog(dialogOpen, DIALOG_TIMEOUT_TICKS);

        if (dialogStatus != ProductionTickTracker.DialogStatus.OPEN)
        {
            if (dialogStatus == ProductionTickTracker.DialogStatus.TIMED_OUT)
            {
                log.warn("[HANDLE_DIALOG] {} dialog did not open after {} ticks — retrying",
                    getStationLabel(), DIALOG_TIMEOUT_TICKS);
                productionTracker.resetDialog();
                state = State.FIND_STATION;
            }
            else
            {
                log.info("[HANDLE_DIALOG] Waiting for {} dialog ({}/{})",
                    getStationLabel(),
                    productionTracker.getDialogWaitTicks(),
                    DIALOG_TIMEOUT_TICKS);
                setTickDelay(1);
            }
            return;
        }

        if (isAnvilMode())
        {
            log.info("[HANDLE_DIALOG] Smithing dialog open — clicking {} (id={})",
                settings.smithItem.displayName, settings.smithItem.itemId);
            widgets.clickSmithingItem(settings.smithItem.itemId);
        }
        else
        {
            log.info("[HANDLE_DIALOG] Furnace dialog open — selecting '{}'",
                settings.furnaceProduct.makeOptionText);
            if (!widgets.clickMakeOption(settings.furnaceProduct.makeOptionText))
            {
                log.warn("[HANDLE_DIALOG] Furnace option '{}' not found — retrying",
                    settings.furnaceProduct.makeOptionText);
                productionTracker.resetDialog();
                state = State.FIND_STATION;
                return;
            }
        }

        productionTracker.resetDialog();
        productionTracker.resetAnimation();
        setTickDelay(2);
        state = State.WAITING;
    }

    private void handleWaiting()
    {
        if (isBlastFurnaceMode())
        {
            state = State.BLAST_COLLECT;
            return;
        }

        ProductionTickTracker.BatchStatus batchStatus =
            productionTracker.observeAnimation(players.isAnimating(), ANIMATION_TIMEOUT_TICKS);

        if (batchStatus == ProductionTickTracker.BatchStatus.STARTED)
        {
            log.info("[WAITING] Production animation started");
            return;
        }

        if (batchStatus == ProductionTickTracker.BatchStatus.IN_PROGRESS)
        {
            return;
        }

        if (batchStatus == ProductionTickTracker.BatchStatus.COMPLETED)
        {
            log.info("[WAITING] Production animation stopped");
            transitionAfterProduction();
            return;
        }

        if (batchStatus == ProductionTickTracker.BatchStatus.TIMEOUT)
        {
            log.info("[WAITING] Animation never started (timeout) — retrying");
            productionTracker.resetAnimation();
            state = State.FIND_STATION;
        }
        else
        {
            log.info("[WAITING] Waiting for production animation ({}/{})",
                productionTracker.getNoAnimationTicks(), ANIMATION_TIMEOUT_TICKS);
        }
    }

    private void handleBlastCollect()
    {
        if (inventory.containsById(settings.furnaceProduct.outputBarType.barItemId))
        {
            log.info("[BLAST] Bars collected — banking");
            state = State.BANKING;
            return;
        }

        SceneObject dispenser = gameObjects.nearest(o ->
            o.getName().equalsIgnoreCase("Bar dispenser") && (o.hasAction("Take") || o.hasAction("Check")));
        if (dispenser == null)
        {
            log.info("[BLAST] Bar dispenser not found — waiting");
            setTickDelay(3);
            return;
        }

        if (dispenser.hasAction("Take"))
        {
            log.info("[BLAST] Taking bars from dispenser");
            dispenser.interact("Take");
            setTickDelay(3);
        }
        else
        {
            log.info("[BLAST] Waiting for bars at dispenser");
            setTickDelay(2);
        }
    }

    private void transitionAfterProduction()
    {
        if (hasRequiredWorkingMaterials())
        {
            log.info("[WAITING] Materials remain — returning to FIND_STATION");
            state = State.FIND_STATION;
            return;
        }

        if (settings.bankForBars)
        {
            log.info("[WAITING] Materials depleted — transitioning to BANKING");
            state = State.BANKING;
        }
        else
        {
            log.info("[WAITING] Materials depleted and bankForBars=false — stopping");
            stop();
        }
    }

    private void handleBanking()
    {
        if (!bank.isOpen())
        {
            bankJustOpened = false;
            bankDepositDone = false;
            bankWithdrawStep = 0;

            if (bankOpenAttempts >= MAX_BANK_OPEN_ATTEMPTS)
            {
                log.warn("[BANKING] Could not open bank after {} attempts — stopping",
                    MAX_BANK_OPEN_ATTEMPTS);
                bankOpenAttempts = 0;
                stop();
                return;
            }

            if (bank.openNearest(locationProfile))
            {
                log.info("[BANKING] Clicked bank (attempt {}/{})",
                    bankOpenAttempts + 1, MAX_BANK_OPEN_ATTEMPTS);
                bankOpenAttempts++;
                setTickDelay(3);
            }
            else
            {
                log.debug("[BANKING] Walking to bank...");
                setTickDelay(2);
            }
            return;
        }

        bankOpenAttempts = 0;

        if (!bankJustOpened)
        {
            if (locationProfile != null && !locationProfile.hasBankAnchor())
            {
                WorldTile bankTile = new WorldTile(players.getWorldX(), players.getWorldY(), players.getPlane());
                locationProfile = locationProfile.withBankAnchor(bankTile);
                log.info("[BANKING] Bank anchor recorded: ({},{},{})",
                    bankTile.getWorldX(), bankTile.getWorldY(), bankTile.getPlane());
            }
            log.info("[BANKING] Bank open — waiting for UI to load");
            bankJustOpened = true;
            setTickDelay(2);
            return;
        }

        if (!bankDepositDone)
        {
            if (bank.depositAllExcept(getProtectedBankItemIds()))
            {
                log.debug("[BANKING] Depositing items...");
                setTickDelay(1);
                return;
            }
            log.info("[BANKING] Deposit complete");
            bankDepositDone = true;
        }

        if (withdrawMissingMaterials())
        {
            setTickDelay(1);
            return;
        }

        if (!hasRequiredWorkingMaterials())
        {
            log.warn("[BANKING] Could not assemble the next material batch — stopping");
            bank.close();
            stop();
            return;
        }

        log.info("[BANKING] Closing bank");
        bank.close();
        resetBankState();

        WorldTile currentTile = new WorldTile(players.getWorldX(), players.getWorldY(), players.getPlane());
        if (locationProfile != null && locationProfile.shouldReturnToWorkArea(currentTile))
        {
            WorldTile returnAnchor = locationProfile.getReturnAnchor();
            log.info("[BANKING] Returning to smithing area ({},{},{})",
                returnAnchor.getWorldX(), returnAnchor.getWorldY(), returnAnchor.getPlane());
            pathfinder.walkTo(returnAnchor.getWorldX(), returnAnchor.getWorldY(), returnAnchor.getPlane());
            setTickDelay(5);
        }
        else
        {
            setTickDelay(2);
        }

        state = State.FIND_STATION;
    }

    private void resetBankState()
    {
        bankJustOpened = false;
        bankDepositDone = false;
        bankWithdrawStep = 0;
    }

    private boolean withdrawMissingMaterials()
    {
        if (isAnvilMode())
        {
            return withdrawAnvilMaterials();
        }
        return withdrawFurnaceMaterials();
    }

    private boolean withdrawAnvilMaterials()
    {
        if (!inventory.containsById(SmithingSettings.HAMMER_ID))
        {
            log.warn("[BANKING] Hammer missing after deposit protection — stopping");
            return false;
        }

        if (inventory.containsById(settings.barType.barItemId))
        {
            return false;
        }

        if (!bank.contains(settings.barType.barItemId))
        {
            log.warn("[BANKING] No {} left in bank", settings.barType.barName);
            return false;
        }

        log.info("[BANKING] Withdrawing {}", settings.barType.barName);
        bank.withdrawAll(settings.barType.barItemId);
        return true;
    }

    private boolean withdrawFurnaceMaterials()
    {
        FurnaceProduct product = settings.furnaceProduct;
        if (isBlastFurnaceMode() && product.requiresAmmoMould)
        {
            log.warn("[BANKING] Cannonballs are not supported in Blast Furnace mode");
            return false;
        }

        if (product.requiresAmmoMould && !inventory.containsById(SmithingSettings.AMMO_MOULD_ID))
        {
            if (!bank.contains(SmithingSettings.AMMO_MOULD_ID))
            {
                log.warn("[BANKING] Ammo mould missing from bank");
                return false;
            }

            log.info("[BANKING] Withdrawing ammo mould");
            bank.withdraw(SmithingSettings.AMMO_MOULD_ID, 1);
            return true;
        }

        if (product.requiresSecondaryOre() && !inventory.containsById(product.secondaryMaterialId))
        {
            if (!bank.contains(product.secondaryMaterialId))
            {
                log.warn("[BANKING] Secondary material missing for {}", product.displayName);
                return false;
            }

            int amount = getSecondaryWithdrawAmount(product);
            log.info("[BANKING] Withdrawing secondary material id={} x{}",
                product.secondaryMaterialId, amount);
            bank.withdraw(product.secondaryMaterialId, amount);
            return true;
        }

        if (!inventory.containsById(product.primaryMaterialId))
        {
            if (!bank.contains(product.primaryMaterialId))
            {
                log.warn("[BANKING] Primary material missing for {}", product.displayName);
                return false;
            }

            int amount = getPrimaryWithdrawAmount(product);
            if (amount == Integer.MAX_VALUE)
            {
                log.info("[BANKING] Withdrawing all primary material id={}", product.primaryMaterialId);
                bank.withdrawAll(product.primaryMaterialId);
            }
            else
            {
                log.info("[BANKING] Withdrawing primary material id={} x{}",
                    product.primaryMaterialId, amount);
                bank.withdraw(product.primaryMaterialId, amount);
            }
            return true;
        }

        if (product.requiresCoal() && inventory.count(product.coalItemId) < getRequiredCoalAmount(product))
        {
            if (!bank.contains(product.coalItemId))
            {
                log.warn("[BANKING] Coal missing for {}", product.displayName);
                return false;
            }

            int amount = getRequiredCoalAmount(product);
            log.info("[BANKING] Withdrawing coal x{}", amount);
            bank.withdraw(product.coalItemId, amount);
            return true;
        }

        return false;
    }

    private boolean hasRequiredWorkingMaterials()
    {
        if (isAnvilMode())
        {
            return inventory.containsById(SmithingSettings.HAMMER_ID)
                && inventory.containsById(settings.barType.barItemId);
        }

        FurnaceProduct product = settings.furnaceProduct;
        if (isBlastFurnaceMode() && product.requiresAmmoMould)
        {
            return false;
        }
        if (product.requiresAmmoMould && !inventory.containsById(SmithingSettings.AMMO_MOULD_ID))
        {
            return false;
        }
        if (!inventory.containsById(product.primaryMaterialId))
        {
            return false;
        }
        if (product.requiresSecondaryOre() && !inventory.containsById(product.secondaryMaterialId))
        {
            return false;
        }
        return !product.requiresCoal() || inventory.count(product.coalItemId) >= getCoalPerPrimary(product);
    }

    private boolean isDialogAlreadyOpen()
    {
        return isAnvilMode() ? widgets.isSmithingDialogOpen() : widgets.isMakeDialogOpen();
    }

    private SceneObject findNearestStation()
    {
        if (isAnvilMode())
        {
            return gameObjects.nearest(o -> matchesAny(o.getId(), ANVIL_IDS));
        }

        if (isBlastFurnaceMode())
        {
            return gameObjects.nearest(o ->
                o.getName().equalsIgnoreCase("Conveyor belt")
                    && (o.hasAction("Put-ore-on") || o.hasAction("Use")));
        }

        return gameObjects.nearest(o ->
            o.getName().equalsIgnoreCase("Furnace") && o.hasAction("Smelt"));
    }

    private boolean isAnvilMode()
    {
        return settings.method == SmithingMethod.ANVIL;
    }

    private boolean isBlastFurnaceMode()
    {
        return settings.method == SmithingMethod.BLAST_FURNACE;
    }

    private String getStationLabel()
    {
        if (isAnvilMode())
        {
            return "smithing";
        }
        return isBlastFurnaceMode() ? "blast furnace" : "furnace";
    }

    private int[] getProtectedBankItemIds()
    {
        if (isAnvilMode())
        {
            return new int[]{ SmithingSettings.HAMMER_ID };
        }

        if (settings.furnaceProduct.requiresAmmoMould)
        {
            return new int[]{ SmithingSettings.AMMO_MOULD_ID };
        }

        return new int[0];
    }

    private int getPrimaryWithdrawAmount(FurnaceProduct product)
    {
        if (product.requiresAmmoMould)
        {
            return Integer.MAX_VALUE;
        }

        if (product.requiresSecondaryOre())
        {
            return 14;
        }

        if (product.requiresCoal())
        {
            int reservedSlots = 0;
            int totalSlots = 28 - reservedSlots;
            return Math.max(1, totalSlots / (1 + getCoalPerPrimary(product)));
        }

        return Integer.MAX_VALUE;
    }

    private int getSecondaryWithdrawAmount(FurnaceProduct product)
    {
        return product.requiresSecondaryOre() ? 14 : 0;
    }

    private int getRequiredCoalAmount(FurnaceProduct product)
    {
        return product.requiresCoal() ? getPrimaryWithdrawAmount(product) * getCoalPerPrimary(product) : 0;
    }

    private int getCoalPerPrimary(FurnaceProduct product)
    {
        if (!isBlastFurnaceMode())
        {
            return product.coalPerPrimary;
        }

        return Math.max(0, product.coalPerPrimary / 2);
    }

    private static boolean matchesAny(int id, int[] ids)
    {
        for (int candidate : ids)
        {
            if (candidate == id)
            {
                return true;
            }
        }
        return false;
    }
}
