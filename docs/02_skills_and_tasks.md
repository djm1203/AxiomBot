# OSRS Skills & Tasks — Full Automation Breakdown
> Status: Living document | Last updated: 2026-04-05

---

## Complexity Key
- **Simple** — click something, wait, repeat. No movement or interfaces.
- **Moderate** — involves banking, interfaces, or moving targets.
- **Complex** — pathfinding, multiple locations, multi-step logic, dynamic state.

## Detection Risk Key
- **Low** — long AFK sessions, few clicks, natural-looking
- **Medium** — periodic patterns, banking loops, some interface interaction
- **High** — tick manipulation, consistent multi-step loops, rapid clicking
- **Very High** — pathfinding algorithms, minigames, precise timing

---

## PART 1 — ALL SKILLS

### 1. Woodcutting
**AFK Loop:** Click tree → chopping animation plays → tree depletes → find next tree → repeat

**What to monitor:**
- Animation ID: woodcutting animation active = still chopping
- Tree GameObject ID: changes when it depletes (tree stump object replaces it)
- Inventory: logs accumulate → bank when full

**Complexity:** Simple (standard) / Complex (tick methods)

**Special mechanics:**
- Redwood trees (lvl 90) have a 1/11 depletion chance — can AFK 5+ minutes per click
- 3-tick woodcutting: sync herb+tar timer with tree click = 2 rolls per 3 ticks (double XP)
- 2-tick woodcutting at Ape Atoll: position between teak trees with 2 rats attacking 2 ticks apart

**Detection risk:** Low (standard AFK) / Very High (tick manipulation)

**Good for learning:** Yes — simplest possible script. Single click target, static object, animation check.

---

### 2. Mining
**AFK Loop:** Click ore rock → mining animation → rock depletes → find next rock → repeat

**What to monitor:**
- Animation ID: mining animation
- Rock GameObject ID: changes on depletion
- Inventory: ores fill up → bank

**Complexity:** Simple (Motherlode Mine) / Moderate (standard)

**Special mechanics:**
- Motherlode Mine (upper level): veins deplete on 15–30s timers, highly AFK (~5 min sessions)
- Pay-dirt system at Motherlode: deposit at hopper, collect from sack
- No tick manipulation exists for mining

**Detection risk:** Low–Medium (long AFK sessions appear human-like)

**Good for learning:** Yes — nearly identical loop to woodcutting but adds hopper deposit as a variant.

---

### 3. Fishing
**AFK Loop:** Click fishing spot → animation plays → spot moves/depletes → find new spot → repeat

**What to monitor:**
- Animation ID: fishing animation
- Fishing spot NPC: moves every ~10 minutes
- Inventory: fish accumulate → bank

**Complexity:** Simple (standard) / Complex (2-tick)

**Special mechanics:**
- Spot is an NPC (not an object) — moves unpredictably
- Anglerfish (lvl 82+): very low depletion rate, 30+ minute AFK sessions possible
- 2-tick fishing at Piscarilius: requires 2 rats on 2-tick attack cycle — inhuman precision

**Detection risk:** Low–Medium (standard AFK) / Very High (2-tick)

**Good for learning:** Yes — introduces dynamic target tracking (spot moves = must re-find).

---

### 4. Firemaking
**AFK Loop:** Use tinderbox on logs → walk one tile east → repeat down a line

**What to monitor:**
- Player position: must have open tiles ahead
- Inventory: logs count
- Fires placed: tile becomes unusable for ~5–10 minutes

**Complexity:** Moderate (requires movement)

**Special mechanics:**
- Unlike most skills, firemaking is NOT wait-based — it's movement-based
- No true AFK; player walks continuously
- Fastest method is rapid clicking without pausing

**Detection risk:** Medium–High (repetitive walk pattern)

**Good for learning:** Introduces movement logic — walking in a line, tile awareness.

---

