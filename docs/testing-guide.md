# Testing Guide

How to load and test the Bot Engine plugin in RuneLite.

---

## Prerequisites

- RuneLite source cloned to `Personal/Github/runelite/` (Gradle-based, tag `1.12.23`)
- Java 11+ on your PATH
- Maven 3.x on your PATH
- Bot Engine JAR built: `mvn package` in the project root

The fat JAR will be at:
```
target/bot-engine-osrs-1.0-SNAPSHOT.jar
```

---

## Step 1 — Build RuneLite in developer mode

RuneLite must be launched with `--developer-mode` to allow loading external plugin JARs.

In `Personal/Github/runelite/`:
```bash
./gradlew :runelite-client:run --args="--developer-mode"
```

On Windows without a Unix shell:
```cmd
gradlew.bat :runelite-client:run --args="--developer-mode"
```

This launches the RuneLite client with all developer features enabled.

---

## Step 2 — Load the plugin JAR

With RuneLite running in developer mode:

1. Click the **wrench icon** (Plugin Hub) in the RuneLite sidebar
2. Click **"..."** menu at the top-right of the Plugin Hub panel
3. Select **"Load plugin from file"**
4. Navigate to and select `target/bot-engine-osrs-1.0-SNAPSHOT.jar`
5. The "Bot Engine" plugin will appear in the plugin list — enable it

If "Load plugin from file" is not visible, confirm that RuneLite launched with `--developer-mode`.

---

## Step 3 — Verify the plugin loaded

Once enabled:
- A **robot icon** should appear in the RuneLite navigation sidebar
- Click it to open the **Bot Engine panel** (script selector + Start/Pause/Stop buttons)
- Open RuneLite's config panel and search "Bot Engine" — antiban and debug settings should appear

If the panel doesn't appear, check the RuneLite console (View → Show Client Log) for errors.

---

## Step 4 — Enable debug overlay

Before testing any scripts:

1. Go to Plugin Config → Bot Engine → Debug → enable **"Show debug overlay"**
2. The debug overlay appears in the top-right of the game viewport showing:
   - Current script name and state
   - Player position (X, Y coordinates)
   - Number of visible NPCs
   - Green tile outline under your player
   - Yellow bounding boxes on nearby NPCs

This is your main diagnostic tool during testing.

---

## Step 5 — Test each script

### Recommended test order (simplest → most complex)

**1. High Alchemy** (no movement, pure inventory)
- Fill inventory with nature runes + items to alch
- Have fire runes or a fire staff equipped
- Select "High Alchemy" → Start
- Should alch one item every ~3 ticks (1.8 seconds)
- Verify: items disappear, GP increases, state label shows "Running"

**2. Woodcutting** (world interaction, object click)
- Stand near some trees
- Select "Woodcutting" → Start
- Should click "Chop down" on nearest tree, chop, drop logs when full, repeat
- Verify: player animates, logs accumulate then drop, finds new tree after depletion

**3. Fishing** (NPC target)
- Stand near fishing spots
- Select "Fishing" → Start
- Should click spot with "Lure"/"Bait"/etc., power-fish, drop fish when full
- Verify: player faces spot, catch accumulates, drops when full, re-finds spot

**4. Mining** (world object, rock depletion)
- Stand near ore rocks
- Select "Mining" → Start
- Verify rock depletion is detected (player goes idle, finds new rock)

**5. Combat** (NPC attack + eating)
- Stand near Sand Crabs (NPC IDs 1266/1267) or similar
- Have food in inventory
- Select "Combat" → Start
- Verify: attacks target, eats when below 50% HP, re-attacks after kill

**6. Cooking, Smithing, Fletching, Gem Cutting**
- These all use the Make-All production dialogue
- Verify the dialogue opens and "Make All" is clicked correctly
- Watch the state label — should go to "Running" while the production animation plays

---

## Checking logs

RuneLite console (View → Show Client Log) shows all bot engine log output:

```
[BotEngine] [Woodcutting] Started — power-chop mode (drop logs when full)
[BotEngine] [Woodcutting] Chopping tree (id=1276)
[BotEngine] [Woodcutting] Inventory full — dropping logs
[BotEngine] Break started — pausing Woodcutting
[BotEngine] Break complete — resuming Woodcutting
```

Enable "Verbose logging" in config to see debug-level output including every click, slot lookup, and state transition.

---

## Common issues

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| Panel doesn't appear | JAR not loaded / plugin disabled | Re-load JAR, enable in plugin list |
| Script starts but nothing happens | Standing too far from target | Move closer, try again |
| "No tree found nearby" in logs | Wrong tree IDs for your location | Add IDs to `TREE_IDS` in WoodcuttingScript |
| High Alchemy doesn't fire | Missing runes or no alchable items | Check inventory |
| Production dialogue doesn't open | Object or NPC not found | Enable debug overlay to check targeting |
| NullPointerException in logs | Logged by ScriptRunner, script auto-stopped | Check logs for which line failed |

---

## Adding support for new object/NPC IDs

If a script doesn't find its target at your location, the object ID may not be in the script's array.

1. Enable debug overlay — it shows NPC IDs and object info
2. Alternatively, use the RuneLite "Object Markers" or "NPC Indicators" plugins to see IDs
3. Add the ID to the relevant array in the script (e.g., `TREE_IDS` in `WoodcuttingScript`)
4. Rebuild with `mvn package` and reload the JAR

---

## Antiban behavior during testing

With default settings, the first break is scheduled 45 minutes (±20%) into the session. During testing you may want to shorten this to verify the break cycle works:

- Set "Break every" to 1 minute in config
- Run a script for 1-2 minutes
- The state label should change to "Breaking" and the overlay should show break info
- After the break duration, it should resume automatically

---

## Re-loading after code changes

After modifying and rebuilding (`mvn package`):
1. Disable the plugin in RuneLite's plugin list
2. Use "Load plugin from file" again to re-load the new JAR
3. Re-enable the plugin

RuneLite does not hot-reload — a full disable/re-load cycle is required.
