package com.axiom.scripts.alchemy;

import com.axiom.api.game.Messages;
import com.axiom.api.game.Players;
import com.axiom.api.game.Widgets;
import com.axiom.api.player.Equipment;
import com.axiom.api.player.Inventory;
import com.axiom.api.player.Skills;
import com.axiom.api.script.BotScript;
import com.axiom.api.script.ScriptCategory;
import com.axiom.api.script.ScriptManifest;
import com.axiom.api.script.ScriptSettings;
import com.axiom.api.util.Antiban;
import com.axiom.api.util.InventoryDeltaTracker;
import com.axiom.api.util.Log;
import com.axiom.api.util.RetryBudget;
import com.axiom.api.world.Bank;

/**
 * Axiom Alchemy — validates Magic spellbook widget interaction.
 *
 * Key difference from all prior scripts: the interaction targets a UI widget
 * (spellbook) rather than a game object, NPC, or inventory item pair. Both
 * clicks use RobotClick via the Widgets and Inventory APIs.
 *
 * State machine:
 *   CAST_SPELL → SELECT_ITEM → WAITING → CAST_SPELL
 *                                       ↓ (bankForItems, no items left)
 *                                     BANKING → CAST_SPELL
 */
@ScriptManifest(
    name        = "Axiom Alchemy",
    version     = "1.0",
    category    = ScriptCategory.SKILLING,
    author      = "Axiom",
    description = "Casts High or Low Alchemy on a configured item until inventory is empty."
)
public class AlchemyScript extends BotScript
{
    // ── Injected by ScriptRunner ──────────────────────────────────────────────
    private Inventory inventory;
    private Equipment equipment;
    private Players   players;
    private Skills    skills;
    private Messages  messages;
    private Widgets   widgets;
    private Bank      bank;
    private Antiban   antiban;
    private Log       log;

    // ── Settings ──────────────────────────────────────────────────────────────
    private AlchemySettings settings;

    // ── State machine ─────────────────────────────────────────────────────────
    private enum State { CAST_SPELL, SELECT_ITEM, WAITING, BANKING }
    private State state = State.CAST_SPELL;

    private boolean wasAnimating     = false;
    private int     noAnimationTicks = 0;
    private int     cooldownTicksRemaining = 0;
    private final InventoryDeltaTracker itemDeltaTracker = new InventoryDeltaTracker();
    // 7 ticks = 5-tick alchemy cooldown plus a small grace window.
    private static final int ANIMATION_TIMEOUT_TICKS = 7;
    private static final int CAST_COOLDOWN_TICKS = 5;
    // Animation ID 712 = High Alchemy / Low Alchemy cast animation.
    private static final int ALCH_ANIMATION_ID = 712;
    private static final int NATURE_RUNE_ID = 561;
    private static final int FIRE_RUNE_ID   = 554;
    private static final int MAX_SPELL_WIDGET_RETRIES = 4;
    private final RetryBudget spellWidgetRetries = new RetryBudget(MAX_SPELL_WIDGET_RETRIES);

    // Banking
    private boolean bankJustOpened   = false;
    private boolean bankWithdrawDone = false;
    private int     bankOpenAttempts = 0;
    private static final int MAX_BANK_OPEN_ATTEMPTS = 20;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public String getName() { return "Axiom Alchemy"; }

    @Override
    public void onStart(ScriptSettings raw)
    {
        this.settings = (raw instanceof AlchemySettings)
            ? (AlchemySettings) raw
            : AlchemySettings.defaults();

        antiban.setBreakIntervalMinutes(settings.breakIntervalMinutes);
        antiban.setBreakDurationMinutes(settings.breakDurationMinutes);
        antiban.reset();
        spellWidgetRetries.reset();
        itemDeltaTracker.reset();

        log.info("Alchemy started: spell={} item='{}' (id={}) bankForItems={}",
            settings.spell.displayName, settings.targetItemName,
            settings.targetItemId, settings.bankForItems);

        if (settings.targetItemId <= 0)
        {
            log.warn("Alchemy requires a valid target item ID — stopping");
            stop();
            return;
        }

        int magicLevel = skills.getBaseLevel(Skills.Skill.MAGIC);
        if (magicLevel < settings.spell.levelRequired)
        {
            log.warn("Magic level {} is below the {} requirement for {} — stopping",
                magicLevel, settings.spell.levelRequired, settings.spell.displayName);
            stop();
            return;
        }

        state = State.CAST_SPELL;
    }

