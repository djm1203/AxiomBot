# Bot Engine OSRS

A standalone RuneLite plugin that provides an AFK scripting framework for skill automation in Old School RuneScape. Built as a learning exercise to understand game scripting architecture — how tools like DreamBot and RuneMate work under the hood.

**All testing is done offline / on a private server. This is a learning project, not a production tool.**

---

## What it does

The plugin adds a side panel to RuneLite with a script selector and Start/Pause/Stop controls. Select a skill, press Start, and the bot runs that skill's automation loop on every game tick (~600ms) until you stop it.

Scripts use a state machine pattern (`FIND_TARGET → WORKING → DROPPING → FIND_TARGET`) and include human-like randomization (Gaussian delays, break scheduling, Bezier mouse paths).

---

## Scripts included

| Script | Skill | Behaviour |
|--------|-------|-----------|
| Woodcutting | Woodcutting | Power-chop (drops logs, never banks) |
| High Alchemy | Magic | Alches all non-rune items, 3-tick cooldown |
| Gem Cutting | Crafting | Chisel+gem → Make-All production dialogue |
| Fishing | Fishing | Power-fish (drops catch when full) |
| Mining | Mining | Power-mine (drops ore when full) |
| Cooking | Cooking | Use raw food on fire/range → Make-All |
| Combat | Combat | Attack nearest target, eat below 50% HP |
| Smithing | Smithing | Use bar on anvil → Make-All |
| Fletching | Fletching | Auto-detects knife+log or stringing mode |

---

## Architecture

```
BotEnginePlugin          ← RuneLite @PluginDescriptor, wires everything
│
├── BotEnginePanel       ← Swing UI (script selector, Start/Pause/Stop)
├── BotEngineConfig      ← RuneLite config (antiban settings, debug toggle)
├── BotOverlay           ← In-game HUD (script name, state, runtime)
├── DebugOverlay         ← Dev overlay (tile highlight, NPC boxes, position)
│
├── ScriptRunner         ← @Subscribe GameTick → calls script.onLoop()
│   └── manages STOPPED / RUNNING / PAUSED / BREAKING state
│
├── BotScript (abstract) ← Base class for all scripts
│   ├── inject(...)      ← ScriptRunner injects api/ and util/ deps before onStart()
│   ├── onStart()
│   ├── onLoop()         ← called every ~600ms while RUNNING
│   └── onStop()
│
├── api/                 ← Game state + action wrappers
│   ├── Players          ← local player location, animation, HP, combat
│   ├── Npcs             ← find nearest/all by ID or name, filter dead
│   ├── GameObjects      ← find nearest/all by ID or name
│   ├── Inventory        ← contains, count, slots, getItems()
│   ├── Bank             ← open, deposit, withdraw, close
│   ├── Movement         ← walkTo, run toggle, energy
│   ├── Interaction      ← all menuAction calls (click, use-on, drop)
│   ├── Magic            ← High Alch, rune check
│   └── Combat           ← attack, eat, prayers
│
└── util/
    ├── Time             ← sleep, sleepUntil, tick math, formatElapsed
    ├── Log              ← SLF4J wrapper with script-name prefix, varargs
    └── Antiban          ← Gaussian delays, break scheduling, Bezier mouse paths
```

### Key design decisions

**RuneLite Plugin API (not bytecode injection)**
We hook into RuneLite's official plugin API. This is the safest, most maintainable approach. Scripts subscribe to `GameTick` events via the EventBus and read game state through the official `Client` object.

**All interactions use `client.menuAction()`**
Every action in OSRS (click tree, attack NPC, drop item, cast spell) is a menu entry. `client.menuAction(p0, p1, MenuAction, id, itemId, option, target)` is the single correct way to trigger any action in RuneLite 1.12.23.

**Provider<Script> pattern for fresh instances**
Scripts are created via Guice `Provider<ScriptClass>` in the panel — each Start press creates a new script instance with clean state, rather than reusing a potentially dirty one.

