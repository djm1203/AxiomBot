# Axiom

A RuneLite sideloaded plugin that provides an AFK scripting framework for skill automation in Old School RuneScape. Built as a learning exercise to understand how tools like TriBot, DreamBot, and RuneMate work under the hood.

**All testing is done offline / on a private server. This is a learning project, not a production tool.**

---

## What it is

Axiom has two components:

**axiom-plugin** — a RuneLite sideloaded plugin. Each script has its own configuration dialog (not RuneLite config strings) — you pick a script, click **Configure & Start**, fill out the settings, and click **Start Script**. The script runs its automation loop on every game tick (~600ms) until you stop it. Scripts include human-like randomization (Gaussian delays, break scheduling) via the Antiban system.

**axiom-launcher** — a standalone JavaFX multi-client launcher for managing accounts, proxies, and simultaneously running multiple RuneLite instances. Includes a CLI for headless / automated operation.

---

## Scripts

| Script | Skill | Notes |
|--------|-------|-------|
| **Woodcutting** | Woodcutting | Tree type, bank or power-drop, auto-mode progression |
| **Fishing** | Fishing | Configurable spot/action, bank or power-fish |
| **Firemaking** | Firemaking | Tinderbox + logs, walks a fire line |
| **Alchemy** | Magic | High or low alch, configurable item |
| **Herblore** | Herblore | Clean herbs or mix potions |
| **Fletching** | Fletching | Knife+logs or string+bows, banking |
| **Crafting** | Crafting | Gem cutting, banking |
| **Mining** | Mining | Rock type, bank or power-drop |
| **Smithing** | Smithing | Anvil smithing, bar type + item selection |
| **Combat** | Combat | Target NPC by name, eat food on HP threshold, loot drops |
| **Thieving** | Thieving | Steal from stall or pickpocket NPC |
| **Cooking** | Cooking | Range or fire cooking, Make-All dialog support |

Agility is deferred — obstacle sequencing per course is more complex and will be added later.

---

## Architecture

```
axiom/
├── axiom-api/                  ← pure interface layer, no RuneLite imports
├── axiom-plugin/               ← RuneLite sideloaded plugin + all implementations
│   └── target/axiom-plugin-1.0-SNAPSHOT.jar   ← fat JAR (shade plugin)
├── axiom-scripts/              ← one Maven module per script
│   ├── axiom-woodcutting/
│   ├── axiom-fishing/
│   ├── axiom-firemaking/
│   ├── axiom-alchemy/
│   ├── axiom-herblore/
│   ├── axiom-fletching/
│   ├── axiom-crafting/
│   ├── axiom-mining/
│   ├── axiom-smithing/
│   ├── axiom-combat/
│   ├── axiom-thieving/
│   └── axiom-cooking/
└── axiom-launcher/             ← standalone JavaFX multi-client launcher
    └── target/axiom-launcher-1.0-SNAPSHOT.jar  ← fat JAR (shade + optional ProGuard)
```

**Dependency rules:**
- `axiom-api` — pure Java, zero RuneLite imports
- `axiom-plugin` — implements all interfaces; RuneLite as `provided`; shades everything
- `axiom-scripts/axiom-*` — depend on `axiom-api` only; shaded into the plugin JAR
- `axiom-launcher` — standalone; no RuneLite dependency; bundles JavaFX + SQLite + JNA

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
com.axiom.plugin/           AxiomPlugin, ScriptRunner, ScriptLoader, LauncherBridge
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

**Requirements:** Java 11+, Maven 3.x, RuneLite built from source with `--developer-mode`

```bash
# Build plugin and deploy to RuneLite sideloaded-plugins (debug build)
mvn package -DskipTests -q
cp "axiom-plugin/target/axiom-plugin-1.0-SNAPSHOT.jar" \
   "$USERPROFILE/.runelite/sideloaded-plugins/axiom-plugin-1.0-SNAPSHOT.jar"

# Build launcher fat JAR (debug)
cd axiom-launcher
mvn package -DskipTests -q

# Build obfuscated release JARs for both plugin and launcher
mvn clean package -Prelease -DskipTests
# Output: axiom-plugin/target/axiom-plugin-*-release.jar
#         axiom-launcher/target/axiom-launcher-*-release.jar
# Mapping: target/proguard-mapping.txt  (store safely — never commit)
```

---

## Running

```bash
# Launch RuneLite from local source build (required for --developer-mode)
java -ea -jar "C:\Users\dmart\...\client-1.12.24-SNAPSHOT-shaded.jar" --developer-mode
```

RuneLite reads credentials from `~/.runelite/credentials.properties` (populated by the Jagex launcher on first login with `RUNELITE_ARGS=--insecure-write-credentials` set).

The Axiom panel appears as the ⚡ icon in the RuneLite toolbar.

---

## Launcher

The launcher is a standalone JavaFX application that manages multiple RuneLite instances.

**GUI** (`java -jar axiom-launcher.jar`):
- Sessions view — live table of running clients with runtime + status
- Accounts view — add/edit/delete OSRS accounts; CSV import with validation
- Proxies view — add/edit/delete proxies; socket latency test
- Scripts view — tile grid of available scripts; click to launch
- Settings view — RuneLite JAR path, scripts directory

**CLI** (`java -jar axiom-launcher.jar <subcommand>`):
```
axiom run        --account <name> --script <name> [--world N] [--proxy <name>]
axiom bulk-launch --file accounts.csv [--delay-ms 3000]
axiom accounts   list | import --file accounts.csv | export --file out.csv
axiom proxies    list | import --file proxies.csv  | export --file out.csv
```

**Security:**
- Bank PINs and proxy passwords encrypted at rest with AES-256-GCM
- Master key protected by Windows DPAPI (`%APPDATA%\axiom\master.key`, ~200 bytes)
- Non-Windows: plain key file with POSIX 600 permissions
- Log files and data directories restricted to the current OS user (ACL on Windows)
- No credentials appear in log output at INFO level or above

**Auto-start via launcher:** When a client is launched with `-Daxiom.script=<name>`, `AxiomPlugin` subscribes to `GameStateChanged` and automatically starts the named script on first `LOGGED_IN`, using the script's `getDefaultSettings()`.

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