### 5. Cooking
**AFK Loop:** Click raw food → cooking interface appears → select quantity → wait → cooked food in inventory → repeat

**What to monitor:**
- Inventory: raw food count, cooked food accumulating
- Widget state: cooking interface (group 1743 for range)
- Range/fire GameObject: confirm it's there
- Animation ID: cooking animation

**Complexity:** Moderate (interface interaction)

**Special mechanics:**
- 1-tick karambwan cooking: hold spacebar to spam-confirm, click rapidly — fastest XP but inhuman
- Hosidius Kitchen range: lowest burn rate in game (post-diary)
- Burn chance decreases with level and cooking gauntlets

**Detection risk:** Medium (standard) / Very High (1-tick)

**Good for learning:** First introduction to widget/interface interaction in a loop.

---

### 6. Smithing
**AFK Loop (smelting):** Use ores on furnace → interface select → wait → bars → bank → repeat
**AFK Loop (smithing):** Use bars on anvil → interface select → wait → items → bank → repeat

**What to monitor:**
- Inventory: ore types + coal counts (coal: 2 for steel, 4 for mithril, 6 for addy, 8 for rune)
- Furnace GameObject
- Anvil GameObject
- Widget: item selection interface

**Complexity:** Moderate–Complex (two different objects, widget, banking)

**Special mechanics:**
- Smithing always takes exactly 5 ticks (3 seconds) per item regardless of type
- Blast Furnace: special location with conveyor belt mechanic — fastest bars but complex setup

**Detection risk:** High (multi-location, consistent pattern)

---

### 7. Crafting (Multiple Sub-types)

#### 7A. Gem Cutting
**Loop:** Use chisel on gem in inventory → cut gem → repeat
**Complexity:** Simple (inventory-only, no objects)
**Detection risk:** Low — no objects, no movement, pure inventory
**Good for learning:** Yes — easiest possible crafting loop.

#### 7B. Leather Crafting
**Loop:** Use needle+thread on leather → select item type → 3-tick wait → item created → repeat
**Complexity:** Simple (inventory-only)
**Special:** Every item is exactly 3 ticks — very regular timing
**Detection risk:** Medium

#### 7C. Glassblowing
**Loop:** Use molten glass on glassblowing pipe → select item → wait → repeat
**Complexity:** Simple (inventory-only)
**Detection risk:** Low (classic AFK method)

#### 7D. Pottery
**Loop:** Clay on potter's wheel → shape item → clay on oven → fire item → repeat
**Complexity:** Moderate (two sequential objects)
**Detection risk:** Medium

#### 7E. Jewelry (Furnace)
**Loop:** Use bar+mould on furnace → interface select → wait → jewelry → bank → repeat
**Complexity:** Moderate (furnace + widget)
**Detection risk:** Medium

---

### 8. Fletching (Multiple Sub-types)

#### 8A. Bow Cutting
**Loop:** Use knife on logs → 3-tick wait → unstrung bow → repeat
**Complexity:** Simple (inventory-only)
**Detection risk:** Low–Medium

#### 8B. Bow Stringing
**Loop:** Use bowstring on bow → 2-tick wait → strung bow → repeat
**Complexity:** Simple (inventory-only)
**Special:** 2-tick interval — faster than cutting
**Detection risk:** Low–Medium

#### 8C. Bolt Fletching
**Loop:** Use unfinished bolts on feathers → receive finished bolts → repeat
**Complexity:** Simple (inventory-only, stacks)
**Detection risk:** Low

**Good for learning:** Fletching introduces tick-timing awareness in a safe, no-object context.

---

### 9. Herblore
**AFK Loop (potion making):** Use herb on vial → unfinished potion → use secondary on potion → finished potion → repeat

**What to monitor:**
- Inventory: clean herbs, vials of water, secondaries
- Item counts: 14 of each ingredient per inventory
- Experience feedback: confirms action completed

**Complexity:** Moderate (two sequential item-on-item actions, multiple item types)

