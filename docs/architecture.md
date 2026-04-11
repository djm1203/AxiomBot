# Axiom — Architecture Overview
> Status: Active reference | Last updated: April 2026
> Replaces: docs/01_architecture_research.md, docs/03_project_structure.md

---

## What Axiom is

Axiom is a long-term OSRS bot platform built on RuneLite. It is structured as
three distinct products that are built in phases:

```
┌─────────────────────────────────────────────────────────┐
│                   AXIOM LAUNCHER                        │
│              Standalone desktop app (Java)              │
│                                                         │
│  • Account manager (SQLite, AES-encrypted credentials)  │
│  • Proxy manager                                        │
│  • Script library browser                               │
│  • Spawn/kill RuneLite instances per account            │
│  • Per-instance config: script, world, proxy, heap      │
│  • Session monitoring dashboard                         │
│  • CLI for headless/bulk launch (like TRiBot CLI)       │
└──────────────────────┬──────────────────────────────────┘
                       │ spawns instances via subprocess
                       │ passes --axiom-script etc. as args
                       ▼
┌─────────────────────────────────────────────────────────┐
│              RUNELITE + AXIOM PLUGIN                    │
│         Sideloaded plugin JAR, one per instance         │
│                                                         │
│  • Reads launch args from LauncherBridge                │
│  • ScriptLoader discovers available script JARs         │
│  • ScriptRunner manages the tick loop + state machine   │
│  • Exposes full API layer to scripts                    │
│  • AxiomPanel: status display + manual script launch    │
└──────────────────────┬──────────────────────────────────┘
                       │ loads at runtime via URLClassLoader
                       ▼
┌─────────────────────────────────────────────────────────┐
│                  SCRIPT JARs                            │
│    Separate Maven modules, loaded from disk at runtime  │
│                                                         │
│  ~/.axiom/scripts/axiom-woodcutting.jar                 │
│  ~/.axiom/scripts/axiom-mining.jar                      │
│  ~/.axiom/scripts/axiom-agility.jar                     │
│  (future: community scripts downloaded from repo)       │
└─────────────────────────────────────────────────────────┘
```

---

## Industry Context (April 2026)

Jagex shut down the Legacy Java Client on January 28, 2026. Every bot built on
Java injection (DreamBot, OSBot, RuneMate) lost its attack surface overnight.

The two surviving approaches:

**RuneLite plugin approach (Axiom, TRiBot Echo)**
- Run the bot engine as a RuneLite sideloaded plugin
- Access game state via RuneLite's clean injected API
- Appears as vanilla RuneLite to Jagex's detection
- TRiBot Echo uses this exact architecture

**Native client approach (PowBot)**
- Built in Rust, hooks into the official C++ OSRS client
- Completely independent of RuneLite or Java
- Supports mobile (Android) botting
- Scripted in Lua
- Different attack surface, not replicable without years of work

Axiom is correctly positioned on the RuneLite approach. This is the right
long-term architecture given the January 2026 client shutdown.

---

## Maven Module Structure

```
axiom/                          ← parent POM
├── axiom-api/                  ← shared contract layer
├── axiom-plugin/               ← RuneLite plugin engine
├── axiom-launcher/             ← standalone launcher app (Phase 3)
└── axiom-scripts/
    ├── axiom-woodcutting/
    ├── axiom-mining/
    ├── axiom-agility/
    └── ... (one module per script)
```

Dependency rules:
- `axiom-api` has NO RuneLite dependency — it's pure Java
- `axiom-plugin` depends on `axiom-api` + RuneLite as `provided`
- Each script module depends on `axiom-api` only (NOT `axiom-plugin`)
- `axiom-launcher` depends on nothing from the bot engine — it just spawns processes

---

## API Layer (`axiom-api`)

Scripts import ONLY from `com.axiom.api.*`. Never `net.runelite.api.*`.

