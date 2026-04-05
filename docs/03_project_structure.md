# Project Structure — Bot Engine OSRS
> Status: Reference document | Last updated: 2026-04-05

---

## Approach: Standalone Maven Project (Option B)

Our bot engine is a **separate Maven project** that declares RuneLite as a
dependency. It does NOT live inside the RuneLite source tree.

```
Personal/scripts/bot_engine_osrs/    ← OUR PROJECT (this folder)
    pom.xml                          ← Maven build, declares runelite as dep
    src/main/java/...                ← all our source code
    src/test/java/...                ← all our tests
    docs/                            ← research + planning docs

Personal/Github/runelite/            ← RuneLite source (untouched)
                                        used only to run the client
```

RuneLite publishes its API to Maven Central. We pull it in as a `provided`
dependency (meaning RuneLite supplies those classes at runtime — we don't
bundle them in our jar).

---

## Java Package Root

```
com.botengine.osrs
```

All source lives under: `src/main/java/com/botengine/osrs/`
All tests live under:  `src/test/java/com/botengine/osrs/`

---

## Full Directory Layout

```
bot_engine_osrs/
│
├── pom.xml                          Maven build file. RuneLite + Lombok +
│                                    JUnit + Mockito dependencies.
│
├── docs/                            Research and planning docs (this folder)
│
└── src/
    ├── main/
    │   ├── java/com/botengine/osrs/
    │   │   │
    │   │   ├── BotEnginePlugin.java         Main plugin entry point.
    │   │   │                                @PluginDescriptor annotation.
    │   │   │                                Wires all components via @Inject.
    │   │   │                                Registers overlays + panel.
    │   │   │
    │   │   ├── BotEngineConfig.java         Top-level config interface.
    │   │   │                                Break interval, break duration,
    │   │   │                                antiban intensity, debug toggle.
    │   │   │
    │   │   ├── BotEnginePanel.java          Side panel (extends PluginPanel).
    │   │   │                                Script selector dropdown.
    │   │   │                                Start / Stop / Pause buttons.
    │   │   │
    │   │   ├── api/                         ── FRAMEWORK API LAYER ──────────
    │   │   │   │                            Scripts ONLY import from here.
    │   │   │   │                            No net.runelite.api imports
    │   │   │   │                            inside scripts/ ever.
    │   │   │   │
    │   │   │   ├── Players.java             Local player state helpers.
    │   │   │   ├── Npcs.java                NPC find/filter utilities.
    │   │   │   ├── GameObjects.java         GameObject find/filter utilities.
    │   │   │   ├── Inventory.java           Inventory queries + item actions.
    │   │   │   ├── Bank.java                Bank open/deposit/withdraw/close.
    │   │   │   ├── Movement.java            Walk, distance, run energy.
    │   │   │   ├── Interaction.java         Click object/NPC/tile/menu.
    │   │   │   ├── Magic.java               Cast spells, check runes, alch.
    │   │   │   └── Combat.java              Attack NPC, eat food, prayer.
    │   │   │
    │   │   ├── script/                      ── SCRIPT ENGINE ────────────────
    │   │   │   ├── BotScript.java           Abstract base. getName/onStart/
    │   │   │   │                            onLoop/onStop. Injected helpers.
    │   │   │   ├── ScriptRunner.java        Subscribes to GameTick. Calls
    │   │   │   │                            active script each tick.
    │   │   │   └── ScriptState.java         Enum: STOPPED/RUNNING/PAUSED/
    │   │   │                                BREAKING
    │   │   │
    │   │   ├── scripts/                     ── INDIVIDUAL SCRIPTS ───────────
    │   │   │   ├── woodcutting/
    │   │   │   │   ├── WoodcuttingScript.java
    │   │   │   │   └── WoodcuttingConfig.java
    │   │   │   ├── fishing/
    │   │   │   │   ├── FishingScript.java
    │   │   │   │   └── FishingConfig.java
    │   │   │   ├── mining/
    │   │   │   │   ├── MiningScript.java
    │   │   │   │   └── MiningConfig.java
    │   │   │   ├── combat/
    │   │   │   │   ├── CombatScript.java
    │   │   │   │   └── CombatConfig.java
    │   │   │   ├── cooking/
    │   │   │   │   ├── CookingScript.java
    │   │   │   │   └── CookingConfig.java
    │   │   │   ├── alchemy/
    │   │   │   │   ├── AlchemyScript.java
    │   │   │   │   └── AlchemyConfig.java
    │   │   │   ├── smithing/
    │   │   │   │   ├── SmithingScript.java
    │   │   │   │   └── SmithingConfig.java
    │   │   │   ├── crafting/
    │   │   │   │   ├── CraftingScript.java
    │   │   │   │   └── CraftingConfig.java
    │   │   │   └── fletching/
    │   │   │       ├── FletchingScript.java
    │   │   │       └── FletchingConfig.java
    │   │   │
    │   │   ├── util/                        ── UTILITIES ────────────────────
    │   │   │   ├── Antiban.java             Delays, jitter, break scheduler.
    │   │   │   ├── Time.java                Sleep, tick math, timers.
    │   │   │   └── Log.java                 Unified logging wrapper.
    │   │   │
    │   │   └── overlay/                     ── OVERLAYS ─────────────────────
    │   │       ├── BotOverlay.java          Script name, state, XP/hr, time.
    │   │       └── DebugOverlay.java        Tile/NPC/click debug highlights.
    │   │
    │   └── resources/
    │       └── com/botengine/osrs/          Plugin icon if desired later.
    │
    └── test/
        └── java/com/botengine/osrs/
            ├── api/
            │   ├── PlayersTest.java
            │   ├── NpcsTest.java
            │   ├── GameObjectsTest.java
            │   ├── InventoryTest.java
            │   ├── BankTest.java
            │   └── MovementTest.java
            ├── script/
            │   └── ScriptRunnerTest.java
            ├── scripts/
            │   ├── woodcutting/WoodcuttingScriptTest.java
            │   ├── fishing/FishingScriptTest.java
            │   ├── mining/MiningScriptTest.java
            │   ├── combat/CombatScriptTest.java
            │   ├── cooking/CookingScriptTest.java
            │   └── alchemy/AlchemyScriptTest.java
            └── util/
                ├── AntibanTest.java
                └── TimeTest.java
```

