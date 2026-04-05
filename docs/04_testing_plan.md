# Testing Plan — Bot Engine OSRS
> Status: Reference document | Last updated: 2026-04-05

---

## Overview

Testing a RuneLite plugin has three distinct layers because some things can be
tested in pure isolation (no game running) and others require a live client.

```
PYRAMID OF TESTING
──────────────────

        ▲
       /|\
      / | \        Manual / Live Client Testing
     /  |  \       (RuneLite dev mode, private server)
    /───────\
   /         \     Integration Tests
  /           \    (RuneLite's test harness, mock Client)
 /─────────────\
/               \  Unit Tests
/                \ (JUnit 5 + Mockito, pure Java, no game)
─────────────────

Most tests should be unit tests — fast, reliable, no game required.
```

---

## Layer 1 — Unit Tests

**Where:** `runelite-client/src/test/java/net/runelite/client/plugins/botengine/`

**Tools:** JUnit 5 (already in RuneLite's Gradle deps) + Mockito

**What gets unit tested:**

### api/ layer
Each API class wraps `Client` calls. We mock `Client` and verify behavior.

```java
// Example: GameObjects unit test
@Test
void nearest_returnsClosestObjectById() {
    // Arrange
    Client mockClient = mock(Client.class);
    // set up mock scene with two oak trees at known positions
    // ...

    // Act
    GameObject result = GameObjects.nearest(1751); // oak tree ID

    // Assert
    assertEquals(expectedClosestTree, result);
}
```

Classes to unit test:
- `Players` — animation check, idle detection, location
- `Npcs` — nearest by ID, nearest by name, distance filtering
- `GameObjects` — nearest by ID, nearest by name, null when none present
- `Inventory` — contains, count, isFull, isEmpty
- `Bank` — isOpen state detection
- `Movement` — distance calculations, isMoving detection

### util/ layer
Pure logic, easiest to test.

```java
// Example: Antiban timing distribution test
@Test
void gaussianDelay_staysWithinBounds() {
    Antiban antiban = new Antiban();
    for (int i = 0; i < 1000; i++) {
        long delay = antiban.gaussianDelay(600, 80);
        assertTrue(delay >= 300 && delay <= 1200,
            "Delay out of reasonable bounds: " + delay);
    }
}

@Test
void shouldTakeBreak_returnsTrueAfterBreakInterval() {
    Antiban antiban = new Antiban();
    antiban.setBreakInterval(30); // 30 minutes
    antiban.setSessionStart(Instant.now().minus(31, MINUTES));
    assertTrue(antiban.shouldTakeBreak());
}
```

Classes to unit test:
- `Antiban` — delay distribution, break scheduling, jitter bounds
- `Time` — ticksToMs, msToTicks, elapsed calculations

### script/ state machine
Test state transitions without running any game code.

```java
// Example: WoodcuttingScript state machine test
@Test
void stateTransition_inventoryFull_switchesToBanking() {
    WoodcuttingScript script = new WoodcuttingScript(mockDependencies);
    script.setState(WoodcuttingState.CHOPPING);

    when(mockInventory.isFull()).thenReturn(true);
    script.onLoop();

    assertEquals(WoodcuttingState.BANKING, script.getState());
}

@Test
void stateTransition_bankEmpty_switchesToWalking() {
    WoodcuttingScript script = new WoodcuttingScript(mockDependencies);
    script.setState(WoodcuttingState.BANKING);

    when(mockInventory.isEmpty()).thenReturn(true);
    when(mockBank.isOpen()).thenReturn(false);
    script.onLoop();

    assertEquals(WoodcuttingState.WALKING, script.getState());
}
```

State machines to unit test (for each script):
- All state transitions
- Edge cases (NPC not found, object not found, inventory edge cases)
- Error/recovery states

### ScriptRunner
Test the runner's dispatch logic.

```java
@Test
void runner_doesNotCallLoop_whenPaused() {
    BotScript mockScript = mock(BotScript.class);
    runner.setScript(mockScript);
    runner.setState(ScriptState.PAUSED);

    runner.onGameTick(new GameTick());

    verify(mockScript, never()).onLoop();
}

@Test
void runner_callsOnStop_whenScriptChanges() {
    BotScript oldScript = mock(BotScript.class);
    BotScript newScript = mock(BotScript.class);
    runner.setScript(oldScript);
    runner.setScript(newScript);

    verify(oldScript).onStop();
    verify(newScript).onStart();
}
```

---

## Layer 2 — Integration Tests (Mock Client)

**Where:** Same test directory, but using RuneLite's `MockClient` or a more
complete Mockito setup that mimics a real game session.

**What gets tested here:**
- Full script run-through with simulated game state changes
- Banking loop end-to-end (walk → open bank → deposit → withdraw → walk back)
- Combat loop (find NPC → attack → NPC dies → find next → eat food)

These are slower but catch issues that unit tests miss (e.g., interaction between
`Movement` + `GameObjects` + `ScriptRunner`).

**How to run:** `./gradlew :client:test --tests "*botengine*"`

---

## Layer 3 — Manual / Live Testing

**When:** After unit + integration tests pass. Final validation.

### Setup options (safest first):

**Option A — RuneLite developer mode (no server needed)**
```
./gradlew :client:run
```
Opens RuneLite in dev mode. You can enable your plugin from the plugin panel.
No account needed — test UI, overlays, plugin load/unload.

**Option B — RuneLite against a private server (full AFK testing)**
Use a RuneLite RSPS fork connecting to a local OpenRS2 server.
Lets you actually run scripts against a live game world with no ban risk.

**Option C — Live account (last resort, ban risk)**
Only after thorough testing on private server. Use a throwaway account.

### Manual test checklist per script:
```
□ Plugin enables without error
□ Plugin disables cleanly (no leftover state)
□ Script selector shows script in panel
□ Start button transitions to RUNNING state
□ Overlay appears on screen with correct info
□ Script performs first action correctly
□ Script transitions states correctly (e.g., chop → bank → chop)
□ Pause button halts action mid-session
□ Stop button cleans up and resets state
□ Antiban delays are visible (not instant clicking)
□ Script handles target-not-found gracefully (doesn't crash)
□ Script handles full inventory gracefully
□ Script handles empty inventory gracefully
□ XP/hr counter on overlay updates correctly
□ Debug overlay shows correct tile/object highlights
```

---

## Test File Structure

```
runelite-client/src/test/java/net/runelite/client/plugins/botengine/
│
├── api/
│   ├── PlayersTest.java
│   ├── NpcsTest.java
│   ├── GameObjectsTest.java
│   ├── InventoryTest.java
│   ├── BankTest.java
│   ├── MovementTest.java
│   └── InteractionTest.java
│
├── script/
│   └── ScriptRunnerTest.java
│
├── scripts/
│   ├── woodcutting/
│   │   └── WoodcuttingScriptTest.java
│   ├── fishing/
│   │   └── FishingScriptTest.java
│   ├── mining/
│   │   └── MiningScriptTest.java
│   ├── combat/
│   │   └── CombatScriptTest.java
│   ├── cooking/
│   │   └── CookingScriptTest.java
│   ├── alchemy/
│   │   └── AlchemyScriptTest.java
│   └── fletching/
│       └── FletchingScriptTest.java
│
└── util/
    ├── AntibanTest.java
    └── TimeTest.java
```

---

## Running Tests

```bash
# Run all botengine tests
./gradlew :client:test --tests "net.runelite.client.plugins.botengine.*"

# Run just API layer tests
./gradlew :client:test --tests "net.runelite.client.plugins.botengine.api.*"

# Run a single test class
./gradlew :client:test --tests "*.WoodcuttingScriptTest"

# Run with output (see System.out from tests)
./gradlew :client:test --tests "*.botengine.*" --info
```

---

## Dependencies Already in RuneLite's Gradle

No additions needed — these are already available:
- `junit-jupiter` (JUnit 5)
- `mockito-core`
- `mockito-junit-jupiter`

Check `libs.versions.toml` in the root for exact versions.
