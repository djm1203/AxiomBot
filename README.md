# Axiom

A RuneLite sideloaded plugin that provides an AFK scripting framework for skill automation in Old School RuneScape. Built as a learning exercise to understand how tools like TriBot, DreamBot, and RuneMate work under the hood.

**All testing is done offline / on a private server. This is a learning project, not a production tool.**

---

## What it is

Axiom adds an "⚡ AXIOM" side panel to RuneLite. Each script has its own configuration dialog (not RuneLite config strings) — you pick a script, click **Configure & Start**, fill out the settings, and click **Start Script**. The script then runs its automation loop on every game tick (~600ms) until you stop it.

Scripts include human-like randomization (Gaussian delays, break scheduling) and an antiban system configurable from the global settings panel.

---

## Scripts

| Script | Skill | Modes / Notable features |
|--------|-------|--------------------------|
| **Combat** | Combat | Target NPC by name, eat threshold, protective + offensive prayers, potion drinking, loot pickup, special attack, banking mode, sand crabs aggro reset, dwarf cannon (place/refill/GE restock), emergency logout |
| **Woodcutting** | Woodcutting | Tree name filter, power-drop or banking, bird nest pickup, world hop on competition, skill progression (e.g. "1:Oak,30:Willow") |
| **Mining** | Mining | Rock name filter, power-drop or banking, shift-drop, hop on competition, Motherlode Mine mode (veins, hopper, sack, broken strut repair), skill progression |
| **Fishing** | Fishing | Configurable action (Lure/Bait/Net/Cage/Harpoon), power-fish or banking, shift-drop, skill progression |
| **Cooking** | Cooking | Banking mode, fire/range detection, Make-All dialogue |
| **High Alchemy** | Magic | Configurable item ID (0 = auto-detect), banking mode, nature rune tracking |
| **Smithing** | Smithing | Standard anvil mode or Blast Furnace (conveyor belt, coal bag, bar dispenser), banking |
| **Fletching** | Fletching | Auto-detects mode (knife+logs, string+bows, darts, bolts), banking |
| **Crafting** | Crafting | Gem cutting with chisel, all uncut gem types, banking |

---

## Architecture

```
BotEnginePlugin              ← RuneLite @PluginDescriptor ("Axiom"), orange A toolbar icon
│
├── ui/
│   ├── AxiomPanel           ← PluginPanel: script list with icon badges, Configure & Start,
│   │                           Stop/Pause/Resume, live status bar (polls every 500ms)
│   ├── AxiomTheme           ← Colors (RuneLite ColorScheme), fonts, padding, icon colors
│   ├── AxiomButton          ← Styled JButton (PRIMARY/SECONDARY/DANGER/NEUTRAL variants)
│   ├── AxiomSectionPanel    ← Collapsible section panel with orange left-border accent
│   ├── ScriptSettings       ← Abstract base; Gson save/load to ~/.runelite/axiom/<name>.json
│   └── ScriptConfigDialog   ← Abstract JDialog (460×580, APPLICATION_MODAL); header badge,
│                               scrollable sections, Save Profile + Start Script footer
│
├── BotEngineConfig          ← Global settings: antiban (break interval/duration, mouse jitter),
│                               safety (emergency logout), debug overlay, GE restock
│
├── ScriptRunner             ← @Subscribe GameTick → calls script.onLoop() per tick
│   └── manages STOPPED / RUNNING / PAUSED / BREAKING state
│
├── BotScript (abstract)     ← Base class for all scripts
│   ├── inject(...)          ← ScriptRunner injects api/ and util/ deps before onStart()
│   ├── configure(config, settings)
│   ├── createConfigDialog() ← Returns the script's XxxConfigDialog
│   ├── onStart()
│   ├── onLoop()             ← called every ~600ms while RUNNING
│   └── onStop()
│
├── api/                     ← Game state + action wrappers (all inject Client internally)
│   ├── Players              ← location, animation, HP%, isIdle, isInCombat, nearbyCount,
│   │                           distanceTo, shouldEat
│   ├── Npcs                 ← nearest by name / IDs / predicate
│   ├── GameObjects          ← nearest by ID / predicate
│   ├── Inventory            ← contains, isFull, isEmpty, getSlot, getItems, count
│   ├── Bank                 ← isOpen, isNearBank, openNearest, depositAll, withdraw,
│   │                           contains, close
│   ├── Movement             ← walkTo, setRunning, distanceTo, logout
│   ├── Interaction          ← click(NPC/GameObject/action), clickInventoryItem, useItemOn,
│   │                           useItemOnItem, clickWidget, dropAll
│   ├── Magic                ← canAlch, alch
│   ├── Combat               ← attackNpc, eat, canSpec, activateSpec, isSpecActive, getSpecPercent
│   ├── Camera               ← rotateTo
│   ├── Prayers              ← isActive, activate, deactivate, deactivateAll,
│   │                           shouldDrinkPotion, hasPotion, drinkPotion
│   ├── GroundItems          ← nearestWithTile, exists
│   └── GrandExchange        ← isOpen, openNearest, findEmptySlot, clickSlot, clickBuy,
│                               clickSearchBox, clickFirstResult, setQuantity,
│                               setPriceAboveGuide, confirmOffer, collectAll,
│                               hasItemsToCollect, hasActiveOffer, isSearchOpen,
│                               isOfferSetupOpen
│
└── util/
    ├── Antiban              ← reactionDelay, randomDelay, shouldTakeBreak, startBreak,
    │                           isBreakOver, reset
    ├── Time                 ← sleep, randomSleep, ticksToMs
    ├── Log                  ← info/warn/error/debug with script context prefix
    ├── WorldHopper          ← hopToMembers, hopToWorld
    └── Progression          ← level-gated target string (e.g. "1:Oak,30:Willow")
```