    @Override
    public void onLoop()
    {
        switch (state)
        {
            case CAST_SPELL:  handleCastSpell();  break;
            case SELECT_ITEM: handleSelectItem(); break;
            case WAITING:     handleWaiting();    break;
            case BANKING:     handleBanking();    break;
        }
    }

    @Override
    public void onStop()
    {
        log.info("Alchemy stopped");
    }

    // ── State handlers ────────────────────────────────────────────────────────

    private void handleCastSpell()
    {
        if (!hasAlchemyResources())
        {
            stop();
            return;
        }

        if (!inventory.contains(settings.targetItemId))
        {
            if (settings.bankForItems)
            {
                log.info("[CAST_SPELL] No items — transitioning to BANKING");
                state = State.BANKING;
            }
            else
            {
                log.info("[CAST_SPELL] No items to alch — script complete");
                stop();
            }
            return;
        }

        boolean spellClicked = widgets.clickWidgetContainingText(
            settings.spell.widgetGroup, settings.spell.displayName);
        if (!spellClicked && widgets.isWidgetVisible(settings.spell.widgetGroup, settings.spell.widgetChild))
        {
            widgets.clickWidget(settings.spell.widgetGroup, settings.spell.widgetChild);
            spellClicked = true;
        }

        if (!spellClicked)
        {
            spellWidgetRetries.fail();
            if (spellWidgetRetries.isExhausted())
            {
                log.warn("[CAST_SPELL] Spell widget stayed unavailable after {} retries — stopping",
                    spellWidgetRetries.getMaxAttempts());
                stop();
                return;
            }

            log.warn("[CAST_SPELL] Spell widget not found — is the Magic tab open? Retry {}/{}",
                spellWidgetRetries.getAttempts(), spellWidgetRetries.getMaxAttempts());
            setTickDelay(2);
            return;
        }

        spellWidgetRetries.reset();
        log.info("[CAST_SPELL] Clicking {}", settings.spell.displayName);

        // No delay — spell click and item click fire on consecutive ticks.
        // The 5-tick server cooldown is enforced server-side regardless.
        state = State.SELECT_ITEM;
    }

    private void handleSelectItem()
    {
        log.info("[SELECT_ITEM] Clicking {} (id={})",
            settings.targetItemName.isEmpty() ? "item" : settings.targetItemName,
            settings.targetItemId);
        itemDeltaTracker.capture(inventory, settings.targetItemId);
        cooldownTicksRemaining = CAST_COOLDOWN_TICKS;
        inventory.clickItem(settings.targetItemId);

        wasAnimating     = false;
        noAnimationTicks = 0;

        int reactionTicks = Math.max(antiban.reactionTicks(), 1);
        log.debug("[SELECT_ITEM] Reaction delay: {} ticks before WAITING", reactionTicks);
        setTickDelay(reactionTicks);
        state = State.WAITING;
    }

    private void handleWaiting()
    {
        if (messages.pollRecent("don't have enough", 6) != null
            || messages.pollRecent("do not have enough", 6) != null)
        {
            log.warn("[WAITING] Client reported missing runes or requirements — stopping");
            stop();
            return;
        }

        if (messages.pollRecentRegex("can't reach that|nothing interesting happens|you can't cast.*", 6) != null)
        {
            log.warn("[WAITING] Cast interaction failed — retrying");
            resetWaitingState();
            state = State.CAST_SPELL;
            return;
        }

        if (cooldownTicksRemaining > 0)
        {
            cooldownTicksRemaining--;
        }

        boolean itemConsumed = itemDeltaTracker.hasAnyDecrease(inventory);
        if (players.getAnimation() == ALCH_ANIMATION_ID)
        {
            if (!wasAnimating) log.info("[WAITING] Alchemy animation started (id={})", ALCH_ANIMATION_ID);
            else               log.debug("[WAITING] Still casting...");
            wasAnimating     = true;
            noAnimationTicks = 0;
            return;
        }

        noAnimationTicks++;

        if ((wasAnimating || itemConsumed) && cooldownTicksRemaining <= 0)
        {
            log.info("[WAITING] Cast complete — next cast");
            resetWaitingState();
            state        = State.CAST_SPELL;
        }
        else if (noAnimationTicks >= ANIMATION_TIMEOUT_TICKS && cooldownTicksRemaining <= 0)
        {
            if (itemConsumed)
            {
                log.info("[WAITING] Cooldown elapsed with inventory delta — next cast");
            }
            else
            {
                log.info("[WAITING] Cooldown elapsed without animation or inventory delta — retrying");
            }
            resetWaitingState();
            state = State.CAST_SPELL;
        }
        else
        {
            log.debug("[WAITING] Waiting for cast completion animTicks={}/{} cooldown={} itemConsumed={}",
                noAnimationTicks, ANIMATION_TIMEOUT_TICKS, cooldownTicksRemaining, itemConsumed);
        }
    }

