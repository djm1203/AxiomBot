package com.botengine.osrs.scripts;

import com.botengine.osrs.api.*;
import com.botengine.osrs.scripts.herblore.HerbloreScript;
import com.botengine.osrs.scripts.herblore.HerbloreSettings;
import com.botengine.osrs.util.Antiban;
import com.botengine.osrs.util.Log;
import com.botengine.osrs.util.Time;
import com.botengine.osrs.util.WorldHopper;
import net.runelite.api.Client;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * State machine tests for HerbloreScript (Clean and Make Potion modes).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HerbloreScriptTest
{
    private static final int GRIMY_GUAM = 169;
    private static final int CLEAN_GUAM = 199;
    private static final int EYE_OF_NEWT = 221;
    private static final int VIAL_OF_WATER = 227;
    private static final int ATTACK_POT_UNF = 91;

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
    @Mock private Prayers prayers;
    @Mock private GroundItems groundItems;
    @Mock private WorldHopper worldHopper;
    @Mock private GrandExchange grandExchange;
    @Mock private Antiban antiban;
    @Mock private Time time;
    @Mock private Log log;

    private HerbloreScript script;

    @BeforeEach
    void setUp()
    {
        script = new HerbloreScript();
        script.inject(client, players, npcs, gameObjects, inventory,
            bank, movement, interaction, magic, combat, camera,
            prayers, groundItems, worldHopper, grandExchange, antiban, time, log);
    }

    // ── Clean mode ────────────────────────────────────────────────────────────

    @Test
    void cleanMode_hasGrimyHerb_cleansIt()
    {
        HerbloreSettings s = new HerbloreSettings();
        s.mode       = "Clean";
        s.herbItemId = GRIMY_GUAM;
        script.configure(null, s);
        script.onStart();

        when(inventory.contains(GRIMY_GUAM)).thenReturn(true);

        script.onLoop();

        verify(interaction).clickInventoryItem(GRIMY_GUAM, "Clean", 1);
    }

    @Test
    void cleanMode_noGrimyHerb_goesToBanking()
    {
        HerbloreSettings s = new HerbloreSettings();
        s.mode       = "Clean";
        s.herbItemId = GRIMY_GUAM;
        script.configure(null, s);
        script.onStart();

        when(inventory.contains(GRIMY_GUAM)).thenReturn(false);
        when(bank.isOpen()).thenReturn(false);
        when(bank.isNearBank()).thenReturn(false);

        script.onLoop(); // CLEANING → BANKING
        script.onLoop(); // BANKING: not near bank

        verify(movement).setRunning(true);
    }

    @Test
    void cleanMode_bankOpen_withdrawsGrimyHerbs()
    {
        HerbloreSettings s = new HerbloreSettings();
        s.mode       = "Clean";
        s.herbItemId = GRIMY_GUAM;
        script.configure(null, s);
        script.onStart();

        when(inventory.contains(GRIMY_GUAM)).thenReturn(false);
        when(bank.isOpen()).thenReturn(true);
        when(bank.contains(GRIMY_GUAM)).thenReturn(true);

        script.onLoop(); // CLEANING → BANKING
        script.onLoop(); // BANKING: open → depositAll + withdraw

        verify(bank).depositAll();
        verify(bank).withdraw(GRIMY_GUAM, Integer.MAX_VALUE);
    }

    // ── Make Potion mode ──────────────────────────────────────────────────────

    @Test
    void makePotionMode_step1_usesHerbOnVial()
    {
        HerbloreSettings s = new HerbloreSettings();
        s.mode          = "Make Potion";
        s.cleanHerbId   = CLEAN_GUAM;
        s.vialOfWaterId = VIAL_OF_WATER;
        s.secondaryId   = EYE_OF_NEWT;
        script.configure(null, s);
        script.onStart();

        when(inventory.contains(CLEAN_GUAM)).thenReturn(true);
        when(inventory.contains(VIAL_OF_WATER)).thenReturn(true);
        when(inventory.getSlot(CLEAN_GUAM)).thenReturn(0);
        when(inventory.getSlot(VIAL_OF_WATER)).thenReturn(1);

        script.onLoop(); // MAKE_POTION_STEP1 → useItemOnItem(herb, vial)

        verify(interaction).useItemOnItem(0, 1);
    }

    @Test
    void makePotionMode_step2_usesSecondaryOnUnfPot()
    {
        HerbloreSettings s = new HerbloreSettings();
        s.mode          = "Make Potion";
        s.cleanHerbId   = CLEAN_GUAM;
        s.vialOfWaterId = VIAL_OF_WATER;
        s.secondaryId   = EYE_OF_NEWT;
        script.configure(null, s);
        script.onStart();

        // Step 1 already done — herb and vial gone, unf pot present
        when(inventory.contains(CLEAN_GUAM)).thenReturn(false);
        when(inventory.contains(VIAL_OF_WATER)).thenReturn(false);
        when(inventory.contains(ATTACK_POT_UNF)).thenReturn(true);
        when(inventory.contains(EYE_OF_NEWT)).thenReturn(true);
        when(inventory.getSlot(ATTACK_POT_UNF)).thenReturn(0);
        when(inventory.getSlot(EYE_OF_NEWT)).thenReturn(1);

        script.onLoop(); // MAKE_POTION_STEP1 → detects unf pot present → STEP2
        script.onLoop(); // MAKE_POTION_STEP2 → useItemOnItem(secondary, unfPot)

        verify(interaction).useItemOnItem(1, 0);
    }

    @Test
    void makePotionMode_noIngredients_goesToBanking()
    {
        HerbloreSettings s = new HerbloreSettings();
        s.mode          = "Make Potion";
        s.cleanHerbId   = CLEAN_GUAM;
        s.vialOfWaterId = VIAL_OF_WATER;
        s.secondaryId   = EYE_OF_NEWT;
        script.configure(null, s);
        script.onStart();

        when(inventory.contains(CLEAN_GUAM)).thenReturn(false);
        when(inventory.contains(VIAL_OF_WATER)).thenReturn(false);
        when(inventory.contains(ATTACK_POT_UNF)).thenReturn(false);
        when(bank.isOpen()).thenReturn(false);
        when(bank.isNearBank()).thenReturn(false);

        script.onLoop(); // STEP1 → no ingredients → BANKING
        script.onLoop(); // BANKING: not near bank

        verify(movement).setRunning(true);
    }
}
