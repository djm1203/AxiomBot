# Testing Guide
> Status: Reference | Last updated: April 2026

---

## Prerequisites

- Java 11+ on PATH
- Maven 3.x on PATH
- RuneLite source built from tag `1.12.23` or `1.12.24-SNAPSHOT`
- Axiom built: `mvn clean package -DskipTests` from project root

---

## Building

```bash
# Build all modules
mvn clean package -DskipTests

# Outputs:
#   axiom-plugin/target/axiom-plugin-*.jar    → deploy to RuneLite
#   axiom-scripts/axiom-woodcutting/target/axiom-woodcutting-*.jar → deploy to scripts dir
```

---

## Deploying

```bash
# 1. Deploy plugin
cp axiom-plugin/target/axiom-plugin-*.jar ~/.runelite/sideloaded-plugins/

# 2. Deploy script JARs
mkdir -p ~/.axiom/scripts
cp axiom-scripts/axiom-woodcutting/target/axiom-woodcutting-*.jar ~/.axiom/scripts/

# 3. Launch RuneLite in developer mode
java -ea -jar "<path-to-runelite-shaded.jar>" --developer-mode
```

Log should show:
```
Side-loading plugin ...axiom-plugin-*.jar
Axiom ready — loaded N scripts
```

If N = 0, ScriptLoader failed to find scripts — check ~/.axiom/scripts/ has JARs.

---

## Credentials (one-time setup)

```powershell
# Set env var, then launch OSRS via Jagex Launcher once to write credentials
[System.Environment]::SetEnvironmentVariable('RUNELITE_ARGS', '--insecure-write-credentials', 'User')
```

Verify: `cat "$HOME/.runelite/credentials.properties"` — should show JX_SESSION_ID etc.

---

## Running tests

```bash
# All tests
mvn test

# Specific module
mvn test -pl axiom-plugin

# Specific class
mvn test -Dtest=ScriptRunnerTest

# With output
mvn test -Dtest=WoodcuttingScriptTest --info
```

---

## In-game validation checklist

Run this against every script before marking it complete.

### Framework checks (run once after Phase 1)

```
□ Plugin enables without error in RuneLite plugin list
□ Plugin disables cleanly (no leftover state, no exceptions)
□ AxiomPanel shows available scripts (loaded from ScriptLoader, not hardcoded)
□ Adding a new script JAR to ~/.axiom/scripts/ and restarting shows it in panel
□ ScriptLoader loads @ScriptManifest correctly (name, category, version)
```

### Per-script checks (run for each script)

```
□ Script appears in AxiomPanel with correct name and category
□ Config dialog opens with correct fields
□ Start button transitions panel to RUNNING state
□ Overlay shows script name, state, runtime
□ Script performs correct first action for its state
□ State transitions work: e.g. FIND_TREE → CHOPPING → BANKING → FIND_TREE
□ Pause button halts action; Resume resumes from same state
□ Stop button cleans up and resets to STOPPED
□ Antiban delays are visible — not instant-clicking every tick
□ Script handles target-not-found gracefully (doesn't crash, logs warning)
□ Script handles full inventory correctly (banks or drops, doesn't freeze)
□ Script handles empty inventory correctly (withdraws or stops, doesn't freeze)
□ Make-All dialog handled by Widgets.java (not ad-hoc per script)
□ XP/hr counter on overlay updates correctly
□ Debug overlay shows correct tile/object highlights
□ Emergency logout triggers if HP is critical (combat scripts)
□ Break scheduling fires (wait 30+ min, should see a break taken)
□ Auto-pause on logout; auto-resume on re-login
□ Script stops cleanly after 5 consecutive errors
```

### Agility-specific checks

```
□ Each obstacle in the course is clicked correctly
□ Completion detected via animation ID (not just tile position)
□ Course restarts cleanly after final obstacle lap bonus
□ Marks of Grace are picked up if configured
□ Course handles obstacle failure (player pushed back, re-attempts)
□ Progressive course switching at configured levels
```

### Pathfinder checks

```
□ walkTo() correctly navigates through a door
□ walkTo() correctly navigates up a staircase
□ walkTo() handles a gate (click to open, walk through)
□ isReachable() returns false for tiles behind closed obstacles
```

---

## Common failure modes and fixes

| Symptom | Likely cause | Fix |
|---|---|---|
| Script list empty in panel | ScriptLoader not finding JARs | Check ~/.axiom/scripts/ has JARs, check @ScriptManifest annotation |
| Make-All dialog not clicked | Per-script dialog handling | Move to Widgets.clickMakeAll() |
| Script freezes at obstacle | No pathfinding | Use Pathfinder.walkTo() not Movement.walkTo() |
| Script clicks wrong object | ID mismatch | Verify object ID in-game with developer overlay |
| Script crashes every loop | Exception in onLoop | Check consecutive error counter, look at log |
| Animation not detected | Wrong animation ID | Use developer overlay to find correct animation ID |
| Bank not found | Wrong bank object ID | Verify with developer overlay at that bank |

---

## After code changes

```bash
mvn clean package -DskipTests
cp axiom-plugin/target/axiom-plugin-*.jar ~/.runelite/sideloaded-plugins/
cp axiom-scripts/axiom-<name>/target/*.jar ~/.axiom/scripts/
# Full close + restart RuneLite
```

Always `mvn clean package` (not just `mvn package`) after source changes.
