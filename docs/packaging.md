# Packaging Axiom as a Windows .exe Installer

This document outlines the plan for distributing Axiom as a single Windows installer that sets up everything a user needs: a bundled JRE, a local RuneLite build, and the Axiom plugin pre-installed.

---

## Goal

A user double-clicks `AxiomSetup.exe` and gets:

- A self-contained install directory with its own JRE (no system Java required)
- RuneLite launched in developer mode with Axiom sideloaded
- A desktop shortcut that starts everything with one click

---

## Toolchain overview

| Tool | Role |
|------|------|
| **jlink** (JDK 14+) | Creates a minimal JRE containing only the modules Axiom + RuneLite actually use |
| **jpackage** (JDK 14+) | Wraps the JRE + application JARs into a Windows app directory (`.exe` launcher) |
| **Inno Setup** | Wraps the jpackage output into a proper install wizard (`.exe` installer with EULA, shortcuts, uninstaller) |

**Why this combination:** jpackage handles the JRE bundling and produces a native launcher. Inno Setup adds the wizard UI, directory picker, desktop shortcut, and uninstall entry — things jpackage's built-in installer support handles poorly on Windows compared to a dedicated tool.

**Alternatives considered:**
- **NSIS** — more flexible than Inno Setup but requires more manual scripting; good if you need custom install logic
- **WiX Toolset** — produces MSI packages (enterprise-friendly); heavier and more complex than needed here
- **Inno Setup alone** — could bundle JARs directly without jpackage, but you lose the native `.exe` launcher and have to write the classpath manually

---

## What needs to be built first: `Launcher.java`

Before packaging, you need a `Launcher` main class that acts as the entry point. Its job:

1. Determine the install directory (relative to its own location)
2. Copy `axiom.jar` into `~/.runelite/sideloaded-plugins/` (creating the directory if needed)
3. Exec RuneLite's shaded JAR with `--developer-mode` (and optionally pass through any CLI args)

```java
// src/main/java/com/botengine/osrs/launcher/Launcher.java (sketch)
public class Launcher {
    public static void main(String[] args) throws Exception {
        // 1. Resolve paths relative to the install dir
        Path installDir = Path.of(Launcher.class
            .getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
        Path axiomJar   = installDir.resolve("lib/bot-engine-osrs-1.0-SNAPSHOT.jar");
        Path runeliteJar = installDir.resolve("lib/runelite-client-shaded.jar");
        Path sideloaded = Path.of(System.getProperty("user.home"),
            ".runelite", "sideloaded-plugins");

        // 2. Deploy the plugin
        Files.createDirectories(sideloaded);
        Files.copy(axiomJar, sideloaded.resolve("bot-engine-osrs-1.0-SNAPSHOT.jar"),
            StandardCopyOption.REPLACE_EXISTING);

        // 3. Launch RuneLite
        List<String> cmd = new ArrayList<>();
        cmd.add(ProcessHandle.current().info().command().orElse("java"));
        cmd.add("-ea");
        cmd.add("-jar");
        cmd.add(runeliteJar.toString());
        cmd.add("--developer-mode");
        Collections.addAll(cmd, args);

        new ProcessBuilder(cmd)
            .inheritIO()
            .start()
            .waitFor();
    }
}
```

This class becomes the jpackage `--main-class`. It ensures the plugin is always in sync before RuneLite loads.

---

## Step-by-step packaging plan

### Step 1 — Build the Axiom JAR

```bash
mvn package -DskipTests
# Output: target/bot-engine-osrs-1.0-SNAPSHOT.jar
```

### Step 2 — Obtain the RuneLite shaded JAR

Build RuneLite from source (tag `1.12.24`):

```bash
cd path/to/runelite
./gradlew :runelite-client:shadowJar
# Output: runelite-client/build/libs/client-1.12.24-SNAPSHOT-shaded.jar
```

Copy it into a staging directory:

```
packaging/
├── lib/
│   ├── bot-engine-osrs-1.0-SNAPSHOT.jar
│   ├── runelite-client-shaded.jar
│   └── launcher.jar          ← compiled Launcher.java (see step 3)
└── input/                    ← jpackage --input directory
```

### Step 3 — Compile and package the Launcher

