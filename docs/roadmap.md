# Axiom — Product Roadmap
> Status: Active | Last updated: April 2026
> Replaces: previous docs/roadmap.md

---

## Overview

Axiom is built in five phases. Each phase must be stable before the next begins.
The goal is a production-quality platform, built correctly and incrementally.

---

## Phase 1 — Engine Core Rebuild ⬅ CURRENT

**Goal:** Clean Maven multi-module structure with one working script.
Everything built in this phase is the foundation all future work depends on.

| Item | Status | Notes |
|---|---|---|
| Maven multi-module structure (axiom-api / axiom-plugin / axiom-scripts) | 🔲 | Top priority |
| axiom-api: game/, player/, world/, interaction/, util/ | 🔲 | No RuneLite imports in this module |
| Widgets.java — centralized dialog/make-all handling | 🔲 | Fixes Fletching/Cooking breakage |
| Pathfinder.java — obstacle-aware pathfinding | 🔲 | Fixes Agility/Thieving breakage |
| Skills.java — level/xp tracking | 🔲 | Needed for progressive mode |
| @ScriptManifest annotation + ScriptLoader | 🔲 | Replaces hardcoded 13-param AxiomPanel |
| ScriptRunner — clean state machine | 🔲 | STOPPED/RUNNING/PAUSED/BREAKING |
| LauncherBridge — stub for launcher args | 🔲 | Reads -Daxiom.* system properties |
| AxiomPanel — dynamic script list from ScriptLoader | 🔲 | Never hardcodes scripts |
| WoodcuttingScript — validation script | 🔲 | If this works, the framework is correct |
| Unit tests for API layer + ScriptRunner | 🔲 | |

**Definition of done:** `mvn package` succeeds, plugin loads, Woodcutting runs
a full bank loop without crashing.

---

## Phase 2 — All Scripts, Production Quality

Build scripts in this order. Each one validates a different part of the API.
Do not start the next until the current is stable in-game.

| Order | Script | Key validation |
|---|---|---|
| 1 | ✅ Woodcutting | Done in Phase 1 |
| 2 | Fishing | shift-drop, spot hopping |
| 3 | Firemaking | linear path, no bank |
| 4 | Cooking | Widgets.java make-all, bank loop |
| 5 | Alchemy | simplest 1-item loop |
| 6 | Herblore | make-all dialog variant |
| 7 | Fletching | 1-tick timing, Widgets.java |
| 8 | Crafting | Widgets.java, gem variety |
| 9 | Smithing | Blast Furnace mode |
| 10 | Mining | MLM mode, rock progression |
| 11 | Thieving | Pathfinder, stall→knight→blackjack |
| 12 | Agility | Course-as-data table, all rooftops |
| 13 | Combat | Full state: eat, pray, loot, spec |

**Additional combat modes (after base Combat):**
- NMZ mode (absorption/overload, point collection, dream setup)
- Crab Killer (aggro reset, multi-spot)

**Additional money-making scripts (after Phase 2 complete):**
- Blast Mine
- Dragon Killer
- Hunter (box traps, bird snares, black chins)
- GE Mercher (price margin automation)

### Config persistence

During Phase 2, add JSON save/load for all ScriptSettings:
- Save to `~/.axiom/profiles/<scriptname>/last.json`
- Load on dialog open
- Support named profiles (save as / load from)

### Script chaining / task queue

Add `TaskQueue.java` to axiom-plugin during Phase 2:
```
Queue: [Woodcutting(Willows, 60min)] → [Fletching(Willow shortbows)] → [Alchemy]
```
This is what nScripts calls "nScript Queue" — one of their most-used features.

---

## Phase 3 — Standalone Launcher

**Goal:** A desktop app that replaces manual RuneLite setup. Users never touch
RuneLite or the Jagex Launcher directly.

**Tech:** Java + JavaFX. SQLite via JDBC for local account/proxy storage.

| Item | Status | Notes |
|---|---|---|
| axiom-launcher Maven module | 🔲 | |
| SQLite schema: accounts, proxies, sessions | 🔲 | |
| Account manager UI | 🔲 | Add/edit/delete accounts, assign proxy |
| Proxy manager UI | 🔲 | Add/edit/delete proxies |
| Spawn single RuneLite instance with args | 🔲 | ClientManager.java |
| Session monitor — see running bots | 🔲 | |
| Bulk launch from account list | 🔲 | CSV import |
| CLI — headless operation | 🔲 | axiom run / bulk-launch / accounts / proxies |
| Account CSV import/export | 🔲 | |
| Proxy CSV import/export | 🔲 | |
| Break profile management | 🔲 | |
| jpackage → Windows .exe | 🔲 | See docs/packaging.md |

**Launcher → plugin communication:**
```bash
java -jar runelite-shaded.jar --developer-mode \
     -Daxiom.script="Axiom Woodcutting"         \
     -Daxiom.world=302                           \
     -Daxiom.account.id=<jagex_character_id>
```
LauncherBridge in the plugin reads these on startup and auto-starts the script.

---

## Phase 4 — Script Distribution

**Goal:** Scripts downloadable from a repository, not manually copied.

| Item | Status | Notes |
|---|---|---|
| Script repo server (GitHub releases as CDN) | 🔲 | Free, no infra needed |
| Launcher downloads/updates script JARs | 🔲 | Auto-update per script |
| Script browser in launcher UI | 🔲 | Browse, install, update |
| Community script submissions | 🔲 | Separate from first-party scripts |

---

## Phase 5 — License & Monetization

**Goal:** Tier-based access. Developer always has free unlimited access.

**Tiers:**
| Tier | Access | Price |
|---|---|---|
| Free | F2P scripts only | Free |
| Premium | All scripts, 1 account | $X/mo |
| Ultimate | All scripts, unlimited accounts | $Y/mo |
| Owner | Everything, hardcoded key | Free (you) |

**Components:**
- `LicenseManager.java` — reads/validates license from disk
- `HardwareFingerprint.java` — stable machine ID (MAC + CPU + disk + username hash)
- Validation endpoint — simple Spring Boot app on Railway (free tier)
- Web portal — purchase, download, manage license
- RSA signing: private key signs (fingerprint + tier + expiry), public key in JAR

---

## Deferred / Future

| Item | Notes |
|---|---|
| macOS / Linux support | setup.sh equivalent, jpackage .dmg |
| Session telemetry (opt-in) | PostHog, XP/hr, script usage |
| Muling support | Trade GP between accounts automatically |
| Web dashboard | Session history, XP graphs |
| Mobile support | Out of scope — PowBot owns this |