    private void handleBanking()
    {
        if (!bank.isOpen())
        {
            bankJustOpened   = false;
            bankWithdrawDone = false;

            if (bankOpenAttempts >= MAX_BANK_OPEN_ATTEMPTS)
            {
                log.info("[BANKING] Could not open bank after {} attempts — stopping",
                    MAX_BANK_OPEN_ATTEMPTS);
                bankOpenAttempts = 0;
                stop();
                return;
            }

            log.info("[BANKING] Opening bank (attempt {}/{})",
                bankOpenAttempts + 1, MAX_BANK_OPEN_ATTEMPTS);
            bank.openNearest();
            bankOpenAttempts++;
            setTickDelay(3);
            return;
        }

        bankOpenAttempts = 0;

        if (!bankJustOpened)
        {
            log.info("[BANKING] Bank open — waiting for UI to load");
            bankJustOpened = true;
            setTickDelay(2);
            return;
        }

        if (!bankWithdrawDone)
        {
            if (!bank.contains(settings.targetItemId))
            {
                log.info("[BANKING] No more {} in bank — stopping",
                    settings.targetItemName.isEmpty() ? "items" : settings.targetItemName);
                bank.close();
                bankJustOpened   = false;
                bankWithdrawDone = false;
                stop();
                return;
            }

            log.info("[BANKING] Withdrawing all {}",
                settings.targetItemName.isEmpty() ? "item " + settings.targetItemId : settings.targetItemName);
            bank.withdrawAll(settings.targetItemId);
            bankWithdrawDone = true;
            setTickDelay(2);
            return;
        }

        log.info("[BANKING] Withdraw complete — closing bank");
        bank.close();
        bankJustOpened   = false;
        bankWithdrawDone = false;
        setTickDelay(2);
        state = State.CAST_SPELL;
    }

    private boolean hasAlchemyResources()
    {
        int natureRunes = inventory.count(NATURE_RUNE_ID);
        if (natureRunes <= 0)
        {
            log.warn("[CAST_SPELL] No nature runes available — stopping");
            return false;
        }

        if (!hasInfiniteFireSource() && inventory.count(FIRE_RUNE_ID) < settings.spell.fireRuneCost)
        {
            log.warn("[CAST_SPELL] Need {} fire runes or a fire staff/tome for {} — stopping",
                settings.spell.fireRuneCost, settings.spell.displayName);
            return false;
        }

        return true;
    }

    private boolean hasInfiniteFireSource()
    {
        return equipment.hasItemEquippedByName("staff of fire")
            || equipment.hasItemEquippedByName("fire battlestaff")
            || equipment.hasItemEquippedByName("mystic fire battlestaff")
            || equipment.hasItemEquippedByName("lava battlestaff")
            || equipment.hasItemEquippedByName("mystic lava battlestaff")
            || equipment.hasItemEquippedByName("smoke battlestaff")
            || equipment.hasItemEquippedByName("mystic smoke battlestaff")
            || equipment.hasItemEquippedByName("steam battlestaff")
            || equipment.hasItemEquippedByName("mystic steam battlestaff")
            || equipment.hasItemEquippedByName("tome of fire");
    }

    private void resetWaitingState()
    {
        wasAnimating = false;
        noAnimationTicks = 0;
        cooldownTicksRemaining = 0;
        itemDeltaTracker.reset();
    }
}
