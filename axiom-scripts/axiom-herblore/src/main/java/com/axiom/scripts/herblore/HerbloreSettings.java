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
    /** Clean herbs with their IDs, level requirements, and resulting unfinished potion IDs. */
    public enum HerbType
    {
        GUAM       ("Guam leaf",       249,  3,  "Guam potion (unf)",        91),
        MARRENTILL ("Marrentill",      251,  5,  "Marrentill potion (unf)",  93),
        TARROMIN   ("Tarromin",        253, 11,  "Tarromin potion (unf)",    95),
        HARRALANDER("Harralander",     255, 20,  "Harralander potion (unf)", 97),
        RANARR     ("Ranarr weed",     257, 25,  "Ranarr potion (unf)",      99),
        TOADFLAX   ("Toadflax",       2998, 30,  "Toadflax potion (unf)",  3002),
        IRIT       ("Irit leaf",       259, 40,  "Irit potion (unf)",       101),
        AVANTOE    ("Avantoe",         261, 48,  "Avantoe potion (unf)",    103),
        KWUARM     ("Kwuarm",          263, 54,  "Kwuarm potion (unf)",     105),
        SNAPDRAGON ("Snapdragon",     3000, 59,  "Snapdragon potion (unf)", 3004),
        CADANTINE  ("Cadantine",       265, 65,  "Cadantine potion (unf)",  107),
        LANTADYME  ("Lantadyme",      2481, 67,  "Lantadyme potion (unf)", 2483),
        DWARF_WEED ("Dwarf weed",      267, 70,  "Dwarf weed potion (unf)", 109),
        TORSTOL    ("Torstol",         269, 75,  "Torstol potion (unf)",    111);

        public final String herbName;
        public final int    herbId;
        public final int    levelRequired;
        public final String potionName;
        public final int    unfPotionId;

        /** Vial of water — always the same secondary ingredient for unfinished potions. */
        public static final int VIAL_OF_WATER_ID = 227;

        HerbType(String herbName, int herbId, int levelRequired,
                 String potionName, int unfPotionId)
        {
            this.herbName      = herbName;
            this.herbId        = herbId;
            this.levelRequired = levelRequired;
            this.potionName    = potionName;
            this.unfPotionId   = unfPotionId;
        }
    }

    public final HerbType herbType;

    /** True = walk to bank for more herbs/vials when inventory runs out. */
    public final boolean bankForIngredients;

    /** Average minutes between antiban breaks. */
    public final int breakIntervalMinutes;

    /** Average duration of each antiban break. */
    public final int breakDurationMinutes;

    public HerbloreSettings(
        HerbType herbType,
        boolean  bankForIngredients,
        int      breakIntervalMinutes,
        int      breakDurationMinutes)
    {
        this.herbType             = herbType;
        this.bankForIngredients   = bankForIngredients;
        this.breakIntervalMinutes = breakIntervalMinutes;
        this.breakDurationMinutes = breakDurationMinutes;
    }

    /** Sensible defaults used when no config dialog is shown. */
    public static HerbloreSettings defaults()
    {
        return new HerbloreSettings(HerbType.GUAM, false, 60, 5);
    }
}
