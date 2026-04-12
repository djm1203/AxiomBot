package com.axiom.scripts.alchemy;

import com.axiom.api.game.Players;
import com.axiom.api.game.Widgets;
import com.axiom.api.player.Inventory;
import com.axiom.api.script.BotScript;
import com.axiom.api.script.ScriptCategory;
import com.axiom.api.script.ScriptManifest;
import com.axiom.api.script.ScriptSettings;
import com.axiom.api.util.Antiban;
import com.axiom.api.util.Log;
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
    private Players   players;
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
    // 6 ticks matches the server-side 5-tick alchemy cooldown + 1 grace tick.
    private static final int ANIMATION_TIMEOUT_TICKS = 6;
    // Animation ID 712 = High Alchemy / Low Alchemy cast animation.
    private static final int ALCH_ANIMATION_ID = 712;

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

        log.info("Alchemy started: spell={} item='{}' (id={}) bankForItems={}",
            settings.spell.displayName, settings.targetItemName,
            settings.targetItemId, settings.bankForItems);

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

        if (!widgets.isWidgetVisible(settings.spell.widgetGroup, settings.spell.widgetChild))
        {
            log.warn("[CAST_SPELL] Spell widget not found — is the Magic tab open? Waiting 2 ticks");
            setTickDelay(2);
            return;
        }

        log.info("[CAST_SPELL] Clicking {}", settings.spell.displayName);
        widgets.clickWidget(settings.spell.widgetGroup, settings.spell.widgetChild);

        // No delay — spell click and item click fire on consecutive ticks.
        // The 5-tick server cooldown is enforced server-side regardless.
        state = State.SELECT_ITEM;
    }

    private void handleSelectItem()
    {
        log.info("[SELECT_ITEM] Clicking {} (id={})",
            settings.targetItemName.isEmpty() ? "item" : settings.targetItemName,
            settings.targetItemId);
        inventory.clickItem(settings.targetItemId);

        wasAnimating     = false;
        noAnimationTicks = 0;

        int reactionTicks = Math.max(antiban.reactionTicks(), 2);
        log.debug("[SELECT_ITEM] Reaction delay: {} ticks before WAITING", reactionTicks);
        setTickDelay(reactionTicks);
        state = State.WAITING;
    }

    private void handleWaiting()
    {
        if (players.getAnimation() == ALCH_ANIMATION_ID)
        {
            if (!wasAnimating) log.info("[WAITING] Alchemy animation started (id={})", ALCH_ANIMATION_ID);
            else               log.debug("[WAITING] Still casting...");
            wasAnimating     = true;
            noAnimationTicks = 0;
            return;
        }

        noAnimationTicks++;

        if (wasAnimating)
        {
            log.info("[WAITING] Cast complete — next cast");
            wasAnimating = false;
            state        = State.CAST_SPELL;
        }
        else if (noAnimationTicks >= ANIMATION_TIMEOUT_TICKS)
        {
            log.info("[WAITING] Animation timeout — retrying from CAST_SPELL");
            wasAnimating     = false;
            noAnimationTicks = 0;
            state            = State.CAST_SPELL;
        }
        else
        {
            log.debug("[WAITING] Waiting for animation ({}/{})", noAnimationTicks, ANIMATION_TIMEOUT_TICKS);
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
}
