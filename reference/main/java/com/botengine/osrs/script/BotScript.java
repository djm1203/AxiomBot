package com.botengine.osrs.script;

import com.botengine.osrs.BotEngineConfig;
import com.botengine.osrs.ui.ScriptConfigDialog;
import com.botengine.osrs.ui.ScriptSettings;
import com.botengine.osrs.api.Bank;
import com.botengine.osrs.api.Camera;
import com.botengine.osrs.api.Combat;
import com.botengine.osrs.api.GameObjects;
import com.botengine.osrs.api.GrandExchange;
import com.botengine.osrs.api.GroundItems;
import com.botengine.osrs.api.Interaction;
import com.botengine.osrs.api.Inventory;
import com.botengine.osrs.api.Magic;
import com.botengine.osrs.api.Movement;
import com.botengine.osrs.api.Npcs;
import com.botengine.osrs.api.Players;
import com.botengine.osrs.api.Prayers;
import com.botengine.osrs.util.Antiban;
import com.botengine.osrs.util.Log;
import com.botengine.osrs.util.Time;
import com.botengine.osrs.util.WorldHopper;
import net.runelite.api.Client;

/**
 * Abstract base class for all bot scripts.
 *
 * Every skill script (WoodcuttingScript, FishingScript, etc.) extends this class.
 * Scripts implement their logic as a state machine inside onLoop(), which is
 * called once per game tick (600ms) by ScriptRunner while the state is RUNNING.
 *
 * Scripts access the game exclusively through the injected api/ layer fields.
 * Direct imports of net.runelite.api are not permitted inside scripts.
 *
 * Lifecycle:
 *   onStart() → [onLoop() × N ticks] → onStop()
 *
 * Example minimal script:
 *
 *   public class MyScript extends BotScript {
 *       public String getName() { return "My Script"; }
 *
 *       public void onStart() {
 *           log.info("Starting");
 *       }
 *
 *       public void onLoop() {
 *           if (players.isIdle()) {
 *               interaction.click(gameObjects.nearest(1751), "Chop down");
 *           }
 *       }
 *
 *       public void onStop() {
 *           log.info("Stopped");
 *       }
 *   }
 */
public abstract class BotScript
{
    // ── RuneLite core ────────────────────────────────────────────────────────
    protected Client client;

    // ── API layer — game state queries ───────────────────────────────────────
    protected Players players;
    protected Npcs npcs;
    protected GameObjects gameObjects;
    protected Inventory inventory;

    // ── API layer — game actions ──────────────────────────────────────────────
    protected Bank bank;
    protected Movement movement;
    protected Interaction interaction;
    protected Magic magic;
    protected Combat combat;
    protected Camera camera;
    protected Prayers prayers;
    protected GroundItems groundItems;
    protected WorldHopper worldHopper;
    protected GrandExchange grandExchange;

    // ── Utilities ─────────────────────────────────────────────────────────────
    protected Antiban antiban;
    protected Time time;
    protected Log log;

    /**
     * Tick delay counter. When > 0, ScriptRunner skips onLoop() for that many
     * ticks (decrementing each time). Scripts call setTickDelay() to insert a
     * human-like pause between state transitions without sleeping the client thread.
     */
    private int tickDelay = 0;

    /** Returns true if this script is currently in a tick-delay pause. */
    public final boolean hasTickDelay() { return tickDelay > 0; }

    /** Called by ScriptRunner each tick when hasTickDelay() is true. */
    public final void decrementTickDelay() { if (tickDelay > 0) tickDelay--; }

    /** Scripts call this to pause execution for N ticks before the next onLoop(). */
    protected final void setTickDelay(int ticks) { tickDelay = Math.max(0, ticks); }

    /**
     * Called by ScriptRunner to inject all dependencies before onStart().
     * Scripts never call this directly.
     */
    public final void inject(
        Client client,
        Players players,
        Npcs npcs,
        GameObjects gameObjects,
        Inventory inventory,
        Bank bank,
        Movement movement,
        Interaction interaction,
        Magic magic,
        Combat combat,
        Camera camera,
        Prayers prayers,
        GroundItems groundItems,
        WorldHopper worldHopper,
        GrandExchange grandExchange,
        Antiban antiban,
        Time time,
        Log log
    )
    {
        this.client = client;
        this.players = players;
        this.npcs = npcs;
        this.gameObjects = gameObjects;
        this.inventory = inventory;
        this.bank = bank;
        this.movement = movement;
        this.interaction = interaction;
        this.magic = magic;
        this.combat = combat;
        this.camera = camera;
        this.prayers = prayers;
        this.groundItems = groundItems;
        this.worldHopper = worldHopper;
        this.grandExchange = grandExchange;
        this.antiban = antiban;
        this.time = time;
        this.log = log;
    }

    // ── Optional configuration ────────────────────────────────────────────────

    /**
     * Called by ScriptRunner after inject() and before onStart().
     * Passes both global plugin config (antiban, safety, debug) and the
     * per-script settings populated from the Axiom config dialog.
     *
     * Override this in each script to read field values.
     * {@code scriptSettings} will be null when called from tests — guard with null check.
     *
     * @param globalConfig  global RuneLite config (antiban, emergency logout, debug)
     * @param scriptSettings script-specific settings from the Axiom dialog (may be null)
     */
    public void configure(BotEngineConfig globalConfig, ScriptSettings scriptSettings) {}

    /**
     * Backward-compatible overload — passes null for scriptSettings.
     * Used by ScriptRunnerTest and any caller that hasn't migrated yet.
     */
    public final void configure(BotEngineConfig config)
    {
        configure(config, null);
    }

    /**
     * Returns the per-script Axiom config dialog for this script.
     * AxiomPanel calls this to open the configuration popup before starting.
     *
     * @param parent the panel used to anchor the dialog to RuneLite's window
     */
    public abstract ScriptConfigDialog<?> createConfigDialog(javax.swing.JComponent parent);

    // ── Contract every script must implement ─────────────────────────────────

    /**
     * Human-readable name shown in the panel and overlay.
     * Example: "Woodcutting", "High Alchemy", "Sand Crabs Combat"
     */
    public abstract String getName();

    /**
     * Called once when the script is started.
     * Use this to set initial state, log startup info, validate requirements.
     */
    public abstract void onStart();

    /**
     * Called once per game tick (every ~600ms) while state is RUNNING.
     * Implement your state machine logic here.
     * Must return quickly — never block or sleep inside onLoop().
     */
    public abstract void onLoop();

    /**
     * Called once when the script is stopped (by user or error).
     * Use this to clean up any state.
     */
    public abstract void onStop();
}
