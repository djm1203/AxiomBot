# OSRS Scripting Framework — Architecture Research
> Status: Living document | Last updated: 2026-04-05

---

## Table of Contents
1. [Language & Runtime](#1-language--runtime)
2. [The OSRS Client Landscape (2026)](#2-the-osrs-client-landscape-2026)
3. [RuneLite Plugin Architecture](#3-runelite-plugin-architecture)
4. [How Bot Clients Work — The Three Approaches](#4-how-bot-clients-work--the-three-approaches)
5. [Game State Model](#5-game-state-model)
6. [The Game Tick System](#6-the-game-tick-system)
7. [Input Simulation](#7-input-simulation)
8. [Script API Design Patterns](#8-script-api-design-patterns)
9. [Private & Offline Servers](#9-private--offline-servers)
10. [What Tasks Are Worth Scripting](#10-what-tasks-are-worth-scripting)
11. [Our Architecture Direction](#11-our-architecture-direction)

---

## 1. Language & Runtime

### Everything is Java (was)

OSRS was written in **Java**, compiled to JVM bytecode (`.class` files), and distributed as a `.jar` or applet.
This is why every bot client, RuneLite, DreamBot, and RuneMate are all also written in **Java** — they live in the same process, on the same JVM.

> **Important note (2026):** Jagex officially deprecated the Java client on July 24, 2024 and shut it down January 28, 2026.
> The official client is now C++. **However**, RuneLite is still actively maintained as a full replacement client
> written in Java — it reimplements everything the official client did. For our purposes, RuneLite is our entry point.

### Why the JVM matters

The JVM is the key that unlocks everything:

```
+--------------------------------------------------+
|                   JVM Process                    |
|                                                  |
|  +----------------+     +--------------------+   |
|  |  OSRS Gamepack |     |   RuneLite Client  |   |
|  |  (obfuscated   | <-- |   (open source,    |   |
|  |   bytecode)    |     |    hooks gamepack) |   |
|  +----------------+     +--------------------+   |
|                                                  |
|  Classes are loaded into shared memory           |
|  Reflection can inspect any loaded class         |
|  Bytecode can be modified before a class loads   |
+--------------------------------------------------+
```

**Key JVM capabilities exploited by bot clients:**
- `java.lang.reflect` — inspect and call private fields/methods at runtime
- Java agents / `Instrumentation` API — transform bytecode as classes load
- `java.awt.Robot` — inject mouse and keyboard events into the OS
- Shared classloader — RuneLite and the gamepack share a class namespace

---

## 2. The OSRS Client Landscape (2026)

```
OSRS Client Ecosystem
─────────────────────

Official Jagex Client (C++)
  └── Modern, anti-cheat, not accessible to Java tooling

RuneLite (Java, Open Source)
  ├── Hooks into gamepack via bytecode weaving at startup
  ├── Exposes clean API (runelite-api interfaces)
  ├── Plugin system (event-driven, @Subscribe)
  └── Our target platform ✓

DreamBot (Java, Commercial)
  ├── Injection-based bot client
  ├── Loop-driven script API (onLoop)
  └── Adapting post-Java client deprecation

RuneMate (Java, Commercial)
  ├── Cloud-backed reflection approach
  ├── Loop-driven script API
  └── More detection-resistant than injection

RSBot / PowerBot (Java, Defunct)
  └── Shut down October 2020 via legal action
```

---

## 3. RuneLite Plugin Architecture

### Big Picture

RuneLite's architecture has three distinct layers:

```
┌──────────────────────────────────────────────────────────────┐
│                    RUNELITE LAYERS                           │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  LAYER 3: Plugin Layer                               │   │
│  │  Your code lives here. Plugins extend Plugin class,  │   │
│  │  subscribe to events, call Client API.               │   │
│  │  Examples: XpTracker, TileIndicators, YourPlugin    │   │
│  └──────────────────────────────────────────────────────┘   │
│                           ▲ calls                            │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  LAYER 2: runelite-api (Interfaces)                  │   │
│  │  Clean Java interfaces: Client, Player, NPC,         │   │
│  │  GameObject, Widget, Skill, WorldPoint, etc.         │   │
│  │  No implementation here — just contracts.            │   │
│  └──────────────────────────────────────────────────────┘   │
│                           ▲ implemented by                   │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  LAYER 1: runelite-mixins + injector                 │   │
│  │  Bytecode injected INTO the obfuscated gamepack.     │   │
│  │  Adds getters/setters that expose internal state.    │   │
│  │  You never touch this layer directly.                │   │
│  └──────────────────────────────────────────────────────┘   │
│                           ▲ wraps                            │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  LAYER 0: Obfuscated OSRS Gamepack                   │   │
│  │  Jagex's compiled Java code. Field names are         │   │
│  │  gibberish (a, b, c...). RuneLite team maps them.    │   │
│  └──────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────┘
```

### The EventBus

The EventBus is RuneLite's central nervous system. Instead of plugins calling each other directly, they communicate by posting and subscribing to events.

```
Game loop fires → RuneLite detects state change → Posts Event to EventBus
                                                          │
                         ┌────────────────────────────────┤
                         ▼                ▼               ▼
                    Plugin A         Plugin B         Plugin C
                 @Subscribe       @Subscribe       @Subscribe
                 onNpcSpawned     onNpcSpawned     onAnimChanged
```

**Common events you'll use:**
| Event | When it fires |
|---|---|
| `GameTick` | Every 600ms server tick |
| `NpcSpawned` / `NpcDespawned` | NPC appears/disappears from scene |
| `AnimationChanged` | A player or NPC changes animation |
| `GameStateChanged` | Login, loading, hopping worlds |
| `ChatMessage` | Any chat message received |
| `ItemContainerChanged` | Inventory/bank/equipment changes |
| `StatChanged` | XP gain in any skill |
| `MenuOptionClicked` | Any right-click menu action |

### A Minimal Plugin

```java
@PluginDescriptor(name = "My AFK Plugin")
public class MyAfkPlugin extends Plugin {

    @Inject
    private Client client;          // The main game state object

    @Inject
    private EventBus eventBus;

    @Override
    protected void startUp() {
        // Called when plugin is enabled
    }

    @Override
    protected void shutDown() {
        // Called when plugin is disabled
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        // Fires every 600ms — core of most AFK logic
        Player local = client.getLocalPlayer();
        if (local.getAnimation() == -1) {
            // Player is idle — do something
        }
    }

    @Subscribe
    public void onStatChanged(StatChanged event) {
        // Fires when any skill gains XP
        if (event.getSkill() == Skill.WOODCUTTING) {
            log.info("Chopped a log! XP: " + event.getXp());
        }
    }
}
```

### Dependency Injection (Guice)

RuneLite uses **Google Guice** for dependency injection. You don't `new` your dependencies — you `@Inject` them and RuneLite provides them:

```java
@Inject private Client client;           // Game state
@Inject private ClientThread clientThread; // Run code on game thread
@Inject private OverlayManager overlayManager; // Register overlays
@Inject private ItemManager itemManager;  // Item name/price lookups
```

---

## 4. How Bot Clients Work — The Three Approaches

### Approach A: Bytecode Injection (RSBot era, DreamBot)

```
At startup:
  1. Decompile / analyze the OSRS .jar
  2. Map obfuscated field names → meaningful names (a.x → playerX)
  3. Inject hooks directly into game bytecode
  4. Run the modified client

At runtime:
  Modified game code calls your bot's hooks automatically
  Bot reads injected data, sends actions back
```

**Pro:** Very fast, direct access to all game state  
**Con:** Detectable (client code is different from vanilla), legally risky

### Approach B: Reflection (RuneMate, older PowerBot)

```
At startup:
  1. Load OSRS client normally (unmodified)
  2. Use Java reflection to find game classes by structure
     (look for a class with fields that match "player x/y coordinates")
  3. Build a field map: meaningfulName → obfuscatedField reference

At runtime:
  Read game state by calling Field.get() on obfuscated fields
  No code injection — just observation via reflection
```

**Pro:** Doesn't modify the client — harder to detect via code analysis  
**Con:** Slower, field mapping breaks every OSRS update

### Approach C: RuneLite Plugin API (Our Approach)

```
At startup:
  RuneLite's injector adds hooks to the gamepack once,
  mapping to clean interfaces. This is pre-built and maintained
  by the RuneLite team.

At runtime:
  Your plugin calls client.getLocalPlayer() etc.
  RuneLite's injected hooks return the real values from the game.
  You never touch obfuscated code.
```

**Pro:** Clean, maintainable, update-proof (RuneLite team handles mapping), open source  
**Con:** Limited to what RuneLite exposes in the API — some things are harder to access

### Comparison

```
                  Injection    Reflection    RuneLite Plugin
─────────────────────────────────────────────────────────────
Modifies client?     YES           NO              NO (RL does it)
Detectable?          High          Medium          Low (it IS the client)
Maintenance burden?  High          High            Low
API quality?         Custom        Custom          Excellent (open source)
Our choice?          No            No              YES ✓
```

---

## 5. Game State Model

The `Client` object is the root of all game state. Everything flows from it.

```
Client
├── getLocalPlayer() → Player
│   ├── getWorldLocation() → WorldPoint (x, y, plane)
│   ├── getAnimation() → int (animation ID, -1 = idle)
│   ├── getGraphic() → int (gfx/spell effect ID)
│   ├── getHealthRatio() → int
│   ├── getInteracting() → Actor (who they're fighting)
│   └── getName() → String
│
├── getNpcs() → List<NPC>
│   └── NPC extends Actor
│       ├── getId() → int (NPC type ID)
│       ├── getName() → String
│       ├── getWorldLocation() → WorldPoint
│       ├── getAnimation() → int
│       └── getNpcComposition() → NpcComposition (defs)
│
├── getPlayers() → List<Player>
│
├── getScene() → Scene
│   └── getTiles() → Tile[][][]  (104x104x4 planes)
│       └── Tile
│           ├── getGameObjects() → List<GameObject>
│           ├── getGroundItems() → List<TileItem>
│           └── getWallObject(), getDecorativeObject()
│
├── getWidget(group, child) → Widget  (UI elements)
│
├── getItemContainer(InventoryID) → ItemContainer
│   └── getItems() → Item[]
│       └── Item: getId(), getQuantity()
│
├── getRealSkillLevel(Skill) → int
├── getSkillExperience(Skill) → int
├── getBoostedSkillLevel(Skill) → int
│
├── getGameState() → GameState (LOGIN_SCREEN, LOGGED_IN, etc.)
├── getCameraX/Y/Z() → int
└── getBaseX/Y() → int  (world coords of scene origin)
```

### Coordinate Systems

```
WORLD COORDINATES (WorldPoint)
  Global, never changes.
  Example: Lumbridge is around (3222, 3218, 0)
  Format: WorldPoint(x, y, plane)
  Plane 0 = ground floor, 1 = upstairs, etc.

LOCAL COORDINATES (LocalPoint)
  Relative to the current scene (what's loaded around you).
  1 tile = 128 local units.
  Changes as you move (scene reloads).
  Used for rendering, canvas math.

CANVAS COORDINATES (Point)
  Pixel position on screen.
  Used for clicking — you need LocalPoint → canvas Point.
  Perspective.localToCanvas(client, localPoint, height)
```

### Key ID Systems

OSRS uses integer IDs for everything. The OSRS Wiki documents them all.

```
Animation IDs:  Player idle=808, walk=819, woodcut=879, mine=624...
NPC IDs:        Goblin=66, Cow=81, Chicken=41, Giant Spider=61...
Item IDs:       Coins=995, Logs=1511, Bronze axe=1351...
Object IDs:     Oak tree=1751, Bank booth=10083, Furnace=2030...
Widget IDs:     Inventory=9764864, Prayer=35454976...
```

---

## 6. The Game Tick System

### Standard Tick: 600ms

```
GAME TICK TIMELINE
──────────────────
0ms      600ms     1200ms    1800ms
│         │         │         │
▼         ▼         ▼         ▼
[Tick 1] [Tick 2] [Tick 3] [Tick 4]
   │
   └── Server processes all queued actions
       Updates all entity positions
       Runs combat calculations
       Fires RuneLite GameTick event
```

**Critical implication:** If your script clicks something at 100ms into a tick, the action won't execute until the NEXT tick (500ms later). Timing matters.

### Subtick System: 50ms

Some mechanics run at 50ms resolution (20x per tick):
- Prayer drain calculations
- Combo eating windows (shark + karambwan in same tick)
- High-level PvM mechanics

For basic AFK scripts, you don't need subtick precision. It becomes important for:
- Prayer flicking
- Tick manipulation (fishing, cooking speed tricks)
- Precise PvM scripts

### Scripting Against the Tick

```
GOOD pattern — tick-based:
  @Subscribe
  public void onGameTick(GameTick tick) {
      if (shouldDoAction()) {
          doAction();  // Will execute next tick
      }
  }

BAD pattern — busy loop:
  while (true) {
      if (shouldDoAction()) doAction();
      Thread.sleep(50);  // This is how old bots worked
  }
```

RuneLite's `onGameTick` is the correct heartbeat for most logic.

---

## 7. Input Simulation

### How Clicks Work in RuneLite

```
Your code calls: menuEntry.click() or client.invokeMenuAction(...)
        │
        ▼
RuneLite posts a MenuOptionClicked event
        │
        ▼
The game processes the menu action (next tick)
```

For more raw control, you can simulate actual mouse events:

```java
// Move mouse to canvas coordinates
client.setMouseCanvasPosition(x, y);

// Or use java.awt.Robot for OS-level events
Robot robot = new Robot();
robot.mouseMove(screenX, screenY);
robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
```

### Human-Like Mouse Movement

Real bot detection cares about patterns, not individual actions. The key techniques:

```
LINEAR (obvious bot)          BEZIER CURVE (better)
Start ──────────── End        Start ~~~~~~~~~~~ End
                              (curved path, variable speed)

GAUSSIAN NOISE (best)
Start ~∿~∿~~~~~~~~~ End
(micro-wobbles, acceleration/deceleration, slight overshoot)
```

**Bezier curve formula for mouse paths:**
```
P(t) = (1-t)² × P0 + 2(1-t)t × P1 + t² × P2
  where:
    P0 = start position
    P1 = control point (random offset from midpoint)
    P2 = end position
    t  = 0.0 to 1.0 (progress along path)
```

**Timing randomization:**
```java
// Instead of fixed 600ms delays:
int delay = 600 + random.nextGaussian() * 80;  // ~600ms ± some variance
```

---

## 8. Script API Design Patterns

### Event-Driven (RuneLite style) — Reactive

```java
// The game notifies you → you respond
@Subscribe
public void onGameTick(GameTick event) { ... }

@Subscribe
public void onNpcSpawned(NpcSpawned event) { ... }
```

Best for: Overlays, trackers, alerts, UI enhancements

### Loop-Driven (DreamBot/RuneMate style) — Imperative

```java
// You check state constantly → you decide action
public int onLoop() {
    if (!isNearTree()) return walkToTree();
    if (!hasAxe())     return getAxeFromBank();
    if (inventoryFull()) return bankLogs();
    return chopTree();  // Default action
}
```

Best for: AFK scripts with sequential logic

### State Machine (cleanest for complex scripts)

```
States:          WALKING_TO_TREE → CHOPPING → BANKING → WALKING_TO_TREE
                       ▲                                        │
                       └────────────────────────────────────────┘

Each state has:
  - condition to enter it
  - action to perform
  - condition to exit to next state
```

```java
enum State { WALKING, CHOPPING, BANKING }
State current = State.WALKING;

@Subscribe
public void onGameTick(GameTick tick) {
    switch (current) {
        case WALKING:
            if (nearTree()) current = State.CHOPPING;
            else walkToTree();
            break;
        case CHOPPING:
            if (inventoryFull()) current = State.BANKING;
            else if (idle()) chopTree();
            break;
        case BANKING:
            if (inventoryEmpty()) current = State.WALKING;
            else bankLogs();
            break;
    }
}
```

This pattern is the cleanest, most extensible approach for AFK scripts.

---

## 9. Private & Offline Servers

### What is OpenRS2?

OpenRS2 is two things:
1. **Archive**: Downloads and stores every historical OSRS/RS3 cache with XTEA encryption keys
2. **Server**: Open-source server compatible with OSRS client build 550 (~2009 era)

### Cache Structure

```
OSRS Cache Layout
─────────────────
/cache/
  main_file_cache.dat2   ← All asset data (compressed, XTEA-encrypted per region)
  main_file_cache.idx0   ← Index: animations
  main_file_cache.idx1   ← Index: bases  
  main_file_cache.idx2   ← Index: configs (items, NPCs, objects, etc.)
  main_file_cache.idx3   ← Index: interfaces (widgets)
  main_file_cache.idx4   ← Index: maps/regions
  main_file_cache.idx5   ← Index: music
  main_file_cache.idx7   ← Index: models
  main_file_cache.idx8   ← Index: sprites
  main_file_cache.idx13  ← Index: varbits
  main_file_cache.idx17  ← Index: textures
```

The cache contains all NPC definitions, item definitions, object definitions, animations, models, textures, and map data. You can read it offline without connecting to any server.

### RuneLite on RSPS (Private Server)

Standard RuneLite hardcodes Jagex's RSA key. For RSPS:
- Use `RuneLitePlus-PrivateServerEdition` fork
- Or `CalvoG/Runelite-RSPS` — open source injector fork
- Configure RSA public modulus in settings → connect to local server

### For Pure Offline Cache Reading

You don't need a running server at all to:
- Read NPC/item/object definitions
- Load map data
- Parse animations
- Build a local index of all game content

Tools: `OpenRS2 cache library`, `rs-cache` (Rust), `OSRS-Cache-Reader` (various)

---

## 10. What Tasks Are Worth Scripting

### AFK Skilling (Easiest, Best for Learning)

```
Woodcutting
  WALK to trees → CLICK tree → WAIT (chopping animation) → repeat
  Check: animation ID 879 (or similar) = chopping
  Check: inventory full → WALK to bank → DEPOSIT → return

Mining
  Similar to woodcutting but different animation IDs
  Rock "depletes" (GameObject changes ID) → find new rock

Fishing
  Click fishing spot → wait → spot moves → find new spot

Cooking
  WALK to range/fire → USE logs on fire → WAIT → repeat
  Check: item count in inventory changes

Fletching / Crafting
  Use item on item → wait for animation to finish → repeat
  Fully click-based, no movement needed
```

### Combat / AFK Fighting (Intermediate)

```
Chickens / Cows / Slayer task
  FIND nearest NPC by ID → CLICK to attack → WAIT to die
  Check: player health → eat food if low
  Check: NPC dies → loot drops → pick up loot
  Check: inventory full → bank or drop
```

### Questing / Navigation (Advanced)

```
Requires:
  - Path finding (walk between waypoints)
  - NPC dialogue handling (Widgets)
  - Conditional branching (quest state tracking)
  - Map awareness (region IDs, WorldPoints)
```

### Recommended Learning Order

```
1. ──► Woodcutting AFK (no combat, no banking)
        Learn: animation IDs, GameObject IDs, onGameTick

2. ──► Woodcutting + Banking
        Learn: WorldPoint navigation, bank widget, item containers

3. ──► Combat AFK (chickens)
        Learn: NPC targeting, health tracking, loot detection

4. ──► Multi-skill loop (mine → smelt → smith)
        Learn: state machines, region awareness, condition chaining

5. ──► Custom overlay + AFK tracker
        Learn: rendering pipeline, overlay system, XP/hr calculation
```

---

## 11. Our Architecture Direction

Based on everything above, here's what we're building and why:

```
OUR STACK
─────────
Language:        Java 11+
Base client:     RuneLite (open source, maintained, clean API)
Script style:    Hybrid — GameTick event-driven + state machine pattern
Testing:         RuneLite connecting to OpenRS2 local server (offline)
Build system:    Maven (matches RuneLite's build system)

WHAT WE'RE NOT DOING
─────────────────────
✗ Bytecode injection into gamepack (not needed with RuneLite)
✗ Reflection-based game state reading (RuneLite handles this)
✗ External memory reading (way too complex, defeats learning purpose)
✗ Connecting to live Jagex servers (ToS, risk of bans)
```

### Our Framework Architecture (Target)

```
┌─────────────────────────────────────────────────────┐
│                  SCRIPT LAYER                        │
│  WoodcuttingScript, MiningScript, CombatScript...    │
│  Written against our clean API — simple, readable    │
└─────────────────────────────────────────────────────┘
           │ calls
┌─────────────────────────────────────────────────────┐
│               OUR FRAMEWORK API                      │
│  Players, NPCs, Objects, Inventory, Movement...      │
│  Hides RuneLite specifics behind clean interfaces    │
└─────────────────────────────────────────────────────┘
           │ wraps
┌─────────────────────────────────────────────────────┐
│              RUNELITE PLUGIN API                     │
│  Client, EventBus, GameTick, Widgets...              │
│  Open source, maintained, hooks into gamepack        │
└─────────────────────────────────────────────────────┘
           │ connects to
┌─────────────────────────────────────────────────────┐
│           OPENRS2 LOCAL SERVER (offline)             │
│  Fully local, no Jagex connection, safe testing      │
└─────────────────────────────────────────────────────┘
```

---

## Key Terminology Reference

| Term | Meaning |
|---|---|
| Gamepack | The obfuscated OSRS client `.jar` |
| Mixin | A class whose code gets injected into the gamepack at load time |
| Bytecode weaving | Modifying compiled `.class` files (not source) to add hooks |
| EventBus | RuneLite's publish/subscribe system for game events |
| Game tick | 600ms server cycle — fundamental unit of OSRS time |
| WorldPoint | Global tile coordinate `(x, y, plane)` |
| LocalPoint | Scene-relative coordinate (resets when scene reloads) |
| Widget | A UI element (button, text, inventory slot) |
| NPC Composition | The definition/stats data for an NPC type (from cache) |
| XTEA | Encryption algorithm used on OSRS map region data |
| RSA key | Used in client-server handshake; custom value needed for RSPS |
| Varbits | Game variables stored as bit fields — track quest/game state |

---

*Next: Set up the RuneLite development environment and build a Hello World plugin.*