---

## Data Flow

```
GameTick fires (every 600ms)
        │
        ▼
ScriptRunner.onGameTick()
        │
        ├── STOPPED / PAUSED  →  do nothing
        ├── BREAKING          →  check if break over → resume
        │
        └── RUNNING
                │
                ├── Antiban.check()   → maybe inject delay or break
                │
                └── activeScript.onLoop()
                        │
                  [Script queries via api/]
                  Players.isIdle()?
                  GameObjects.nearest("Oak tree")?
                  Inventory.isFull()?
                        │
                  [Script acts via api/]
                  Interaction.click(tree, "Chop down")
                  Movement.walkTo(bankTile)
                  Bank.depositAll()
```

---

## Design Rules

1. **Scripts never import `net.runelite.api` directly** — only `api/` classes do.
2. **One active script at a time** — ScriptRunner holds a single BotScript.
3. **All timing goes through `Antiban` or `Time`** — no raw Thread.sleep() in scripts.
4. **Each script is a state machine** — no blocking loops inside onLoop().
5. **Config per script** — each script has its own @ConfigGroup interface.

---

## Build Commands

```bash
# From bot_engine_osrs/ directory:

# Compile
mvn compile

# Run all tests
mvn test

# Run tests for one class
mvn test -Dtest=WoodcuttingScriptTest

# Package into jar
mvn package

# Skip tests during package
mvn package -DskipTests
```

---

## Loading Into RuneLite (Dev Mode)

Once built, RuneLite loads external plugins via developer mode:

```bash
# From the RuneLite repo:
./gradlew :client:run --args="--developer-mode --side-loading-path=C:/Users/dmart/Documents/Personal/scripts/bot_engine_osrs/target"
```

Or place the built jar in RuneLite's external plugin folder and enable
developer mode in RuneLite settings.