**Special mechanics:**
- Herb cleaning (separate step): click dirty herb → instant clean
- Amulet of Chemistry: 5% chance of 4-dose potion
- Some potions require unusual bases (blood vials, etc.)

**Detection risk:** Medium

---

### 10. Prayer
**AFK Loop (bone burying):** Click bone in inventory → buried → click next → repeat
**AFK Loop (altar):** Bank bones → walk to POH altar → use bone on altar → repeat

**What to monitor:**
- Inventory: bone count
- Altar state: incense burners lit (for 3.5x XP multiplier)
- POH/Gilded Altar: confirm in house
- Prayer XP: feedback per bone

**Complexity:** Simple (burying) / Moderate (altar + banking loop)

**Special mechanics:**
- Gilded Altar: 350% XP with both burners lit (3.5x multiplier)
- Chaos Altar (Wilderness): 350% XP + 50% chance bone not consumed = effectively 700% XP/bone
- Ectofuntus: 75% more XP but slower (uses bonemeal)

**Detection risk:** Low–Medium

---

### 11. Runecrafting
**AFK Loop:** Bank → teleport to altar area → navigate abyss → craft runes → teleport back → bank → repeat

**What to monitor:**
- Player location: inside abyss, at altar, at bank
- Obstacle state: which obstacle is available (mining, agility, etc.)
- Inventory: pure essence count, rune count
- Teleport state: glory amulet charges

**Complexity:** Complex (pathfinding, multiple locations, dynamic obstacles, teleportation)

**Special mechanics:**
- Abyss: outer ring dangerous (PKers), inner ring has rifts to each altar
- Obstacle success rate depends on agility/mining/strength level
- Glory teleport to Edgeville bank is fastest return route
- Can reach 50–60 trips/hour with optimized path

**Detection risk:** Very High (most detectable skilling method outside tick manipulation)

---

### 12. Agility
**AFK Loop:** Click obstacle → animation plays → move to next obstacle → click → complete lap → restart

**What to monitor:**
- Player position: current course waypoint
- Obstacle GameObjects: each has unique ID per course
- Animation state: landing/transition
- Lap completion

**Complexity:** Complex (dynamic obstacle sequences, position-dependent clicking)

**Special mechanics:**
- Marks of Grace: random rare drops on rooftop courses, can be collected
- Hallowed Sepulchre (lvl 62+): fastest XP, requires navigating moving platforms
- Stamina drain doesn't exist in OSRS agility (only run energy)

**Detection risk:** Very High

---

### 13. Thieving
**AFK Loop (pickpocket):** Click NPC → pick-pocket animation → item received → repeat immediately (can click before animation ends)

**What to monitor:**
- NPC target: still present and in range
- Stun state: failed pickpocket = 4-second stun
- Inventory: coins/items accumulating
- HP: NPC deals damage on failure

**Complexity:** Moderate–Complex (NPC movement, failure states, stun handling)

**Special mechanics:**
- Can click NPC again BEFORE pickpocket animation ends (when you hear the coin sound)
- Stall stealing: position so stall is between player and shop owner (blocks line of sight)
- Elves (lvl 85): highest value but high failure rate at lower thieving
- Master Farmers: high-value seeds, common target

**Detection risk:** High

---

### 14. Farming
**AFK Loop:** Plant seed → wait N hours → harvest → replant → repeat
(Multi-patch: teleport between 5+ patch locations)

**What to monitor:**
- Patch state: growth stages 1–5+ (read via GameObject ID changing)
- Growth timers: herbs every 40 min, trees every 160 min, hops every 10 min
- Disease state: diseased crops die if not cured
- Inventory: seeds, compost, harvest items
- Gardener NPC: pay for crop protection

**Complexity:** Complex (time-gated, multi-location, disease management)

**Special mechanics:**
- Growth happens in ticks — not real-time — all patches at same location advance simultaneously
- Ultracompost eliminates disease chance
- Farming contracts (Farming Guild): bonus points for growing specific crops