Each script lives under `scripts/<skillname>/` and consists of three files:

```
XxxScript.java          ← extends BotScript, implements the loop logic
XxxSettings.java        ← extends ScriptSettings, holds the config fields
XxxConfigDialog.java    ← extends ScriptConfigDialog, builds the Swing UI
```

---

## Building

**Requirements:**
- Java 11+
- Maven 3.x
- RuneLite built from source (tag `1.12.23` or `1.12.24-SNAPSHOT`)

### Build commands

```bash
# Dev build (fast, no obfuscation)
mvn package -DskipTests

# Release build (ProGuard obfuscated, for distribution)
mvn package -Prelease -DskipTests
# Output: target/bot-engine-osrs-<version>-obfuscated.jar

# Run all unit tests
mvn test
```

Tests cover: ScriptRunner state machine, GameTick dispatch, error handling, break cycle, Woodcutting/Alchemy/Combat state machine transitions, Inventory logic, Antiban delay bounds, Log formatting, Time math, Progression level-gating.

---

## Deploying and running

### One-time setup: Jagex credentials on disk

The source-built client needs credentials from your Jagex account. Do this once:

1. Set the `RUNELITE_ARGS` environment variable:
   ```powershell
   [System.Environment]::SetEnvironmentVariable('RUNELITE_ARGS', '--insecure-write-credentials', 'User')
   ```
2. Restart the Jagex Launcher fully (close from system tray), then launch OSRS normally. RuneLite writes your session to `~/.runelite/credentials.properties`.
3. Verify: `cat "$HOME/.runelite/credentials.properties"` — should show `JX_SESSION_ID`, `JX_CHARACTER_ID`, `JX_DISPLAY_NAME`.

Re-run step 2 whenever the session expires.

### Deploy the plugin and launch (one-liner)

```bash
cp "C:/Users/dmart/Documents/Personal/scripts/bot_engine_osrs/target/bot-engine-osrs-1.0-SNAPSHOT.jar" "$HOME/.runelite/sideloaded-plugins/" && java -ea -jar "C:\Users\dmart\Documents\Personal\Github\runelite\runelite-client\build\libs\client-1.12.24-SNAPSHOT-shaded.jar" --developer-mode
```

Or as two separate steps:

```bash
# Deploy the plugin
cp target/bot-engine-osrs-1.0-SNAPSHOT.jar ~/.runelite/sideloaded-plugins/

# Launch RuneLite with developer mode
java -ea -jar "C:\Users\dmart\Documents\Personal\Github\runelite\runelite-client\build\libs\client-1.12.24-SNAPSHOT-shaded.jar" --developer-mode
```

The log should show:
```
Side-loading plugin ...bot-engine-osrs-1.0-SNAPSHOT.jar
Bot Engine ready
```

