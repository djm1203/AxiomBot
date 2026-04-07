# ============================================================================
# Axiom Bot Engine — Windows Setup Script
# ============================================================================
# Run this once to set up everything needed to run Axiom.
#
# Prerequisites (must be done manually BEFORE running this script):
#   1. Log in to Old School RuneScape via the official Jagex Launcher at least
#      once. This creates the credentials file Axiom needs to authenticate.
#      Download: https://www.jagex.com/en-GB/launcher
#
# Then right-click this file → "Run with PowerShell" (or: powershell -File setup.ps1)
# ============================================================================

$ErrorActionPreference = "Stop"
$AxiomDir   = "$HOME\.axiom"
$RuneLiteDir = "$AxiomDir\runelite"
$PluginsDir  = "$HOME\.runelite\sideloaded-plugins"
$CredFile    = "$HOME\.runelite\credentials.properties"

# ── Banner ────────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "  ⚡ AXIOM Bot Engine — Setup" -ForegroundColor Yellow
Write-Host "  ─────────────────────────────────────────────" -ForegroundColor DarkGray
Write-Host ""

# ── Step 1: Java 11+ ──────────────────────────────────────────────────────────
Write-Host "[1/6] Checking Java..." -ForegroundColor Cyan
try {
    $javaVersion = (java -version 2>&1 | Select-String "version").ToString()
    Write-Host "      Found: $javaVersion" -ForegroundColor Green

    # Extract major version number
    $major = [int]($javaVersion -replace '.*"(\d+)[\.\d]*".*', '$1')
    if ($major -lt 11) {
        Write-Host "      ERROR: Java 11 or higher is required (found $major)" -ForegroundColor Red
        Write-Host "      Download: https://adoptium.net" -ForegroundColor Yellow
        exit 1
    }
} catch {
    Write-Host "      ERROR: Java not found. Install Java 11+: https://adoptium.net" -ForegroundColor Red
    exit 1
}

# ── Step 2: Git ───────────────────────────────────────────────────────────────
Write-Host "[2/6] Checking Git..." -ForegroundColor Cyan
try {
    $gitVersion = git --version
    Write-Host "      Found: $gitVersion" -ForegroundColor Green
} catch {
    Write-Host "      ERROR: Git not found. Install Git: https://git-scm.com/download/win" -ForegroundColor Red
    exit 1
}

# ── Step 3: Maven ─────────────────────────────────────────────────────────────
Write-Host "[3/6] Checking Maven..." -ForegroundColor Cyan
try {
    $mvnVersion = mvn --version | Select-Object -First 1
    Write-Host "      Found: $mvnVersion" -ForegroundColor Green
} catch {
    Write-Host "      ERROR: Maven not found. Install Maven: https://maven.apache.org/download.cgi" -ForegroundColor Red
    Write-Host "      Or run: winget install Apache.Maven" -ForegroundColor Yellow
    exit 1
}

# ── Step 4: Jagex credentials check ──────────────────────────────────────────
Write-Host "[4/6] Checking Jagex credentials..." -ForegroundColor Cyan
if (-not (Test-Path $CredFile)) {
    Write-Host ""
    Write-Host "  ╔══════════════════════════════════════════════════════════════╗" -ForegroundColor Yellow
    Write-Host "  ║  ACTION REQUIRED: Jagex credentials not found.              ║" -ForegroundColor Yellow
    Write-Host "  ║                                                              ║" -ForegroundColor Yellow
    Write-Host "  ║  1. Download and install the official Jagex Launcher:        ║" -ForegroundColor Yellow
    Write-Host "  ║     https://www.jagex.com/en-GB/launcher                    ║" -ForegroundColor Yellow
    Write-Host "  ║  2. Log in to OSRS at least once through the launcher.      ║" -ForegroundColor Yellow
    Write-Host "  ║  3. Re-run this setup script.                                ║" -ForegroundColor Yellow
    Write-Host "  ╚══════════════════════════════════════════════════════════════╝" -ForegroundColor Yellow
    Write-Host ""
    exit 1
}
Write-Host "      Credentials found at: $CredFile" -ForegroundColor Green

# ── Step 5: Clone and build RuneLite ─────────────────────────────────────────
Write-Host "[5/6] Setting up RuneLite..." -ForegroundColor Cyan

