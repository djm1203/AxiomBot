package com.axiom.scripts.cooking;

import com.axiom.api.script.ScriptSettings;

/**
 * Configuration for the Cooking script.
 *
 * Cooking finds the nearest fire or range object, selects the raw food item,
 * clicks the object to open the production dialog, and clicks Make All.
 * When raw food runs out it banks for more if bankForFood is true.
 */
public class CookingSettings extends ScriptSettings
{
    public final FoodType       foodType;
    public final CookingLocation location;
    public final boolean         bankForFood;
    public final int             breakIntervalMinutes;
    public final int             breakDurationMinutes;

    // ── Food types ────────────────────────────────────────────────────────────

    public enum FoodType
    {
        SHRIMPS   ("Raw shrimps",    317, "Shrimps",    315,  1),
        SARDINE   ("Raw sardine",    327, "Sardine",    325,  1),
        HERRING   ("Raw herring",    345, "Herring",    347,  5),
        ANCHOVIES ("Raw anchovies",  321, "Anchovies",  319,  1),
        TROUT     ("Raw trout",      335, "Trout",      333, 15),
        SALMON    ("Raw salmon",     331, "Salmon",     329, 25),
        TUNA      ("Raw tuna",       359, "Tuna",       361, 30),
        LOBSTER   ("Raw lobster",    377, "Lobster",    379, 40),
        SWORDFISH ("Raw swordfish",  371, "Swordfish",  373, 45),
        MONKFISH  ("Raw monkfish", 7944, "Monkfish",  7946, 62),
        SHARK     ("Raw shark",      383, "Shark",      385, 80);

        public final String rawName;
        public final int    rawId;
        public final String cookedName;
        public final int    cookedId;
        public final int    levelRequired;

        FoodType(String rawName, int rawId, String cookedName, int cookedId, int levelRequired)
        {
            this.rawName       = rawName;
            this.rawId         = rawId;
            this.cookedName    = cookedName;
            this.cookedId      = cookedId;
            this.levelRequired = levelRequired;
        }
    }

    // ── Cooking locations ─────────────────────────────────────────────────────

    /**
     * Object IDs for common cooking locations.
     *
     * RANGE IDs — standard cooking ranges in F2P areas:
     *   114   = Lumbridge Castle kitchen range
     *   2728  = Al Kharid palace range
     *   9682  = Rogues' Den range
     *   12009 = Hosidius kitchen (P2P)
     *
     * FIRE IDs — player-lit fires:
     *   26185–26187 = standard fire objects
     *   26574       = fire lit on a gravestone tile
     *
     * Objects are searched by ID. If the exact ID is not in the list, the
     * script will fail to find the object and log a diagnostic message.
     */
    public enum CookingLocation
    {
        RANGE ("Cooking range", new int[]{
            114,   // Lumbridge Castle (Cook-o-matic 100)
            2728,  // Al Kharid palace range
            9682,  // Various F2P ranges
            12009, // Various ranges
            26181, // Ardougne / Kandarin ranges
            27290, // Hosidius Kitchen
            34549, // Myths' Guild
            24009, // Cooking Guild
            21302, // Port Sarim
            2029,  // Falador / Varrock area
            3039   // Edgeville (Doris's house)
        }, "Cook"),
        FIRE  ("Fire", new int[]{
            26185, 26186, 26187, 26574, // Player-lit fires
            43475, 43476                // Rogues' Den eternal fire
        }, "Cook");

        public final String objectName;
        public final int[]  objectIds;
        public final String cookAction;

        CookingLocation(String objectName, int[] objectIds, String cookAction)
        {
            this.objectName  = objectName;
            this.objectIds   = objectIds;
            this.cookAction  = cookAction;
        }
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    public CookingSettings(
        FoodType       foodType,
        CookingLocation location,
        boolean         bankForFood,
        int             breakIntervalMinutes,
        int             breakDurationMinutes)
    {
        this.foodType             = foodType;
        this.location             = location;
        this.bankForFood          = bankForFood;
        this.breakIntervalMinutes = breakIntervalMinutes;
        this.breakDurationMinutes = breakDurationMinutes;
    }

    public static CookingSettings defaults()
    {
        return new CookingSettings(
            FoodType.TROUT,
            CookingLocation.RANGE,
            true,
            60,
            5
        );
    }
}