**Detection risk:** Very High (multi-location teleporting patterns are obvious)

---

### 15. Hunter
**AFK Loop:** Place box trap → wait → prey triggers trap → collect → reset trap → repeat (up to 5 traps)

**What to monitor:**
- Trap state: set / shaking (triggered) / empty (failed)
- Trap position: where each trap is placed
- Prey behavior: chinchompa approaching within 2-tile radius
- Inventory: chinchompas accumulating

**Complexity:** Complex (multi-trap, spawn mechanics, prey path prediction)

**Special mechanics:**
- Can have 1 trap at lvl 1, up to 5 at lvl 80
- Traps attempt capture every 3 ticks
- Instant trap reset: pick up + place in same tick = no down time

**Detection risk:** High

---

### 16–19. Combat (Attack / Strength / Defence / Ranged / Hitpoints)
**AFK Loop:** Click NPC → auto-retaliate enabled → weapon swings automatically → NPC dies → click next NPC → repeat

**What to monitor:**
- Combat state: currently in combat
- NPC health: when it dies, find next target
- Player health: eat food if HP drops below threshold
- Loot: items drop on death, click to pick up

**Complexity:** Simple (auto-retaliate) / Moderate (loot pickup, eating, prayer)

**Special mechanics:**
- Attack: determines accuracy (hit chance)
- Strength: determines max damage
- Defence: reduces NPC hit chance on you
- Weapon speed varies: daggers fastest (4-tick), 2h swords slowest
- Ranged with Ava's Device: ~70% arrow recovery automatically
- Dwarf Multicannon: fires 3 shots/sec passively — very fast XP but needs cannonballs

**Best AFK locations:**
- Sand Crabs / Ammonite Crabs: low HP, aggressive, re-aggro automatically
- Rock Crabs: same as above
- Nightmare Zone: controlled environment, absorption pots for true AFK

**Detection risk:** Very Low (auto-retaliate with no looting) / Medium (active looting + eating)

**Good for learning:** Combat is the best second script after woodcutting — introduces health checking and NPC re-targeting.

---

### 20. Magic
**AFK Loop (High Alchemy):** Click High Alch spell → click item in inventory → 5-tick wait → GP received → repeat

**What to monitor:**
- Spellbook widget: High Alch spell position
- Inventory item: target item position
- GP gained: feedback
- Rune count: nature runes consumed (1 per cast) + fire runes (5 per cast)

**Complexity:** Moderate (two widget clicks per action — spellbook + inventory)

**Special mechanics:**
- 5-tick (3 second) cast time — fixed
- Max 1,200 alchs/hour
- Can alch noted items (no need to un-note)
- Stack fire runes with fire staff (removes need to track fire rune count)

**AFK Loop (combat spells):** Same as melee combat but click spell + NPC
**AFK Loop (enchanting):** Use enchant spell on jewelry → 1-tick animation → enchanted item

**Detection risk:** Low–Medium (5-tick interval matches natural spell cooldown)

**Good for learning:** High alchemy is a pure timer-based loop — great for learning tick-based scheduling.

---

### 21. Slayer
**Loop:** Get task from master → navigate to monster → kill N of them → return → new task → repeat

**What to monitor:**
- Current task: monster type + kill count remaining
- Monster NPC IDs: varies per task (50+ monster types)
- Player health + gear: different tasks need different setups
- Loot drops
- Slayer points: currency from completions

**Complexity:** Complex (task variety, navigation, monster switching, loot)

**Detection risk:** Very High

---

### 22. Construction
**AFK Loop:** Right-click hotspot → Build → select furniture → animation → right-click hotspot → Remove → animation → repeat

**What to monitor:**
- Hotspot state: can build or can remove
- Widget: build interface (furniture selection)
- Inventory: planks + nails
- Butler: demon butler delivery of planks from bank

**Complexity:** Moderate–Complex (two actions per cycle, interface, butler coordination)