### After code changes

```bash
mvn clean package -DskipTests
cp target/bot-engine-osrs-1.0-SNAPSHOT.jar ~/.runelite/sideloaded-plugins/
# Full close + re-run the java command above
```

---

## New User Setup

Run `setup.ps1` from the project root in PowerShell — it installs prerequisites, builds the JAR, and drops it into the sideloaded-plugins folder.

**Prerequisite:** Log in to OSRS at least once via the Jagex Launcher before running the script. The source-built client reads credentials from `~/.runelite/credentials.properties`, which is only written after a real Jagex Launcher login with `RUNELITE_ARGS=--insecure-write-credentials` set (see the "One-time setup" section above).

---

## Infrastructure

The project ships with a full build and distribution pipeline, all free and open-source:

| Component | Details |
|---|---|
| **ProGuard release profile** | `mvn package -Prelease` → obfuscated JAR for distribution |
| **GitHub Actions CI** | `.github/workflows/ci.yml` — runs tests on every push |
| **GitHub Actions Release** | `.github/workflows/release.yml` — attaches obfuscated JAR to GitHub releases on tag push |
| **Auto-update check** | `AutoUpdater.java` — checks GitHub releases API on startup, logs when a new version is available |
| **Sentry crash reporting** | `AxiomSentry.java` — optional; set `AXIOM_SENTRY_DSN` env var to enable |

See [docs/roadmap.md](docs/roadmap.md) for the full plan including planned phases (distribution installer, telemetry, licensing).

---

## Using the panel

1. Click the orange **A** icon in the RuneLite toolbar to open the Axiom panel.
2. Click a script in the list to select it.
3. Click **Configure & Start** — the script's config dialog opens.
4. Fill in the settings (target, thresholds, modes). Click **Save Profile** to persist them.
5. Click **Start Script** — the dialog closes and the script starts.
6. Use **Pause/Resume** and **Stop** in the panel to control the running script.

The status bar at the bottom of the panel polls every 500ms and shows the current state (RUNNING / PAUSED / BREAKING / STOPPED) and elapsed runtime.

---

## Global configuration (BotEngineConfig)

| Setting | Default | Description |
|---------|---------|-------------|
| Break every (minutes) | 45 | Average time between antiban breaks |
| Break duration (minutes) | 7 | Average break length |
| Mouse jitter radius (px) | 3 | Gaussian offset added to click coordinates |
| Emergency logout | disabled | Log out immediately on low HP / unexpected state |
| Show debug overlay | false | Tile highlights, NPC boxes, player position, script state |
| GE restock enabled | false | Allow scripts to buy supplies from Grand Exchange |
| GE restock threshold | 50 | Quantity below which restocking triggers |
| GE restock quantity | 500 | How many to buy when restocking |

---

## Adding a new script

1. Create a package under `src/main/java/com/botengine/osrs/scripts/myskill/`.

2. **Settings** — extend `ScriptSettings`:
   ```java
   public class MySettings extends ScriptSettings {
       public String targetName = "Goblin";
       public int eatThreshold = 50;
   
       @Override
       public String getScriptName() { return "myscript"; }
   }
   ```

3. **Config dialog** — extend `ScriptConfigDialog`:
   ```java
   public class MyConfigDialog extends ScriptConfigDialog {
       private final MySettings settings;
   
       public MyConfigDialog(MySettings settings) {
           super("My Script", "M", new Color(0x4CAF50));
           this.settings = settings;
           buildSections();
       }
   
       @Override
       protected void buildSections() {
           AxiomSectionPanel section = new AxiomSectionPanel("Targets");
           section.addRow("Target NPC", targetField);
           addSection(section);
       }
   
       @Override
       protected void saveToSettings() { settings.targetName = targetField.getText(); }
   
       @Override
       protected void loadFromSettings() { targetField.setText(settings.targetName); }
   }
   ```