$RuneLiteShadedJar = "$RuneLiteDir\runelite-client\build\libs\client-*-shaded.jar"

if (-not (Test-Path "$RuneLiteDir\.git")) {
    Write-Host "      Cloning RuneLite source (this takes a few minutes)..." -ForegroundColor White
    New-Item -ItemType Directory -Force -Path $AxiomDir | Out-Null
    git clone https://github.com/runelite/runelite "$RuneLiteDir" --depth 1
    Write-Host "      Cloned." -ForegroundColor Green
} else {
    Write-Host "      RuneLite already cloned at $RuneLiteDir" -ForegroundColor Green
}

# Build RuneLite if the shaded JAR doesn't exist yet
if (-not (Get-ChildItem -Path "$RuneLiteDir\runelite-client\build\libs\" -Filter "*-shaded.jar" -ErrorAction SilentlyContinue)) {
    Write-Host "      Building RuneLite client JAR (5–10 minutes first time)..." -ForegroundColor White
    Push-Location "$RuneLiteDir\runelite-client"
    & "$RuneLiteDir\gradlew.bat" shadowJar -x test
    Pop-Location
    Write-Host "      RuneLite built." -ForegroundColor Green
} else {
    Write-Host "      RuneLite already built." -ForegroundColor Green
}

# ── Step 6: Deploy Axiom plugin ──────────────────────────────────────────────
Write-Host "[6/6] Deploying Axiom plugin..." -ForegroundColor Cyan

# Find the Axiom JAR (looks for it next to this script, or downloads latest release)
$ScriptDir  = Split-Path -Parent $MyInvocation.MyCommand.Path
$AxiomJar   = Get-ChildItem -Path $ScriptDir -Filter "axiom-*.jar" -ErrorAction SilentlyContinue |
               Sort-Object LastWriteTime -Descending | Select-Object -First 1

if (-not $AxiomJar) {
    # Try the Maven target directory (if user cloned the Axiom source too)
    $AxiomJar = Get-ChildItem -Path "$ScriptDir\target" -Filter "bot-engine-osrs-*.jar" `
                    -ErrorAction SilentlyContinue |
                    Where-Object { $_.Name -notlike "*original*" } |
                    Sort-Object LastWriteTime -Descending | Select-Object -First 1
}

if (-not $AxiomJar) {
    Write-Host "      ERROR: No Axiom JAR found next to this script." -ForegroundColor Red
    Write-Host "      Download the latest release from GitHub and place axiom-vX.X.X.jar" -ForegroundColor Yellow
    Write-Host "      in the same folder as setup.ps1, then re-run." -ForegroundColor Yellow
    exit 1
}

New-Item -ItemType Directory -Force -Path $PluginsDir | Out-Null
Copy-Item $AxiomJar.FullName "$PluginsDir\axiom.jar" -Force
Write-Host "      Deployed: $($AxiomJar.Name) → $PluginsDir\axiom.jar" -ForegroundColor Green

# ── Create launch shortcut ────────────────────────────────────────────────────
$RuneLiteJar = Get-ChildItem -Path "$RuneLiteDir\runelite-client\build\libs\" `
                   -Filter "*-shaded.jar" | Select-Object -First 1

$LaunchBat = "$AxiomDir\launch.bat"
@"
@echo off
java -ea -jar "$($RuneLiteJar.FullName)" --developer-mode %*
"@ | Set-Content $LaunchBat

# Desktop shortcut
$WshShell  = New-Object -ComObject WScript.Shell
$Shortcut  = $WshShell.CreateShortcut("$HOME\Desktop\Axiom OSRS.lnk")
$Shortcut.TargetPath    = $LaunchBat
$Shortcut.WorkingDirectory = $AxiomDir
$Shortcut.Description  = "Launch Axiom OSRS Bot Engine"
$Shortcut.Save()

# ── Done ──────────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "  ✓  Setup complete!" -ForegroundColor Green
Write-Host ""
Write-Host "  To start Axiom:" -ForegroundColor White
Write-Host "    • Double-click 'Axiom OSRS' on your Desktop" -ForegroundColor Cyan
Write-Host "    • Or run: $LaunchBat" -ForegroundColor Cyan
Write-Host ""
Write-Host "  In-game: click the ⚡ A icon in the RuneLite toolbar." -ForegroundColor White
Write-Host ""
