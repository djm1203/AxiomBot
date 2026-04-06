# Testing Guide

How to load and test the Bot Engine plugin in RuneLite.

---

## Prerequisites

- RuneLite source cloned to `Personal/Github/runelite/` (Gradle-based, tag `1.12.23`)
- Java 11+ on your PATH
- Maven 3.x on your PATH
- Bot Engine JAR built: `mvn clean package` in the project root

The fat JAR will be at:
```
target/bot-engine-osrs-1.0-SNAPSHOT.jar
```

---

## Running the client with developer mode

Developer mode is required for sideloaded plugins to load. The Jagex Launcher blocks developer mode by setting a system property, so you must run the source-built client directly.

### One-time setup: get Jagex credentials on disk

The source-built client needs credentials from your logged-in Jagex account. Do this once:

1. **Set the `RUNELITE_ARGS` environment variable permanently:**
   ```powershell
   [System.Environment]::SetEnvironmentVariable('RUNELITE_ARGS', '--insecure-write-credentials', 'User')
   ```

2. **Restart the Jagex Launcher** (close fully from system tray), then launch OSRS normally. RuneLite will write your session to `~/.runelite/credentials.properties`.

3. **Verify the file was created:**
   ```powershell
   cat "$HOME/.runelite/credentials.properties"
   ```
   Should show `JX_SESSION_ID`, `JX_CHARACTER_ID`, `JX_DISPLAY_NAME`.

You only need to do this once. The credentials.properties file persists between sessions (re-run step 2 when the session expires and you get login errors).

### Deploying the plugin

Copy the JAR to the sideloaded-plugins directory (RuneLite scans this on startup):
```bash
cp target/bot-engine-osrs-1.0-SNAPSHOT.jar ~/.runelite/sideloaded-plugins/
```

### Launching the client

```bash
java -ea -jar "C:\Users\dmart\Documents\Personal\Github\runelite\runelite-client\build\libs\client-1.12.24-SNAPSHOT-shaded.jar" --developer-mode
```

The log should show:
```
Side-loading plugin ...bot-engine-osrs-1.0-SNAPSHOT.jar
Bot Engine ready
```

If you see `read N credentials from disk`, the Jagex session was loaded successfully and you should be logged in.

---

## After code changes

```bash
mvn clean package
cp target/bot-engine-osrs-1.0-SNAPSHOT.jar ~/.runelite/sideloaded-plugins/
# Restart the client (full close + re-run the java command)
```

Maven caches compiled classes — always use `mvn clean package` (not just `mvn package`) after editing source files to guarantee the new code is picked up.

---

## Enabling debug overlay

1. Plugin Config → Bot Engine → Debug → enable **"Show debug overlay"**
2. The overlay shows current script, state, player position, NPC count, tile/NPC bounding boxes

Use this to verify the script is finding targets — if NPC count is 0 when standing next to mobs, the NPC API is broken. If count > 0 but the script isn't attacking, the NPC ID filter is wrong.

---

## Reading logs

All bot output goes to the terminal where you launched the client (not the in-game chat). Look for lines prefixed with `BotEngine`:

```
[BotEngine] Starting script: Combat
[BotEngine] onLoop state=FIND_TARGET npcsInScene=4
[BotEngine] FIND_TARGET: nearest=Hill Giant/2099
[BotEngine] Attacking NPC id=2099 name=Hill Giant
```

If `onLoop` never appears after starting, the EventBus subscription failed.
If `FIND_TARGET: nearest=null` appears, the NPC ID is wrong or the mob is out of range.

---

## Testing each script

### Combat — Hill Giants (Edgeville dungeon)
- NPC ID: `2099`
- Stand next to the giants, have any food in inventory
- Select Combat → Start
- The script sends direct `menuAction` packets — **the cursor does not move**, this is correct behavior
- Verify: attack animation starts, HP bar appears on the giant

### Woodcutting
- Have an axe in inventory or equipped
- Stand next to any trees
- TREE_IDS in WoodcuttingScript: `1276` (regular), `1278` (oak), etc.
- Verify: chop animation, logs appear, inventory drops when full

### High Alchemy
- Inventory: nature runes + items to alch, fire runes or fire staff equipped
- Select Alchemy → Start
- Should cast every ~1.8 seconds

### Fishing
- Stand at fishing spots
- Verify: click on spot, fish accumulate, drop when full

### Mining
- Stand at ore rocks
- Verify: mine animation, ore accumulates, rock depletion detected (goes idle, finds new rock)

### Cooking, Smithing, Fletching, Gem Cutting
- These use the Make-All production dialogue
- Verify the dialogue opens and "Make All" is clicked

---

## Common issues

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| Plugin not in sidebar | JAR not in sideloaded-plugins or client not in dev mode | Re-copy JAR, re-run with `--developer-mode` |
| "read 0 credentials from disk" / login screen | credentials.properties missing or expired | Redo the one-time Jagex credentials setup above |
| Script starts, nothing happens, no logs | onLoop not being called | Verify EventBus.register(scriptRunner) in plugin startup log |
| `FIND_TARGET: nearest=null` in logs | NPC ID mismatch | Check actual NPC ID with NPC Indicators plugin, update array |
| Attack called but no animation | Wrong MenuAction type | Combat NPCs: NPC_FIRST_OPTION; some quest NPCs: NPC_SECOND_OPTION |
| Production dialogue doesn't open | Object/NPC not found | Enable debug overlay, check NPC count and targeting |
| NullPointerException in logs | Script auto-stopped | Check full stack trace in terminal for the failing line |

---

## Finding NPC and object IDs

The fastest method: install RuneLite's **NPC Indicators** plugin, right-click any NPC → "Tag" → hover the tag to see its ID.

For game objects: install **Object Markers** plugin, right-click any object → "Mark" to see the object ID.

Alternative: enable the debug overlay — it shows NPC IDs and tile info for everything near the player.

---

## Antiban during testing

With defaults, the first break fires after ~45 minutes. To test the break cycle sooner:
- Set "Break every" to 1 minute in config
- Watch for `[BotEngine] Taking antiban break` in logs
- Script should pause then auto-resume after the break duration