**inject() not Guice inside scripts**
Scripts can't use Guice directly (they're created fresh by Provider, outside the injector). Instead, `ScriptRunner` calls `script.inject(client, api..., util...)` before `onStart()`. All api/ and util/ objects are available as `protected` fields on `BotScript`.

---

## Building

**Requirements:**
- Java 11+
- Maven 3.x
- RuneLite 1.12.23 (fetched automatically from `repo.runelite.net`)

```bash
# Compile and run all tests
mvn test

# Build the fat JAR (output: target/bot-engine-osrs-1.0-SNAPSHOT.jar)
mvn package
```

All 285 unit tests should pass. Tests cover:
- `util/` — Time math, Antiban delay bounds and break scheduling, Log formatting
- `api/` — Inventory slot/count logic, Interaction menuAction argument verification
- `script/` — ScriptRunner state machine, GameTick dispatch, error handling, break cycle
- `scripts/` — Woodcutting/Alchemy/Combat state machine transitions

---

## Loading into RuneLite (for testing)

See [docs/testing-guide.md](docs/testing-guide.md) for the step-by-step process.

Short version: RuneLite's developer mode lets you load external plugin JARs via `--developer-mode` and the Plugin Hub's "Load from file" option. You need RuneLite built from source (tag `1.12.23`).

---

## Configuration

Once loaded, the plugin adds a "Bot Engine" section in RuneLite's plugin config panel:

| Setting | Default | Description |
|---------|---------|-------------|
| Break every (minutes) | 45 | Average time between antiban breaks |
| Break duration (minutes) | 7 | Average break length |
| Mouse jitter radius (px) | 3 | Random pixel offset on clicks |
| Enable antiban | true | Toggle all break/delay randomization |
| Show debug overlay | false | Tile highlights, NPC boxes, state info |
| Verbose logging | false | DEBUG level output in RuneLite console |

---

## Project structure

```
src/
├── main/java/com/botengine/osrs/
│   ├── BotEnginePlugin.java
│   ├── BotEngineConfig.java
│   ├── BotEnginePanel.java
│   ├── api/
│   ├── overlay/
│   ├── script/
│   ├── scripts/
│   │   ├── alchemy/
│   │   ├── combat/
│   │   ├── cooking/
│   │   ├── crafting/
│   │   ├── fishing/
│   │   ├── fletching/
│   │   ├── mining/
│   │   ├── smithing/
│   │   └── woodcutting/
│   └── util/
└── test/java/com/botengine/osrs/
    ├── api/
    ├── script/
    ├── scripts/
    └── util/
```

---

## Writing a new script

```java
package com.botengine.osrs.scripts.myskill;

import com.botengine.osrs.script.BotScript;
import javax.inject.Inject;

public class MyScript extends BotScript {

    @Inject
    public MyScript() {}

    @Override
    public String getName() { return "My Skill"; }

    @Override
    public void onStart() {
        log.info("Starting");
    }

    @Override
    public void onLoop() {
        // Called every ~600ms while running.
        // Use api/ fields: players, npcs, gameObjects, inventory, interaction, etc.
        // Use util/ fields: log, time, antiban

        if (players.isIdle()) {
            // do something
        }
    }

    @Override
    public void onStop() {
        log.info("Stopped");
    }
}
```

Then register it in `BotEnginePanel` constructor with a `Provider<MyScript>` parameter and add it to the `scripts` map.

---

## Learning goals

This project covers:
- RuneLite's plugin lifecycle (`startUp`, `shutDown`, EventBus, Guice DI)
- Game tick architecture and event-driven scripting
- State machine pattern for automation loops
- Human-like randomization (Gaussian distributions, Bezier curves)
- RuneLite's menu action system (how every game interaction works)
- Writing testable Java with JUnit 5 + Mockito against a provided-scope library
