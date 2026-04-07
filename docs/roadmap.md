# Axiom — Product Roadmap

This document tracks all planned and completed infrastructure phases beyond the core bot engine.
Everything here uses free, open-source tooling.

---

## Phase 1 — Core Engine ✅ DONE

| Item | Status |
|---|---|
| 9 scripts (Combat, WC, Mining, Fishing, Cooking, Alchemy, Smithing, Fletching, Crafting) | ✅ |
| Axiom UI — per-script config dialogs, AxiomPanel, ScriptSettings persistence | ✅ |
| Cannon support (CombatScript) | ✅ |
| Blast Furnace mode (SmithingScript) | ✅ |
| Motherlode Mine mode (MiningScript) | ✅ |
| Grand Exchange restock API | ✅ |
| 285 unit tests passing | ✅ |

---

## Phase 2 — Build Infrastructure ✅ DONE

| Item | Status | Notes |
|---|---|---|
| **ProGuard obfuscation** | ✅ | `mvn package -Prelease` → `*-obfuscated.jar` |
| **GitHub Actions CI** | ✅ | `.github/workflows/ci.yml` — tests on every push |
| **GitHub Actions Release** | ✅ | `.github/workflows/release.yml` — obfuscated JAR on tag push |
| **Auto-update check** | ✅ | `AutoUpdater.java` — checks GitHub releases API on startup |
| **Sentry crash reporting** | ✅ | `AxiomSentry.java` — reflective init, disabled until DSN configured |
| **Setup script** | ✅ | `setup.ps1` — full Windows installer script |
| **version.properties** | ✅ | Maven-filtered, readable at runtime |

### Using the release build

```bash
# Build obfuscated release JAR
mvn package -Prelease

# Output: target/bot-engine-osrs-<version>-obfuscated.jar
# This is what gets distributed / attached to GitHub releases
```

### Activating Sentry crash reporting

1. Create a free account at https://sentry.io
2. New Project → Java → copy your DSN
3. For local dev: `export AXIOM_SENTRY_DSN=https://...@sentry.io/...`
4. For releases: add `SENTRY_DSN` as a GitHub Actions secret (Settings → Secrets)
5. The release CI pipeline injects it automatically at build time

### Setting up GitHub Actions

1. Push this repo to GitHub (public or private)
2. Update `version.properties`: set `github.repo=YourUsername/axiom` 
3. Update `release.yml`: release body already points at README
4. Push a version tag to trigger a release:
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```

---

## Phase 3 — Distribution (.exe Installer)

> **Status: Planned** — see `docs/packaging.md` for the full technical plan.

Goal: a single `AxiomSetup.exe` that a new user double-clicks to get everything installed.

| Step | Tool | Notes |
|---|---|---|
| Write `Launcher.java` | Java | Bootstraps RuneLite with correct args |
| Minimal JRE | `jlink` | ~50MB custom JRE vs 200MB full JDK |
| App bundle | `jpackage` | Wraps JRE + JARs into a self-contained directory |
| Installer wizard | Inno Setup (free) | `.iss` script → `AxiomSetup.exe` |

The setup script (`setup.ps1`) is the bridge until the `.exe` is ready — it does the same steps but requires the user to have Java/Git/Maven pre-installed.

---

## Phase 4 — Telemetry (Opt-In)

> **Status: Planned** — no implementation yet.
> **Tool: PostHog** — open source, free cloud tier (1M events/month), self-hostable.

What to track (opt-in only, no personal data):
- Which scripts are started and for how long
- Script errors (type + frequency, no stack trace)
- Feature usage (cannon mode, blast furnace, etc.)

```java
// Planned: PostHog Java SDK
PostHog posthog = new PostHog.Builder("YOUR_API_KEY")
    .host("https://app.posthog.com")
    .build();
posthog.capture(anonymousId, "script_started",
    Map.of("script", "Combat", "cannon_mode", true));
```

Setup: https://posthog.com — free cloud tier, or self-host on Railway/Render.

---

## Phase 5 — License & Monetization

> **Status: Deferred** — implement when ready to charge users.
> Developer (you) always has free unlimited access via a hardcoded owner key.

### Architecture
```
Hardware fingerprint = hash(MAC + CPU ID + disk serial + username)
License key          = RSA_sign(fingerprint + expiry + tier, PRIVATE_KEY)
App validates with   = RSA_verify(key, PUBLIC_KEY)   ← public key baked into JAR
```

### Tiers (proposed)
| Tier | Features | Price |
|---|---|---|
| Free (you) | All scripts, unlimited | Owner key |
| Trial | All scripts, 7 days | Email signup |
| Basic | All scripts, 1 account | $X/mo |
| Pro | All scripts + advanced modes, multi-account | $Y/mo |

### Components to build
- `LicenseManager.java` — reads/verifies license from disk
- `HardwareFingerprint.java` — generates stable machine ID
- Online validation endpoint (Railway/Render free tier) — revocation support
- Web portal (axiom.gg) — purchase, download, manage license

### Implementation order when ready
1. `LicenseManager` + `HardwareFingerprint` (offline validation, no server needed)
2. Tier-based feature gating in `BotEnginePlugin.startUp()`
3. Online validation endpoint (simple Spring Boot app, deploy free)
4. Trial key generation + expiry UI
5. Payment integration (Stripe, simple checkout page)

---

## Phase 6 — Auto-Update (Full)

> **Status: Partial** — update check exists (`AutoUpdater.java`), download+replace not yet built.

Current: logs when a new GitHub release is available.

Full implementation:
```
1. AutoUpdater checks GitHub API → finds newer version
2. Shows in-game notification: "Update available: v1.2.3 — Click to install"
3. On confirm: downloads new JAR to temp location
4. Replaces current sideloaded plugin JAR
5. Shows "Restart RuneLite to apply update"
```

No server needed — uses GitHub Releases as the CDN (free, unlimited bandwidth for public repos).

---

## Open Questions / Future Ideas

- **macOS support** — setup script needs a `.sh` equivalent; jpackage can produce `.dmg`
- **Linux support** — straight-forward, same RuneLite source build approach
- **Discord integration** — bot status to Discord server, community support
- **Script marketplace** — allow community scripts to be loaded (much bigger scope)
- **Web dashboard** — usage stats, script uptime, session history
