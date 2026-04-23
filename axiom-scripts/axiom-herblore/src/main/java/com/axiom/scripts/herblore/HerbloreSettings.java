package com.axiom.scripts.herblore;

import com.axiom.api.script.ScriptSettings;

/**
 * Configuration for the Herblore script.
 * Immutable value object passed into onStart().
 *
 * Item IDs sourced from OSRS Wiki.
 */
public class HerbloreSettings extends ScriptSettings
{
    public enum Method
    {
        CLEANING("Clean herb"),
        UNFINISHED("Unfinished potion"),
        FINISHED("Finished potion");

        public final String displayName;

        Method(String displayName)
        {
            this.displayName = displayName;
        }
    }

    /** Clean herbs with their IDs, level requirements, and resulting unfinished potion IDs. */
    public enum HerbType
    {
        GUAM       ("Grimy guam leaf",       199,  "Guam leaf",       249,  3,  "Guam potion (unf)",        91),
        MARRENTILL ("Grimy marrentill",      201,  "Marrentill",      251,  5,  "Marrentill potion (unf)",  93),
        TARROMIN   ("Grimy tarromin",        203,  "Tarromin",        253, 11,  "Tarromin potion (unf)",    95),
        HARRALANDER("Grimy harralander",     205,  "Harralander",     255, 20,  "Harralander potion (unf)", 97),
        RANARR     ("Grimy ranarr weed",     207,  "Ranarr weed",     257, 25,  "Ranarr potion (unf)",      99),
        TOADFLAX   ("Grimy toadflax",       3049,  "Toadflax",       2998, 30,  "Toadflax potion (unf)",  3002),
        IRIT       ("Grimy irit leaf",       209,  "Irit leaf",       259, 40,  "Irit potion (unf)",       101),
        AVANTOE    ("Grimy avantoe",         211,  "Avantoe",         261, 48,  "Avantoe potion (unf)",    103),
        KWUARM     ("Grimy kwuarm",          213,  "Kwuarm",          263, 54,  "Kwuarm potion (unf)",     105),
        SNAPDRAGON ("Grimy snapdragon",     3051,  "Snapdragon",     3000, 59,  "Snapdragon potion (unf)", 3004),
        CADANTINE  ("Grimy cadantine",       215,  "Cadantine",       265, 65,  "Cadantine potion (unf)",  107),
        LANTADYME  ("Grimy lantadyme",      2485,  "Lantadyme",      2481, 67,  "Lantadyme potion (unf)", 2483),
        DWARF_WEED ("Grimy dwarf weed",      217,  "Dwarf weed",      267, 70,  "Dwarf weed potion (unf)", 109),
        TORSTOL    ("Grimy torstol",         219,  "Torstol",         269, 75,  "Torstol potion (unf)",    111);

        public final String grimyName;
        public final int    grimyId;
        public final String herbName;
        public final int    herbId;
        public final int    levelRequired;
        public final String potionName;
        public final int    unfPotionId;

        /** Vial of water — always the same secondary ingredient for unfinished potions. */
        public static final int VIAL_OF_WATER_ID = 227;

        HerbType(String grimyName, int grimyId, String herbName, int herbId, int levelRequired,
                 String potionName, int unfPotionId)
        {
            this.grimyName     = grimyName;
            this.grimyId       = grimyId;
            this.herbName      = herbName;
            this.herbId        = herbId;
            this.levelRequired = levelRequired;
            this.potionName    = potionName;
            this.unfPotionId   = unfPotionId;
        }
    }

    public final Method method;
    public final HerbType herbType;
    public final int secondaryItemId;
    public final String secondaryItemName;

    /** True = walk to bank for more herbs/vials when inventory runs out. */
    public final boolean bankForIngredients;

    /** Average minutes between antiban breaks. */
    public final int breakIntervalMinutes;

    /** Average duration of each antiban break. */
    public final int breakDurationMinutes;

    public HerbloreSettings(
        Method   method,
        HerbType herbType,
        int      secondaryItemId,
        String   secondaryItemName,
        boolean  bankForIngredients,
        int      breakIntervalMinutes,
        int      breakDurationMinutes)
    {
        this.method               = method != null ? method : Method.UNFINISHED;
        this.herbType             = herbType;
        this.secondaryItemId      = secondaryItemId;
        this.secondaryItemName    = secondaryItemName != null ? secondaryItemName.trim() : "";
        this.bankForIngredients   = bankForIngredients;
        this.breakIntervalMinutes = breakIntervalMinutes;
        this.breakDurationMinutes = breakDurationMinutes;
    }

    /** Sensible defaults used when no config dialog is shown. */
    public static HerbloreSettings defaults()
    {
        return new HerbloreSettings(Method.UNFINISHED, HerbType.GUAM, 0, "", false, 60, 5);
    }
}
