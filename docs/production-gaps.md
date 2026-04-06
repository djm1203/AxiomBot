# Production Script Gaps

What production bots (TriBot, DreamBot, RuneMate) have that we currently don't.
Ordered by impact — highest value additions first within each script.

---

## Cross-Cutting (affects all scripts)

| Feature | Current state | What's needed |
|---|---|---|
| Banking mode | All scripts power-drop only | Walk to bank, deposit, return loop |
| Pathfinding | `movement.walkTo()` (direct tile walk) | Obstacle-aware pathfinding (doors, ladders, agility shortcuts) |
| World hopping | Not implemented | Hop on depletion/competition, low-ping world selection |
| Config persistence | RuneLite config panel only | Save/load JSON per-script profiles |
| Progressive leveling | Fixed target | Auto-switch method when level threshold reached |
| Shift-click drop | One item at a time with delay | Hold shift + glide mouse over inventory for fast mass-drop |
| ABC2 antiban | Gaussian delays + breaks | Per-account unique behavior seeds, hover mechanics, fatigue curve |
| Grand Exchange restocking | Not implemented | Auto-buy supplies when running low |
| Emergency logout | Stops script on low HP/no food | Actual game logout, not just script stop |

---

## Combat

**Currently has:** NPC targeting by name, food eating, camera rotation, animation detection, configurable eat threshold.