```bash
javac -d packaging/launcher-classes src/main/java/com/botengine/osrs/launcher/Launcher.java
jar cfe packaging/lib/launcher.jar com.botengine.osrs.launcher.Launcher \
    -C packaging/launcher-classes .
```

### Step 4 — Create a minimal JRE with jlink

Identify the modules needed. At minimum:

```
java.base, java.desktop, java.logging, java.naming, java.net.http,
java.prefs, java.scripting, java.sql, jdk.crypto.ec
```

RuneLite may require additional modules — run `jdeps` against the shaded JAR to get the full list:

```bash
jdeps --multi-release 11 --print-module-deps \
    packaging/lib/runelite-client-shaded.jar
```

Then build the JRE:

```bash
jlink \
  --module-path "$JAVA_HOME/jmods" \
  --add-modules java.base,java.desktop,java.logging,java.naming,java.net.http,\
java.prefs,java.scripting,java.sql,jdk.crypto.ec \
  --strip-debug \
  --no-header-files \
  --no-man-pages \
  --compress=2 \
  --output packaging/jre
```

This typically produces a ~60–80 MB JRE vs ~200 MB for a full JDK.

### Step 5 — Run jpackage

```bash
jpackage \
  --type app-image \
  --name Axiom \
  --app-version 1.0.0 \
  --vendor "Axiom" \
  --input packaging/lib \
  --main-jar launcher.jar \
  --main-class com.botengine.osrs.launcher.Launcher \
  --runtime-image packaging/jre \
  --java-options "-ea" \
  --win-console \
  --dest packaging/output
```

`--type app-image` produces a directory (`packaging/output/Axiom/`) rather than a self-contained installer — Inno Setup will wrap it instead.

`--win-console` keeps a terminal window open so RuneLite and bot logs are visible. Remove it if you want a silent background launch.

### Step 6 — Create the Inno Setup script

Save as `packaging/axiom-installer.iss`:

```iss
[Setup]
AppName=Axiom
AppVersion=1.0.0
AppPublisher=Axiom
DefaultDirName={autopf}\Axiom
DefaultGroupName=Axiom
OutputBaseFilename=AxiomSetup
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
LicenseFile=LICENSE.txt

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Create a &desktop shortcut"; GroupDescription: "Additional icons:"; Flags: unchecked

[Files]
; Include the entire jpackage app-image
Source: "output\Axiom\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\Axiom"; Filename: "{app}\Axiom.exe"
Name: "{commondesktop}\Axiom"; Filename: "{app}\Axiom.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\Axiom.exe"; Description: "Launch Axiom"; Flags: nowait postinstall skipifsilent
```

Compile with:

```bash
iscc packaging/axiom-installer.iss
# Output: packaging/AxiomSetup.exe
```

---

## Credentials (one-time user step)

The installer cannot bundle Jagex credentials — those are per-account and expire. The Axiom panel (or a first-run dialog) should display these instructions on first launch:

> To log in, set the environment variable `RUNELITE_ARGS=--insecure-write-credentials`, launch OSRS once via the Jagex Launcher, then restart Axiom. This writes your session to `~/.runelite/credentials.properties` and only needs to be done once (or when your session expires).

---

## Licensing considerations

- **RuneLite** is BSD 2-clause licensed. Redistributing its compiled JARs is permitted under that license, but you must include the copyright notice and license text.
- **Jagex ToS** prohibits use of third-party clients for real-game automation. This project is for educational purposes on private servers only. Distributing a packaged tool that targets the live game would violate Jagex's terms.
- Include a `LICENSE.txt` (your project license) and a `THIRD-PARTY-LICENSES.txt` covering RuneLite and any other bundled libraries. Reference `LICENSE.txt` in the Inno Setup `[Setup]` section so users see it during install.

---

## Summary of files to create

```
src/main/java/com/botengine/osrs/launcher/
└── Launcher.java

packaging/
├── axiom-installer.iss
├── LICENSE.txt
├── THIRD-PARTY-LICENSES.txt
└── lib/
    ├── bot-engine-osrs-1.0-SNAPSHOT.jar   (from mvn package)
    ├── runelite-client-shaded.jar          (from RuneLite Gradle build)
    └── launcher.jar                        (compiled from Launcher.java)
```

Build order: `mvn package` → Gradle RuneLite build → compile Launcher → jlink → jpackage → Inno Setup.