4. **Script** — extend `BotScript`:
   ```java
   public class MyScript extends BotScript {
   
       private MySettings settings;
   
       @Override
       public void configure(BotEngineConfig config, ScriptSettings s) {
           this.settings = (MySettings) s;
       }
   
       @Override
       public ScriptConfigDialog createConfigDialog() {
           return new MyConfigDialog((MySettings) getSettings());
       }
   
       @Override
       public void onStart() { log.info("Starting My Script"); }
   
       @Override
       public void onLoop() {
           // Called every ~600ms. Do one action and return.
           // Protected fields available: players, npcs, gameObjects, inventory,
           // bank, movement, interaction, magic, combat, camera, prayers,
           // groundItems, grandExchange, antiban, time, log, worldHopper
           if (players.isIdle()) {
               NPC target = npcs.nearest(settings.targetName);
               if (target != null) combat.attackNpc(target);
           }
       }
   
       @Override
       public void onStop() { log.info("Stopped"); }
   }
   ```

5. Register it in `AxiomPanel` — add a `Provider<MyScript>` constructor parameter and add it to the scripts list.

---

## Project structure

```
src/
├── main/java/com/botengine/osrs/
│   ├── BotEnginePlugin.java
│   ├── BotEngineConfig.java
│   ├── script/
│   │   ├── BotScript.java
│   │   ├── ScriptRunner.java
│   │   └── ScriptState.java
│   ├── ui/
│   │   ├── AxiomPanel.java
│   │   ├── AxiomTheme.java
│   │   ├── AxiomButton.java
│   │   ├── AxiomSectionPanel.java
│   │   ├── ScriptSettings.java
│   │   └── ScriptConfigDialog.java
│   ├── api/
│   │   ├── Players.java
│   │   ├── Npcs.java
│   │   ├── GameObjects.java
│   │   ├── Inventory.java
│   │   ├── Bank.java
│   │   ├── Movement.java
│   │   ├── Interaction.java
│   │   ├── Magic.java
│   │   ├── Combat.java
│   │   ├── Camera.java
│   │   ├── Prayers.java
│   │   ├── GroundItems.java
│   │   └── GrandExchange.java
│   ├── util/
│   │   ├── Antiban.java
│   │   ├── Time.java
│   │   ├── Log.java
│   │   ├── WorldHopper.java
│   │   └── Progression.java
│   └── scripts/
│       ├── combat/       (CombatScript, CombatSettings, CombatConfigDialog)
│       ├── woodcutting/  (WoodcuttingScript, WoodcuttingSettings, WoodcuttingConfigDialog)
│       ├── mining/       (MiningScript, MiningSettings, MiningConfigDialog)
│       ├── fishing/      (FishingScript, FishingSettings, FishingConfigDialog)
│       ├── cooking/      (CookingScript, CookingSettings, CookingConfigDialog)
│       ├── alchemy/      (AlchemyScript, AlchemySettings, AlchemyConfigDialog)
│       ├── smithing/     (SmithingScript, SmithingSettings, SmithingConfigDialog)
│       ├── fletching/    (FletchingScript, FletchingSettings, FletchingConfigDialog)
│       └── crafting/     (CraftingScript, CraftingSettings, CraftingConfigDialog)
└── test/java/com/botengine/osrs/
    ├── script/           (ScriptRunnerTest)
    ├── scripts/          (CombatScriptTest, WoodcuttingScriptTest, AlchemyScriptTest)
    └── util/             (AntibanTest, LogTest, TimeTest, ProgressionTest)
```

---

## Docs

- [docs/architecture.md](docs/architecture.md) — full system architecture, module structure, API reference
- [docs/roadmap.md](docs/roadmap.md) — phased build plan, Phase 1 through 5
- [docs/script-development.md](docs/script-development.md) — how to write a new script
- [docs/launcher.md](docs/launcher.md) — standalone launcher architecture (Phase 3)
- [docs/industry-landscape.md](docs/industry-landscape.md) — TRiBot/PowBot/DreamBot reference
- [docs/testing-guide.md](docs/testing-guide.md) — deploy, test, validate
- [docs/packaging.md](docs/packaging.md) — Windows .exe installer plan

---

## Learning goals

- RuneLite plugin lifecycle (`startUp`, `shutDown`, EventBus, Guice DI)
- Game tick architecture and event-driven scripting
- State machine pattern for automation loops
- Human-like randomization (Gaussian distributions, break scheduling)
- RuneLite's menu action system (how every game interaction works internally)
- Custom Swing UI panels within a RuneLite plugin
- Writing testable Java with JUnit 5 + Mockito against a provided-scope library