| Missing feature | Notes |
|---|---|
| Prayer support | Activate/deactivate prayers (Protect from Melee/Ranged/Magic, offensive prayers). Track remaining prayer points, drink prayer potions at threshold |
| Prayer potion management | Detect prayer points %, drink super/regular prayer pots from inventory or bank |
| Loot pickup | Detect ground items after kill, filter by configurable value threshold or item whitelist, pick up before targeting next NPC |
| Special attack usage | Monitor spec bar %, auto-use spec weapon at configured threshold (e.g. Dragon Dagger at 55%) |
| Cannon support | Place dwarf cannon, refill cannonballs, detect when cannon empty |
| Safe-spotting | Walk to a safe tile relative to target NPC and re-position if knocked out of safe spot |
| Position reset (Sand Crabs) | Detect when crabs go dormant, walk away and return to wake them |
| Poison/venom detection | Drink antipoison/antivenom when status inflicted |
| Superantifire detection | Equip/drink before fighting dragons |
| Multiway zone detection | Handle multi-combat areas (don't re-click while already being attacked by 2+ NPCs) |
| Slayer task tracking | Count kills, stop when task complete |
| Combat style selection | Set attack style (accurate/aggressive/defensive/controlled) on the combat tab |
| Configurable loot list | User-supplied item ID/name whitelist or min GE value for pickup |

---

## Woodcutting

**Currently has:** Tree finding by ID or name filter, animation-based chop detection, power-drop.

| Missing feature | Notes |
|---|---|
| Banking mode | Walk to nearest bank, deposit logs, return and resume |
| Bird nest detection | When nest drops (object spawns on ground), pick it up before continuing |
| Axe upgrading | Detect when a better axe is available in bank, equip it |
| Forestry events | Detect Forestry activity spawns (pheasant, poaching, rising roots etc.) and participate for bonus XP/rewards |
| Progressive tree selection | Auto-switch tree type when Woodcutting level reaches threshold (e.g. switch Oak→Willow at 30, Willow→Maple at 45) |
| Position randomization | Don't always click same tile/tree — rotate among nearby valid trees |
| Crystal/Infernal axe tracking | Track special charge usage |

---

## Mining

**Currently has:** Rock finding by ID or name filter, animation-based mine detection, power-drop.

| Missing feature | Notes |
|---|---|
| Banking mode | Walk to bank, deposit ore, return |
| Rock depletion repositioning | When current rock depletes, immediately hover/move to next prioritized rock rather than waiting for FIND_ROCK tick |
| World hopping on competition | Detect other players mining same rocks, hop world |
| Progressive ore selection | Auto-switch ore type at level thresholds |
| Gem rock support | Mine gem rocks, handle cut/uncut gems |
| Motherlode Mine mode | Navigate upper/lower mine, use sack, repair struts, bank pay-dirt |
| 3-rock rotation | Track 2–3 rocks in a cluster and rotate among them optimally |
| Coal bag support | Detect coal bag in inventory, fill/empty it |

---

## Fishing

**Currently has:** Spot finding by NPC ID, configurable action, animation detection, power-drop.

| Missing feature | Notes |
|---|---|
| Banking mode | Walk to bank, deposit fish, return |
| Shift-drop mode | Shift-click drop fish one-by-one with mouse glide for faster inventory clearing |
| Spot prediction | When spot moves, check nearby tiles first before full `nearest()` scan |
| Tool requirement detection | Warn/stop if required tool (fly rod, harpoon, etc.) not equipped or in inventory |
| Karambwan fishing | Special spot + cooking integration |
| Minnow fishing | Attention-based — spot moves every ~15s, must re-click frequently |
| Barbarian fishing | Requires strength/agility levels, uses barbarian rod |
| Energy/stamina potion | Drink when run energy drops below threshold while walking to bank |

---

## High Alchemy

**Currently has:** Basic alchemy loop (item hardcoded).

| Missing feature | Notes |
|---|---|
| Configurable item selection | User picks which item ID to alch — currently hardcoded |
| Nature rune tracking | Count remaining runes, stop/bank when running low |
| Staff of Fire detection | If equipped, skip fire rune requirement; if not, ensure fire runes in inventory |
| Rune pouch support | Withdraw/detect runes inside rune pouch |
| Banking loop | Restock items and runes from bank when supply runs out |
| Profit/XP tracking overlay | Show casts per hour, GP profit, XP/hr |
| Multi-item support | Cycle through a list of items to alch (priority order) |

---

## Cooking

**Currently has:** Basic cooking state machine.

| Missing feature | Notes |
|---|---|
| Location selection | Support multiple cooking spots: Rogues' Den (no burn with gauntlets), Lumbridge range, Hosidius kitchen (+5% burn reduction), Nardah, Al Kharid |
| Cooking gauntlet detection | Detect if Cooking gauntlets equipped (reduces burn rate on lobster/swordfish/shark) |
| Banking loop | Walk to bank, deposit cooked food, withdraw raw food |
| Burn rate tracking | Track burned food %, adjust strategy or switch location |
| Fire detection | If cooking on a player-made fire, detect when it goes out and relight or move |
| Progressive food switching | Auto-switch food type at level thresholds for max XP |
| Make-All dialog | Handle "How many?" / "Make All" dialog that appears when clicking raw food on range |
| Wine making | Jug of water + grapes → ferment (no fire needed, mass XP method) |

---

## Fletching

**Currently has:** Basic fletching state machine.

| Missing feature | Notes |
|---|---|
| Method selection | Support darts, bolts, bows (stringing + cutting), arrow shafts |
| 1-tick fletching | Click item then immediately click next — no wait for animation to complete — for darts/bolts |
| Make-X dialog handling | Handle the "How many would you like to make?" dialog |
| Banking loop | Restock materials (feathers, tips, logs, string) from bank |
| Inventory layout enforcement | Darts/bolts: tips in slot 1, feathers in slot 2 for correct 1-tick behavior |
| Material quantity tracking | Count remaining tips/feathers/logs, trigger bank trip when low |
| Stringing mode | Use bow string on unstrung bow with Make-All dialog |

---

## Smithing

**Currently has:** Basic smithing state machine.

| Missing feature | Notes |
|---|---|
| Blast Furnace mode | Conveyor belt deposit, collect bars, pay foreman, manage coal bag |
| Banking loop | Walk to bank, deposit bars/items, withdraw ore |
| Furnace vs anvil mode | Detect which action (smelting vs smithing) and navigate appropriately |
| Coal bag support | Detect coal bag, fill before trip, empty at furnace |
| Goldsmithing gauntlets | Detect if equipped for gold smelting XP bonus |
| Make-X dialog | Handle the dialog when smithing items at anvil |
| Item selection config | User picks what to smith (platebody, platelegs, helm, bars, etc.) |
| Ore-to-bar tracking | Count ore supply, trigger resupply when running low |

---

## Crafting

**Currently has:** Basic gem cutting state machine.

| Missing feature | Notes |
|---|---|
| Jewelry crafting mode | Gold bar + gem → ring/necklace/bracelet/amulet at furnace |
| Banking loop | Restock uncut gems or gold bars from bank |
| Crushed gem detection | Semi-precious gems (Opal, Jade, Red Topaz) can fail → crushed gem; detect and drop |
| Make-X dialog | Handle dialog for jewelry crafting |
| Chisel tool check | Verify chisel in inventory before starting; withdraw from bank if missing |
| Leather crafting mode | Needle + thread + leather → gloves/chaps/body |
| Gem type progression | Auto-switch gem type at level thresholds |
| Enchanting integration | After crafting jewelry, optionally cast enchant spell on it |

---

## Priority Order for Implementation

1. **Banking loop** (all scripts — biggest functional gap, affects usability most)
2. **Combat: prayer + prayer potions** (core combat feature)
3. **Combat: loot pickup** (required for any profitable combat)
4. **Make-X / Make-All dialog handling** (Cooking, Smithing, Fletching, Crafting all need it)
5. **Shift-drop** (Fishing, Mining quality-of-life)
6. **World hopping** (Mining, Woodcutting — needed for resource competition)
7. **Combat: special attack** (common AFK training optimization)
8. **Progressive leveling** (all skills — reduces manual intervention)
9. **Configurable item selection for Alchemy** (currently hardcoded — trivial fix)
10. **Bird nests** (Woodcutting — popular feature)
11. **Blast Furnace** (Smithing — best XP/GP method)
12. **Cannon support** (Combat — high-value optional feature)
