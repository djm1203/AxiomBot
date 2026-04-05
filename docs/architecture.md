# Architecture Reference

Deep-dive into how the Bot Engine works internally. Useful when rebuilding from scratch.

---

## The game tick loop

OSRS runs on a 600ms server tick. Everything in the game — movement, animations, combat — advances one step per tick.

RuneLite fires a `GameTick` event on its EventBus every tick. `ScriptRunner` subscribes to this:

```java
@Subscribe
public void onGameTick(GameTick event) {
    // called every ~600ms
    if (state == RUNNING) {
        activeScript.onLoop();
    }
}
```

Scripts **must return quickly** from `onLoop()`. Sleeping inside `onLoop()` would block the entire RuneLite event thread. Instead, scripts check state and fire one action per tick, then return.

---

## How `client.menuAction()` works

Every interaction in OSRS is a menu entry — left-clicking a tree, right-clicking an NPC, clicking a spell. The game processes these as menu entries even if the menu is never shown.

RuneLite 1.12.23 exposes:
```java
client.menuAction(p0, p1, MenuAction action, int id, int itemId, String option, String target)
```

Parameters by interaction type:

| Interaction | p0 | p1 | action | id | itemId | option | target |
|-------------|----|----|--------|----|--------|--------|--------|
| Click tree | sceneX | sceneY | GAME_OBJECT_FIRST_OPTION | objectId | -1 | "Chop down" | "Tree" |
| Click NPC | 0 | 0 | NPC_FIRST_OPTION | npc.getIndex() | -1 | "Lure" | "Fishing spot" |
| Drop item | slot | INVENTORY_id | CC_OP | 7 | itemId | "Drop" | "" |
| Cast spell | -1 | widgetId | CC_OP | 1 | -1 | "Cast" | "" |
| Walk to tile | sceneX | sceneY | WALK | 0 | -1 | "Walk here" | "" |

Key: NPC interactions use `npc.getIndex()` (position in NPC table), not `npc.getId()` (the NPC type ID).

---

## State machine pattern

Every script is a state machine. `onLoop()` reads the current state and handles one case:

```
FIND_TREE → (click tree) → CHOPPING
CHOPPING  → (player idle) → FIND_TREE
CHOPPING  → (inventory full) → DROPPING
DROPPING  → (inventory empty) → FIND_TREE
```

Why state machines?
- Each `onLoop()` call is one tick — you can only do one thing
- State persists between ticks (stored as an instance field)
- Transitions are explicit and testable

Anti-pattern to avoid: branching on multiple conditions in a single `onLoop()`. If you need to check inventory AND find a target AND click it, those should be three separate states.

---

## Antiban system

### Delays
All delays are Gaussian (bell curve) rather than uniform random. This matches human reaction time distribution — most reactions cluster near the mean with occasional outliers.

```java
// 650ms ± 80ms, clamped to [260ms, 1300ms]
antiban.gaussianDelay(650, 80)

// 150ms ± 40ms reaction delay (simulates human "noticing" something)
antiban.reactionDelay()
```

### Break scheduling
The break interval itself is also Gaussian:
- Target: every 45 minutes (configurable)
- Variance: ±20% of the interval
- Minimum: 5 minutes (hard floor to prevent immediate breaks)

When `shouldTakeBreak()` returns true, ScriptRunner transitions to `BREAKING` state. The script doesn't run until `isBreakOver()` returns true, then resumes automatically.

### Mouse jitter
Click coordinates get a Gaussian offset:
```java
// Adds ±3px (configurable) to any click target
antiban.mouseJitter(point)
```

### Bezier mouse paths
For future use — generates a curved path between two screen points rather than a straight line. Control point is randomly offset from the midpoint.

---

## Dependency injection

RuneLite uses Google Guice for DI. The plugin root (`BotEnginePlugin`) is constructed by RuneLite's injector.

Scripts can't use Guice directly because they're created via `Provider.get()` (which re-constructs the object each time Start is pressed). Instead:

1. `BotEnginePanel` holds `Provider<WoodcuttingScript>` etc. (injected by Guice)
2. On Start, it calls `provider.get()` to create a fresh script instance
3. `ScriptRunner.start(script)` calls `script.inject(client, api..., util...)` 
4. Script's protected fields are set — it can now use `players.isIdle()`, `interaction.click(...)`, etc.

---

## Widget IDs

RuneLite uses packed widget IDs: `(interfaceId << 16) | componentId`

Helper: `WidgetUtil.packComponentId(interfaceId, componentId)`

Important IDs:
```java
// High Alchemy spell (Magic spellbook interface 218, component 48)
WidgetUtil.packComponentId(218, 48)

// Make-All button on production interface (interface 270, component 14)
WidgetUtil.packComponentId(270, 14)

// Bank interface: isOpen check = client.getWidget(12, 0)
// Deposit All button = client.getWidget(12, 42)
```

---

## Object/NPC identification

GameObjects don't have names on the object itself. You must query the definition:
```java
String name = client.getObjectDefinition(obj.getId()).getName();
// Returns ObjectComposition, not ObjectDefinition
```

NPC names come directly from the NPC:
```java
String name = npc.getName(); // may be null
```

`TileItem` (ground items) only exposes `getId()` and `getQuantity()`. To pick it up, you need the tile it's on:
```java
// From Tile.getGroundItems():
for (TileItem item : tile.getGroundItems()) {
    interaction.click(item, tile); // tile must be passed explicitly
}
```

---

## Testing strategy

Tests mock the RuneLite `Client` and api/ objects using Mockito. Scripts call their injected api/ mocks, and tests verify the mocks received the right calls.

Key patterns used:
```java
// Verify the right menuAction was fired
verify(client).menuAction(eq(10), eq(20), eq(MenuAction.GAME_OBJECT_FIRST_OPTION), ...);

// Build Item arrays BEFORE any when() chain (avoids nested stubbing issues)
Item[] items = makeItems(1511, 561);  // build first
when(container.getItems()).thenReturn(items);  // then stub

// Use LENIENT strictness when setUp() stubs are only needed by some tests
@MockitoSettings(strictness = Strictness.LENIENT)
```

---

## RuneLite plugin lifecycle

```
RuneLite starts
  → Guice injector creates BotEnginePlugin
  → BotEnginePlugin.startUp() called:
      antiban.setBreakInterval(config.breakInterval())
      eventBus.register(scriptRunner)      ← ScriptRunner now receives GameTick
      overlayManager.add(botOverlay)
      overlayManager.add(debugOverlay)     ← only if config.debugOverlay()
      clientToolbar.addNavigation(navButton)

User presses Start in panel:
  → provider.get() creates fresh script instance
  → scriptRunner.start(script)
      script.inject(...)                   ← sets all api/util fields
      script.onStart()
      antiban.reset()
      state = RUNNING

Every 600ms:
  → GameTick event fires
  → scriptRunner.onGameTick()
      antiban.shouldTakeBreak() ?
        yes → state = BREAKING, antiban.startBreak()
      state == RUNNING → script.onLoop()
      state == BREAKING → antiban.isBreakOver() → state = RUNNING

User presses Stop:
  → scriptRunner.stop()
      script.onStop()
      state = STOPPED

RuneLite shuts down:
  → BotEnginePlugin.shutDown()
      scriptRunner.stop()
      eventBus.unregister(scriptRunner)
      overlayManager.remove(...)
      clientToolbar.removeNavigation(navButton)
```