```
com.axiom.api/
├── script/
│   ├── BotScript.java          abstract: getName/onStart/onLoop/onStop
│   ├── ScriptManifest.java     @annotation: name, version, category, author
│   ├── ScriptCategory.java     enum: SKILLING, COMBAT, MONEY_MAKING, UTILITY
│   └── ScriptSettings.java     base class for per-script config
├── game/
│   ├── Players.java            local player position, animation, idle detection
│   ├── Npcs.java               find by name/id, filter, closest
│   ├── GameObjects.java        find by name/id, filter, closest
│   ├── GroundItems.java        find by name/id, take action
│   └── Widgets.java            ★ dialog detection, make-all/make-x, widget clicks
├── player/
│   ├── Inventory.java          contains, count, full, empty, use-on, drop
│   ├── Equipment.java          slot equipped, item name/id
│   ├── Skills.java             getLevel, getXp, getXpToNextLevel
│   └── Prayer.java             isActive, activate, points remaining
├── world/
│   ├── Bank.java               isOpen, open nearest, deposit, withdraw, close
│   ├── Movement.java           walkTo, isMoving, distanceTo
│   ├── Camera.java             rotate toward tile/entity
│   └── WorldHopper.java        hop world, hop to low-pop
├── interaction/
│   ├── Interaction.java        click entity, menu action
│   └── Menu.java               explicit menu action by option + target string
└── util/
    ├── Antiban.java            tick delays, Gaussian random, break scheduling,
    │                           fatigue curve, ABC2 account seed
    ├── Pathfinder.java         ★ obstacle-aware walkTo (doors, stairs, gates)
    ├── Time.java               sleep, sleepUntil(condition, timeout), elapsed
    └── Log.java                info/warn/error with script name prefix
```

★ = critical new additions that fix previous script failures

### Widgets.java — why this exists

The old codebase had every script handle dialogs ad-hoc. This is why Fletching
and Cooking broke — different widget IDs, inconsistent click timing.

`Widgets.java` centralizes all dialog handling:
```java
Widgets.isMakeDialogOpen()          // detect make-all/make-x dialog
Widgets.clickMakeAll()              // click "Make All"
Widgets.clickMakeX(int quantity)    // click "Make X" + type quantity
Widgets.isDialogOpen(int groupId)   // generic widget group check
Widgets.clickDialogOption(String text)  // click option by text
```

### Pathfinder.java — why this exists

Agility and Thieving both require navigating through obstacles. Without
obstacle-aware pathfinding, scripts silently fail at any door/gate/staircase.

`Pathfinder.java` wraps RuneLite's collision map or the ShortestPath plugin:
```java
Pathfinder.walkTo(WorldPoint destination)   // handles obstacles automatically
Pathfinder.isReachable(WorldPoint point)    // check before walking
```

---

## Plugin Layer (`axiom-plugin`)

```
com.axiom.plugin/
├── AxiomPlugin.java        @PluginDescriptor entry, wires Guice, reads launcher args
├── ScriptRunner.java       tick loop, STOPPED/RUNNING/PAUSED/BREAKING state machine
├── ScriptLoader.java       discovers @ScriptManifest scripts from classpath + JARs
├── LauncherBridge.java     reads --axiom-script, --axiom-world, etc. from args
└── ui/
    ├── AxiomPanel.java     script list (from ScriptLoader), Start/Stop/Pause
    └── AxiomTheme.java     colors, fonts
```

### ScriptManifest — replaces hardcoded panel

Old approach (broken at scale):
```java
// AxiomPanel had 13 @Inject parameters, one per script
public AxiomPanel(CombatScript combat, WoodcuttingScript woodcutting, ...)
```

New approach:
```java
// Each script declares itself via annotation
@ScriptManifest(name = "Axiom Woodcutting", category = SKILLING, version = "1.0")
public class WoodcuttingScript extends BotScript { ... }

// ScriptLoader discovers all annotated classes — panel never hardcodes scripts
List<BotScript> scripts = ScriptLoader.discoverScripts();
```

Adding a new script = add the JAR to ~/.axiom/scripts/. Zero changes to the plugin.

### ScriptRunner state machine