**Special mechanics:**
- Build + remove is the fastest cycle (no furniture kept)
- Mahogany tables: fastest XP (most expensive)
- Demon Butler fetches planks — enables third rotation while he's banking
- Press "1" after build to instantly remove

**Detection risk:** Medium–High

---

## PART 2 — NON-SKILL TASKS

### High Alchemy (Money Making)
Already covered under Magic — pure 5-tick inventory loop. One of the easiest scripts to write.

### Ground Item Collection (Loot Runs)
**Loop:** Walk to spawn location → pick up item → walk to next spawn → repeat → bank

**What to monitor:**
- TileItem (ground item): ID + position
- Item respawn timer: varies by world population
- Inventory: fill up → bank

**Complexity:** Moderate (pathfinding between spawn points)
**Detection risk:** High (consistent route is obvious)

### Banking Loops (Generic)
Most skilling scripts need this as a subroutine:
```
FIND bank → OPEN bank interface → DEPOSIT all → WITHDRAW items → CLOSE bank → return
```

**Widget IDs to know:**
- Bank widget: group 12
- Deposit all button
- Withdraw-X button

**Detection risk:** Medium (repeating pattern every inventory cycle)

### Nightmare Zone (AFK Combat)
- Buy absorption potions from Dominic
- Enable Rapid Heal prayer (or not)
- Start dream (rumble mode)
- Auto-retaliate does all the work
- Drink absorption pots when they drop

**Complexity:** Simple (once set up)
**Detection risk:** Medium (consistent boss kill cycle)

### Pest Control (Semi-AFK Combat Points)
- Board boat → defend portals → kill spinners → attack portal
- Activity threshold: must deal 5,000 damage or repair barricades
- **Freeloading**: repair gate for 500 points = minimal effort

**Complexity:** Moderate
**Detection risk:** Very High (minigame bots are heavily monitored)

---

## PART 3 — COMPLEXITY FACTOR REFERENCE

### Target Type Difficulty

```
EASIEST ──────────────────────────────────────────────── HARDEST

Inventory-only     Static object    Dynamic object    Moving NPC
(gem cutting,      (furnace,        (fishing spot,    (thieving,
 fletching,         anvil,           trees that        combat
 alchemy)           altar)           deplete)          targets)

No pathfinding → simple click → re-find target → full NPC tracking
```

### Banking Frequency Impact

```
No banking needed:
  Fishing, woodcutting, mining (inventory holds ~hours of items)
  Best AFK potential

Occasional banking (every 10–30 min):
  Cooking, fletching, herblore
  Repeating 2-phase loop: skill → bank → skill

Constant banking:
  Prayer (altar), smithing, construction
  High click rate, complex state machine needed
```

### Interface Interaction Required

| Skill | Interface Type | Notes |
|---|---|---|
| Cooking | Item selection widget | "How many would you like to cook?" |
| Smithing | Item selection widget | Choose item to smelt/smith |
| High Alchemy | Spellbook widget | Click spell then item |
| Banking | Bank interface | Deposit/withdraw widgets |
| Construction | Build interface | Select furniture from list |
| Slayer | NPC dialogue | Talk to master for task |

### Recommended Learning Order for Scripts

```
1. Woodcutting (no bank)
   Learn: animation ID check, GameObject detection, onGameTick loop

2. High Alchemy
   Learn: timer-based loop, widget clicking, spellbook interaction

3. Woodcutting + Banking
   Learn: WorldPoint navigation, bank widget, state machine

4. Combat (Sand Crabs)
   Learn: NPC targeting, health check, auto-eat, loot detection

5. Fishing
   Learn: dynamic target (spot moves), NPC vs GameObject

6. Cooking
   Learn: item-selection interface, use-item-on-object

7. Gem Cutting / Fletching
   Learn: inventory-only loops, item-on-item interaction

8. Smithing
   Learn: multi-object navigation, complex widget selection

9. Herblore
   Learn: sequential item-on-item, multi-ingredient management

10. Slayer / Runecrafting (advanced)
    Learn: pathfinding, task state, multi-location navigation
```

