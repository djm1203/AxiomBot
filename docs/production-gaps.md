# Production Script Gaps

What production bots (TriBot, DreamBot, RuneMate) have that we currently don't.
Ordered by impact — highest value additions first within each script.

Status key: ✅ Done | 🔲 Not started | 🔜 In progress

---

## Cross-Cutting (affects all scripts)

| Feature | Status | Notes |
|---|---|---|
| Banking mode | ✅ | All scripts have bank/deposit/return loop |
| World hopping utility | ✅ | WorldHopper.java — low-pop world selection, hop cooldown |
| World hop on competition | ✅ | Mining + Woodcutting — hop when player within 5 tiles |
| Shift-click drop | ✅ | Mining + Fishing — dropAll() fires all drops in one pass |
| Pathfinding | 🔲 | Obstacle-aware pathfinding (doors, ladders, agility shortcuts) |
| Config persistence | 🔲 | Save/load JSON per-script profiles |
| Progressive leveling | ✅ | 'level:value,...' string config — woodcutting, mining, fishing |
| ABC2 antiban | ✅ | Account seed (per-run timing variation) + fatigue curve (delays grow over session) |
| Grand Exchange restocking | ✅ | GrandExchange API — open, create buy offer, collect; used by cannon for cannonballs |
| Emergency logout | ✅ | Logs out when HP critically low and no food — configurable threshold |

---

## Combat

**Has:** NPC targeting, food eating, camera rotation, animation detection, configurable eat threshold, prayer activation/potions, loot pickup (configurable item list), special attack, banking loop.

| Missing feature | Status | Notes |
|---|---|---|
| Prayer support | ✅ | Protective + offensive prayers, prayer pot drinking at threshold |
| Loot pickup | ✅ | Ground item scan, configurable item ID whitelist |
| Special attack | ✅ | Monitor spec %, auto-use at configured threshold |
| Configurable loot list | ✅ | Comma-separated item IDs in config panel |
| Banking loop | ✅ | Banks when inventory full, restocks food |
| Cannon support | ✅ | Place dwarf cannon, refill cannonballs by tick threshold, GE restock for balls |
| Safe-spotting | 🔲 | Walk to safe tile, reposition if knocked out |
| Position reset (Sand Crabs) | ✅ | Detects passive crabs, walks 15 tiles away and returns to reset aggro |
| Poison/venom detection | 🔲 | Drink antipoison/antivenom when status inflicted |
| Superantifire detection | 🔲 | Equip/drink before fighting dragons |
| Multiway zone detection | 🔲 | Handle multi-combat areas |
| Slayer task tracking | 🔲 | Count kills, stop when task complete |
| Combat style selection | 🔲 | Set attack style on the combat tab |

---

## Woodcutting

**Has:** Tree finding by ID/name filter, animation-based detection, power-drop, banking mode, bird nest pickup, world hop on competition.

| Missing feature | Status | Notes |
|---|---|---|
| Banking mode | ✅ | Walk to bank, deposit, return |
| Bird nest detection | ✅ | Picks up nests before continuing |
| World hop on competition | ✅ | Hops when another player within 5 tiles |
| Axe upgrading | 🔲 | Detect better axe in bank, equip it |
| Forestry events | 🔲 | Detect activity spawns and participate |
| Progressive tree selection | 🔲 | Auto-switch tree type at level thresholds |
| Position randomization | 🔲 | Rotate among nearby valid trees |
| Crystal/Infernal axe tracking | 🔲 | Track special charge usage |

---

## Mining

**Has:** Rock finding by ID/name filter, animation-based detection, power-drop, banking mode, shift-drop mode, world hop on competition.

| Missing feature | Status | Notes |
|---|---|---|
| Banking mode | ✅ | Walk to bank, deposit ore, return |
| Shift-drop mode | ✅ | dropAll() fires all drops in one pass |
| World hop on competition | ✅ | Hops when another player within 5 tiles |
| Rock depletion repositioning | 🔲 | Hover/move to next rock immediately on depletion |
| Progressive ore selection | 🔲 | Auto-switch ore type at level thresholds |
| Gem rock support | 🔲 | Mine gem rocks, handle cut/uncut gems |
| Motherlode Mine mode | ✅ | Pay-dirt → hopper → sack collect, broken strut repair, banking loop |
| 3-rock rotation | 🔲 | Track 2–3 rocks in a cluster and rotate optimally |
| Coal bag support | 🔲 | Fill/empty coal bag |

---

## Fishing

**Has:** Spot finding by NPC ID, configurable action, animation detection, power-drop, banking mode, shift-drop mode.

| Missing feature | Status | Notes |
|---|---|---|
| Banking mode | ✅ | Walk to bank, deposit fish, return |
| Shift-drop mode | ✅ | dropAll() fires all drops in one pass |
| Spot prediction | 🔲 | Check nearby tiles first when spot moves |
| Tool requirement detection | 🔲 | Warn/stop if required tool missing |
| Karambwan fishing | 🔲 | Special spot + cooking integration |
| Minnow fishing | 🔲 | Attention-based — spot moves every ~15s |
| Barbarian fishing | 🔲 | Requires strength/agility levels |
| Energy/stamina potion | 🔲 | Drink when run energy drops below threshold |

