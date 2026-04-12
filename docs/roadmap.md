# Axiom — Product Roadmap
> Last updated: April 2026

---

## Overview

Axiom is built in five phases. Each phase must be stable before the next begins.

---

## Phase 1 — Engine Core ✅ COMPLETE

Maven multi-module structure, axiom-api contract layer, axiom-plugin implementation, ScriptRunner tick loop, ScriptLoader auto-discovery, AxiomPanel Swing sidebar, one working script (Woodcutting).

---

## Phase 2 — All Scripts ✅ COMPLETE (Agility deferred)

| Script | Status |
|--------|--------|
| Woodcutting | ✅ |
| Fishing | ✅ |
| Firemaking | ✅ |
| Cooking | ✅ |
| Alchemy | ✅ |
| Herblore | ✅ |
| Fletching | ✅ |
| Crafting | ✅ |
| Smithing | ✅ |
| Mining | ✅ |
| Thieving | ✅ |
| Combat | ✅ |
| Agility | 🔲 DEFERRED — obstacle sequencing per course is more complex; will add after review pass |

**Lessons from Phase 2:**
- Widget clicks (make-all, smithing dialog) use `RobotClick` not `client.menuAction()`
- `onStart()` runs on Swing EDT — zero game API calls there; all state in `onLoop()`
- Animation detection unreliable for short-animation actions (stall steal, firemaking); inventory change detection is more robust
- Stun detection: `players.getGraphic() == 245` works for pickpocket stun
- `players.isInCombat()` checks mutual interaction — correctly handles between-swing pauses

---

## Phase 3 — Standalone Launcher 🔲

**Goal:** A desktop app that replaces manual RuneLite setup.

| Item | Status |
|---|---|
| axiom-launcher Maven module (JavaFX) | 🔲 |
| SQLite schema: accounts, proxies, sessions | 🔲 |
| Account manager UI | 🔲 |
| Proxy manager UI | 🔲 |
| ClientManager — spawn RuneLite subprocess with args | 🔲 |
| Session monitor | 🔲 |
| CLI: `axiom run / bulk-launch / accounts / proxies` | 🔲 |
| jpackage → Windows .exe | 🔲 |

**Launcher → plugin arg protocol:**
```bash
java -jar runelite-shaded.jar --developer-mode \
     -Daxiom.script="Axiom Woodcutting"         \
     -Daxiom.world=302                           \
     -Daxiom.account.id=<jagex_character_id>
```
`LauncherBridge.java` in the plugin reads these on startup and auto-starts the script.

---

## Phase 4 — Script Distribution 🔲

Scripts downloadable from a repository, not manually copied into the plugin JAR.

| Item | Status |
|---|---|
| Script repo (GitHub releases as CDN) | 🔲 |
| Launcher downloads/updates script JARs | 🔲 |
| Script browser in launcher UI | 🔲 |
| Runtime URLClassLoader for script JARs | 🔲 |

---

## Phase 5 — License & Monetization 🔲

| Tier | Access | Price |
|---|---|---|
| Free | F2P scripts only | Free |
| Premium | All scripts, 1 account | $X/mo |
| Ultimate | All scripts, unlimited accounts | $Y/mo |
| Owner | Everything | Free (hardcoded) |

Components: `LicenseManager`, `HardwareFingerprint`, RSA offline validation, web portal.

---

## Deferred / Future

| Item | Notes |
|---|---|
| Agility script | Obstacle sequencing per course — adds after review pass |
| macOS / Linux | setup.sh, jpackage .dmg |
| Session telemetry (opt-in) | PostHog, XP/hr, script usage |
| Web dashboard | Session history, XP graphs |
