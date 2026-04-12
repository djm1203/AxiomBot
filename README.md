# Axiom

A RuneLite sideloaded plugin that provides an AFK scripting framework for skill automation in Old School RuneScape. Built as a learning exercise to understand how tools like TriBot, DreamBot, and RuneMate work under the hood.

**All testing is done offline / on a private server. This is a learning project, not a production tool.**

---

## What it is

Axiom adds an "⚡ AXIOM" side panel to RuneLite. Each script has its own configuration dialog (not RuneLite config strings) — you pick a script, click **Configure & Start**, fill out the settings, and click **Start Script**. The script then runs its automation loop on every game tick (~600ms) until you stop it.

Scripts include human-like randomization (Gaussian delays, break scheduling) via the Antiban system.

---

## Scripts

| Script | Skill | Notes |
|--------|-------|-------|
| **Woodcutting** | Woodcutting | Tree type, bank or power-drop |
| **Fishing** | Fishing | Configurable spot/action, bank or power-fish |
| **Firemaking** | Firemaking | Tinderbox + logs, walks a fire line |
| **Alchemy** | Magic | High or low alch, configurable item |
| **Herblore** | Herblore | Clean herbs or mix potions |
| **Fletching** | Fletching | Knife+logs or string+bows, banking |
| **Crafting** | Crafting | Gem cutting, banking |
| **Mining** | Mining | Rock type, bank or power-drop |
| **Smithing** | Smithing | Anvil smithing, bar type + item selection |
| **Combat** | Combat | Target NPC by name, eat food on HP threshold, loot drops |
| **Thieving** | Thieving | Steal from stall or pickpocket NPC; inventory change detection |

Agility is deferred — obstacle sequencing per course is more complex and will be added later.

---

## Architecture

```
axiom/                              ← parent POM (com.axiom, 1.0-SNAPSHOT)
├── axiom-api/                      ← pure interface layer, no RuneLite imports
├── axiom-plugin/                   ← RuneLite sideloaded plugin + all implementations
│   └── target/axiom-plugin-1.0-SNAPSHOT.jar   ← fat JAR (shade plugin bundles everything)
└── axiom-scripts/                  ← one Maven module per script
    ├── axiom-woodcutting/
    ├── axiom-fishing/
    ├── axiom-firemaking/
    ├── axiom-alchemy/
    ├── axiom-herblore/
    ├── axiom-fletching/
    ├── axiom-crafting/
    ├── axiom-mining/
    ├── axiom-smithing/
    ├── axiom-combat/
    └── axiom-thieving/
```

Dependency rules:
- `axiom-api` — pure Java, no RuneLite imports
- `axiom-plugin` — implements all interfaces; depends on `axiom-api` + RuneLite as `provided`; shades everything into one fat JAR
- each `axiom-scripts/axiom-*` — depends on `axiom-api` only; shaded into the plugin JAR at build time

**API packages:**
```
com.axiom.api.game/     Players, Npcs, GameObjects, GroundItems, SceneObject, Widgets
com.axiom.api.player/   Inventory, Equipment, Skills, Prayer
com.axiom.api.world/    Bank, Movement, Camera, WorldHopper
com.axiom.api.script/   BotScript, ScriptManifest, ScriptCategory, ScriptSettings
com.axiom.api.util/     Antiban, Log
```

**Plugin packages:**
```
com.axiom.plugin/           AxiomPlugin, ScriptRunner, ScriptLoader
com.axiom.plugin.impl.*     Implementations of all axiom-api interfaces
com.axiom.plugin.ui/        AxiomPanel, ScriptConfigDialog, AxiomTheme, per-script dialogs
```

Each script consists of exactly three files:
```
XxxScript.java          ← extends BotScript + @ScriptManifest
XxxSettings.java        ← extends ScriptSettings
XxxConfigDialog.java    ← extends ScriptConfigDialog<XxxSettings>  (lives in axiom-plugin)
```

---

## Building

**Requirements:**
- Java 11+
- Maven 3.x
- RuneLite built from source with `--developer-mode`

```bash
# Build and deploy (one command)
cd "C:/Users/dmart/Documents/Personal/scripts/bot_engine_osrs"
mvn package -DskipTests -q && cp "axiom-plugin/target/axiom-plugin-1.0-SNAPSHOT.jar" "$USERPROFILE/.runelite/sideloaded-plugins/axiom-plugin-1.0-SNAPSHOT.jar"
```

---

## Running

```bash
# Launch RuneLite from local source build (required for --developer-mode)
java -ea -jar "C:\Users\dmart\Documents\Personal\Github\runelite\runelite-client\build\libs\client-1.12.24-SNAPSHOT-shaded.jar" --developer-mode
```

RuneLite reads credentials from `~/.runelite/credentials.properties` (populated by the Jagex launcher on first login with `RUNELITE_ARGS=--insecure-write-credentials` set).

The Axiom panel appears as the ⚡ icon in the RuneLite toolbar.

---

## How ScriptRunner works

`ScriptRunner` subscribes to `GameTick` events. On every tick (~600ms):
1. Check `antiban.shouldTakeBreak()` → pause if due
2. Honor any `setTickDelay(n)` the script set on the previous tick
3. Call `script.onLoop()`
4. Catch and count consecutive errors — stop after 5

Before `onStart()` fires, `ScriptRunner.injectApis()` injects all API implementations into the script's declared fields by name (reflection). Scripts just declare the fields; no `@Inject` or wiring needed:

```java
public class MyScript extends BotScript {
    private Players   players;    // injected automatically
    private Inventory inventory;  // injected automatically
    private Antiban   antiban;    // injected automatically
    private Log       log;        // injected automatically
    // etc.
}
```

---

## Using the panel

1. Click the ⚡ icon in the RuneLite toolbar.
2. Click a script in the list.
3. Click **Configure & Start** → fill in settings → **Start Script**.
4. Use **Pause/Resume** and **Stop** to control the running script.

---

## Docs

- [docs/architecture.md](docs/architecture.md) — full system architecture and module dependency rules
- [docs/roadmap.md](docs/roadmap.md) — phased build plan (launcher, distribution, licensing)
- [docs/script-development.md](docs/script-development.md) — how to write a new script
- [docs/launcher.md](docs/launcher.md) — standalone launcher architecture (Phase 3, not yet built)
- [docs/testing-guide.md](docs/testing-guide.md) — deploy, test, validate
- [docs/packaging.md](docs/packaging.md) — Windows installer plan