---

## High Alchemy

**Has:** Configurable item ID (0 = auto-detect), nature rune tracking, Staff of Fire detection, banking loop.

| Missing feature | Status | Notes |
|---|---|---|
| Configurable item selection | ✅ | User picks item ID or auto-detects non-rune |
| Nature rune tracking | ✅ | Stops/banks when out of runes |
| Banking loop | ✅ | Restocks nature runes and items from bank |
| Rune pouch support | 🔲 | Detect runes inside rune pouch |
| Profit/XP tracking overlay | 🔲 | Show casts/hr, GP profit, XP/hr |
| Multi-item support | 🔲 | Cycle through a list of items to alch |

---

## Cooking

**Has:** Fire/range detection, Use-food-on-fire, Make-All dialog, banking loop.

| Missing feature | Status | Notes |
|---|---|---|
| Make-All dialog | ✅ | Handles "How many?" dialog |
| Banking loop | ✅ | Deposit cooked food, withdraw raw food |
| Location selection | 🔲 | Rogues' Den, Hosidius kitchen, etc. |
| Cooking gauntlet detection | 🔲 | Reduce burn rate on lobster/swordfish/shark |
| Burn rate tracking | 🔲 | Track burned food % |
| Progressive food switching | 🔲 | Auto-switch at level thresholds |
| Wine making | 🔲 | Jug of water + grapes mass method |

---

## Fletching

**Has:** Knife+logs (cutting) and string+unstrung (stringing) auto-detection, Make-All dialog, banking loop.

| Missing feature | Status | Notes |
|---|---|---|
| Make-All dialog | ✅ | Handles production dialog |
| Banking loop | ✅ | Restocks logs/strings/unstrung bows |
| 1-tick fletching | ✅ | Darts + bolts mode — useItemOnItem each tick, no dialogue wait |
| Darts/bolts method | ✅ | Bronze through dragon dart tips + bolt tips, feathers auto-detected |
| Inventory layout enforcement | 🔲 | Tips in slot 1, feathers in slot 2 for 1-tick |
| Material quantity tracking | 🔲 | Count remaining tips/feathers, trigger bank early |

---

## Smithing

**Has:** Anvil detection, Use-bar-on-anvil, Make-All dialog, banking loop for bars.

| Missing feature | Status | Notes |
|---|---|---|
| Make-All dialog | ✅ | Handles smithing dialog |
| Banking loop | ✅ | Deposits bars/items, withdraws next bar type |
| Blast Furnace mode | ✅ | Conveyor belt, collect bars, coal bag fill/empty, banking loop |
| Furnace vs anvil mode | 🔲 | Detect which action and navigate appropriately |
| Coal bag support | 🔲 | Fill before trip, empty at furnace |
| Goldsmithing gauntlets | 🔲 | Detect if equipped for gold smelting XP bonus |
| Item selection config | 🔲 | User picks what to smith |
| Ore-to-bar tracking | 🔲 | Count ore supply, trigger resupply early |

---

## Crafting

**Has:** Gem cutting (chisel + uncut gems), Make-All dialog, banking loop for uncut gems.

| Missing feature | Status | Notes |
|---|---|---|
| Make-All dialog | ✅ | Handles production dialog |
| Banking loop | ✅ | Restocks uncut gems from bank |
| Jewelry crafting mode | 🔲 | Gold bar + gem → ring/necklace at furnace |
| Crushed gem detection | 🔲 | Semi-precious gems can fail → drop crushed gem |
| Chisel tool check | 🔲 | Verify chisel in inventory; withdraw from bank if missing |
| Leather crafting mode | 🔲 | Needle + thread + leather |
| Gem type progression | 🔲 | Auto-switch gem type at level thresholds |
| Enchanting integration | 🔲 | Optionally cast enchant after crafting jewelry |

---

## Priority Order for Next Implementation

1. ✅ **Banking loop** (all scripts)
2. ✅ **Combat: prayer + prayer potions**
3. ✅ **Combat: loot pickup**
4. ✅ **Make-X / Make-All dialog** (Cooking, Smithing, Fletching, Crafting)
5. ✅ **Shift-drop** (Fishing, Mining)
6. ✅ **World hopping on competition** (Mining, Woodcutting)
7. ✅ **Combat: special attack**
8. ✅ **Configurable alchemy item**
9. ✅ **Bird nests** (Woodcutting)
10. ✅ **Progressive leveling** (woodcutting, mining, fishing)
11. ✅ **Blast Furnace** (Smithing — conveyor belt, bar dispenser, coal bag)
12. ✅ **Cannon support** (Combat — place, refill by threshold, GE restock)
13. ✅ **ABC2 antiban** (account seed + fatigue curve)
14. ✅ **Emergency logout** (Safety section in config)
15. ✅ **Sand Crabs position reset** (Combat — sandCrabsMode config toggle)
16. ✅ **1-tick fletching** (Fletching — darts + bolts auto-detected)
17. ✅ **Motherlode Mine** (Mining — hopper, sack, strut repair)
18. ✅ **Grand Exchange restocking** (GrandExchange API — open, buy, collect)