```
STOPPED ──start()──► RUNNING ──shouldBreak()──► BREAKING ──breakOver()──► RUNNING
                        │                                                      │
                   onGameTick()                                           onGameTick()
                   calls onLoop()                                       (waits)
                        │
                   logout detected
                        ▼
                     PAUSED ──login detected──► RUNNING
```

---

## Script Layer (`axiom-scripts/*`)

Each script is a separate Maven module that produces a standalone JAR.

Script structure (per script):
```
axiom-woodcutting/
└── src/main/java/com/axiom/scripts/woodcutting/
    ├── WoodcuttingScript.java       extends BotScript, @ScriptManifest
    ├── WoodcuttingSettings.java     extends ScriptSettings
    └── WoodcuttingConfigDialog.java extends ScriptConfigDialog (in axiom-plugin)
```

### Script rules

1. NEVER import `net.runelite.api.*` — only `com.axiom.api.*`
2. No raw `Thread.sleep()` — use `Time.sleepUntil()`
3. Each script is a state machine — no blocking loops inside `onLoop()`
4. All timing goes through `Antiban` or `Time`
5. Target entities (trees, rocks, fish spots) defined as data (enums), not strings

### Agility — course-as-data pattern

Agility courses are data arrays, not logic. Adding a course = adding an array.

```java
public class AgilityObstacle {
    int[] objectIds;     // RuneLite IDs for this obstacle
    String action;       // "Walk-on", "Climb-over", etc.
    WorldPoint standHere; // where to stand to click it
    WorldPoint endsAt;   // where player ends up after completion
    int animationId;     // animation that plays during completion
}

// Gnome Stronghold course:
static final AgilityObstacle[] GNOME = {
    new AgilityObstacle(new int[]{23145}, "Walk-on",   point(2474,3436), point(2474,3430), 762),
    new AgilityObstacle(new int[]{23134}, "Climb-over", point(2476,3430), point(2476,3424), 762),
    // ...
};

// Engine iterates the array — same code runs every course
```

---

## Launcher (`axiom-launcher`) — Phase 3

Not built yet. Architecture is decided:

**Tech:** Java with JavaFX UI. SQLite (via JDBC) for local storage.

**Data model:**
```sql
accounts (id, display_name, jagex_session_id, jagex_character_id,
          legacy_username, legacy_password_encrypted, bank_pin_encrypted,
          preferred_world, proxy_id)

proxies  (id, name, host, port, username, password_encrypted)

sessions (id, account_id, script_name, started_at, ended_at,
          status, xp_gained, gp_gained)
```

**How it spawns instances:**
```bash
java -jar runelite-shaded.jar --developer-mode \
     -Daxiom.script="Axiom Woodcutting"        \
     -Daxiom.world=302                          \
     -Daxiom.account.id=<jagex_character_id>
```

`LauncherBridge.java` in the plugin reads these system properties on startup
and auto-starts the specified script.

**CLI target (like TRiBot CLI):**
```bash
axiom run --account "myaccount" --script "Axiom Woodcutting" --world 302
axiom bulk-launch --file accounts.csv
axiom accounts import --file accounts.csv
axiom proxies import --file proxies.csv
```

---

## Build Commands

```bash
# Build everything
mvn package -DskipTests

# Build release (obfuscated)
mvn package -Prelease -DskipTests

# Deploy plugin to RuneLite
cp axiom-plugin/target/axiom-plugin-*.jar ~/.runelite/sideloaded-plugins/

# Deploy a script JAR
cp axiom-scripts/axiom-woodcutting/target/axiom-woodcutting-*.jar ~/.axiom/scripts/

# Launch RuneLite in dev mode
java -ea -jar <runelite-shaded.jar> --developer-mode
```

---

## Deployment flow

```
mvn package
    → axiom-plugin.jar      → ~/.runelite/sideloaded-plugins/
    → axiom-woodcutting.jar → ~/.axiom/scripts/
    → (future) axiom-launcher.jar → installed as standalone app
```
