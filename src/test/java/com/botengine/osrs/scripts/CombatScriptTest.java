package com.botengine.osrs.scripts;

import com.botengine.osrs.api.*;
import com.botengine.osrs.scripts.combat.CombatScript;
import com.botengine.osrs.util.Antiban;
import com.botengine.osrs.util.Log;
import com.botengine.osrs.util.Time;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)

/**
 * Unit tests for CombatScript.
 *
 * Covers:
 *   - No target found → no attack called
 *   - Target found → combat.attackNpc() called
 *   - Low HP → combat.eat() called before attacking
 *   - Dead target → skipped
 *   - Idle timeout → re-searches for target
 */
@ExtendWith(MockitoExtension.class)
class CombatScriptTest
{
    @Mock private Client client;
    @Mock private Players players;
    @Mock private Npcs npcs;
    @Mock private GameObjects gameObjects;
    @Mock private Inventory inventory;
    @Mock private Bank bank;
    @Mock private Movement movement;
    @Mock private Interaction interaction;
    @Mock private Magic magic;
    @Mock private Combat combat;
    @Mock private Camera camera;
    @Mock private Antiban antiban;
    @Mock private Time time;
    @Mock private Log log;
    @Mock private NPC target;

    private CombatScript script;

    @BeforeEach
    void setUp()
    {
        script = new CombatScript();
        script.inject(client, players, npcs, gameObjects, inventory,
            bank, movement, interaction, magic, combat, camera, antiban, time, log);
        script.onStart();
    }

    // ── FIND_TARGET state ─────────────────────────────────────────────────────

    @Test
    void noTarget_doesNotAttack()
    {
        when(players.shouldEat(anyInt())).thenReturn(false);
        when(npcs.nearest(anyString())).thenReturn(null);

        script.onLoop();

        verify(combat, never()).attackNpc(any());
    }

    @Test
    void targetFound_attacksNpc()
    {
        when(players.shouldEat(anyInt())).thenReturn(false);
        when(npcs.nearest(anyString())).thenReturn(target);
        when(target.isDead()).thenReturn(false);
        when(combat.attackNpc(target)).thenReturn(true);

        script.onLoop();

        verify(combat).attackNpc(target);
    }

    @Test
    void deadTarget_isSkipped()
    {
        when(players.shouldEat(anyInt())).thenReturn(false);
        when(npcs.nearest(anyString())).thenReturn(target);
        when(target.isDead()).thenReturn(true);

        script.onLoop();

        verify(combat, never()).attackNpc(any());
    }

    // ── Eating ────────────────────────────────────────────────────────────────

    @Test
    void lowHp_eatsBeforeAttacking()
    {
        when(players.shouldEat(anyInt())).thenReturn(true);
        when(inventory.contains(anyInt())).thenReturn(true); // has food

        script.onLoop();

        verify(combat).eat(anyInt());
        verify(combat, never()).attackNpc(any());
    }

    @Test
    void lowHp_noFood_doesNotThrow()
    {
        when(players.shouldEat(anyInt())).thenReturn(true);
        when(inventory.contains(anyInt())).thenReturn(false);

        // Should log a warning but not throw
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> script.onLoop());
        verify(combat, never()).eat(anyInt());
    }

    // ── Antiban ───────────────────────────────────────────────────────────────

    @Test
    void attack_callsReactionDelay()
    {
        when(players.shouldEat(anyInt())).thenReturn(false);
        when(npcs.nearest(anyString())).thenReturn(target);
        when(target.isDead()).thenReturn(false);
        when(combat.attackNpc(target)).thenReturn(true);

        script.onLoop();

        verify(antiban).reactionDelay();
    }
}