---

## PART 4 — DETECTION AWARENESS (Educational)

### How Jagex's BotWatch Works

```
DETECTION LAYERS
────────────────

Layer 1: Client Integrity
  Is the client modified? Packet mismatches? Injected code?
  → RuneLite plugin approach: client IS RuneLite, passes this check

Layer 2: Server-Side Action Validation
  Is this action physically possible right now?
  Can the player reach that tile? Do they have the runes?
  → Must validate game state before every action

Layer 3: Behavioral Analytics (the hard one)
  XP/hr compared to human distribution
  Click intervals — are they Gaussian or robotic?
  Session length and schedule patterns
  Mouse path analysis
  Camera movement patterns

Layer 4: ML Pattern Matching
  50+ data points analyzed simultaneously
  Account behavior vs legitimate player baseline
  Cross-skill consistency checks
```

### What Makes Scripts Look Like Bots

```
ROBOTIC BEHAVIOR              MORE HUMAN-LIKE
────────────────              ────────────────
Fixed 600ms intervals   →     600ms ± Gaussian noise (~80ms std dev)
Same pixel every click  →     Random offset within object bounds
Instant reaction        →     50–200ms "reaction time" delay
Never move camera       →     Occasional minor camera adjustments
Never take breaks       →     Random 5–30 min pauses every 1–2 hrs
Perfect success rate    →     Occasional intentional misclicks/delays
Always same route       →     Slight path variation
```

### Risk by Category

| Category | Risk Level | Why |
|---|---|---|
| Tick manipulation (2/3-tick) | Extreme | Inhuman precision |
| Runecrafting (abyss) | Very High | Complex pathfinding signature |
| Agility courses | Very High | Dynamic obstacle sequence |
| Minigames | Very High | Team coordination detection |
| Farming (multi-patch) | Very High | Multi-location teleport pattern |
| Combat at single spot | Low–Medium | Auto-retaliate is natural |
| Woodcutting (AFK trees) | Low | Long AFK sessions = human-like |
| High Alchemy | Low–Medium | Regular 5-tick cooldown is expected |
| Gem cutting | Low | Inventory-only, no objects, easy to randomize |

---

## PART 5 — WHAT WE'LL BUILD (Script Suite Plan)

Based on everything above, here's the recommended suite ordered by complexity for our framework:

### Tier 1 — Foundation Scripts (Build These First)
These test core framework systems with minimal complexity.

| Script | Tests | Complexity |
|---|---|---|
| Woodcutter | GameObject detection, animation check, tick loop | Simple |
| High Alcher | Widget clicking, timer logic, rune tracking | Simple |
| Gem Cutter | Inventory-only item-on-item loop | Simple |

### Tier 2 — Intermediate Scripts (After Framework Is Solid)
These add banking, interfaces, and movement.

| Script | Tests | Complexity |
|---|---|---|
| Woodcutter + Banker | Navigation, bank widget, state machine | Moderate |
| Fisher | Dynamic NPC target (moving spot), bank | Moderate |
| Cooker | Interface interaction, item-on-object | Moderate |
| Combat (Sand Crabs) | NPC targeting, health, loot | Moderate |

### Tier 3 — Advanced Scripts (Full Framework Validation)
These stress-test pathfinding, complex state, multi-location logic.

| Script | Tests | Complexity |
|---|---|---|
| Miner + Banker | Rock depletion, multiple rock fallback | Moderate+ |
| Smithing (smelt+smith) | Multi-object, complex widget | Complex |
| Herblore | Sequential item-on-item, ingredient tracking | Complex |
| Slayer | Task tracking, NPC variety, multi-location | Very Complex |

---

*Next: Environment setup — install Java, set up RuneLite dev environment, write first Hello World plugin.*
