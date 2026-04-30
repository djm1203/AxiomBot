[CmdletBinding()]
param(
    [switch] $NoLaunch,
    [switch] $SkipTests,
    [switch] $Release,
    [switch] $Installer,
    [string] $ProguardJdkHome = "",
    [string] $RuneLiteJar = "C:\Users\dmart\Documents\Personal\Github\runelite\runelite-client\build\libs\client-1.12.24-SNAPSHOT-shaded.jar"
)

# Build + deploy + (optionally) launch RuneLite for Axiom development.
#
# Usage:
#   .\dev.ps1                  # full clean build, deploy, launch RuneLite
#   .\dev.ps1 -SkipTests       # same but skip Maven tests
#   .\dev.ps1 -NoLaunch        # build + deploy only
#   .\dev.ps1 -Release         # use the obfuscated release profile (-P release)

$ErrorActionPreference = 'Stop'
$ProgressPreference    = 'SilentlyContinue'
Set-StrictMode -Version Latest

function Write-Step { param([string]$Message) Write-Host $Message -ForegroundColor Cyan }
function Write-Ok   { param([string]$Message) Write-Host $Message -ForegroundColor Green }
function Write-Warn { param([string]$Message) Write-Host $Message -ForegroundColor Yellow }
function Write-Err  { param([string]$Message) Write-Host $Message -ForegroundColor Red }

$started = Get-Date

# ── Installer path ────────────────────────────────────────────────────────────
if ($Installer) {
    Write-Step "[INSTALLER] Building Axiom installer..."

    # WiX check — jpackage --type EXE requires candle.exe on PATH
    if (-not (Get-Command candle.exe -ErrorAction SilentlyContinue)) {
        Write-Err "WiX Toolset not found on PATH (candle.exe missing)."
        Write-Err "Install WiX 3.11: https://github.com/wixtoolset/wix3/releases"
        Write-Err "Then add its bin/ directory to PATH and retry."
        exit 1
    }
    Write-Ok "  WiX:      $(Get-Command candle.exe | Select-Object -ExpandProperty Source)"

    # 2. Locate a JDK 14+ for jlink/jpackage (does NOT need to be JAVA_HOME)
    $jpJdk = $env:AXIOM_JPACKAGE_HOME
    if (-not $jpJdk) {
        $detected = @(
            'C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot',
            'C:\Program Files\Eclipse Adoptium\jdk-21.0.7.6-hotspot',
            'C:\Program Files\Eclipse Adoptium\jdk-17.0.13.11-hotspot'
        ) | Where-Object { Test-Path "$_\bin\jpackage.exe" }
        if (@($detected).Count -gt 0) {
            $jpJdk = @($detected)[0]
            Write-Warn "  AXIOM_JPACKAGE_HOME not set; auto-detected: $jpJdk"
        }
    }
    if (-not $jpJdk -or -not (Test-Path "$jpJdk\bin\jpackage.exe")) {
        Write-Err "Cannot find jpackage.exe (requires JDK 14+)."
        Write-Err "Set AXIOM_JPACKAGE_HOME to your JDK 25 installation path."
        exit 1
    }
    Write-Ok "  jpackage JDK: $jpJdk"

    # 3. ProGuard needs JDK 11 jmods/ — resolve from parameter, env var, or auto-detect
    $pgJdk = $ProguardJdkHome
    if (-not $pgJdk) { $pgJdk = $env:AXIOM_PROGUARD_JDK_HOME }
    if (-not $pgJdk) {
        $detected = @(
            'C:\Program Files\Eclipse Adoptium\jdk-11.0.30.7-hotspot',
            'C:\Program Files\Eclipse Adoptium\jdk-11.0.25.9-hotspot',
            'C:\Program Files\Eclipse Adoptium\jdk-11.0.24.8-hotspot',
            'C:\Program Files\Java\jdk-11'
        ) | Where-Object { Test-Path "$_\jmods\java.base.jmod" }
        if (@($detected).Count -gt 0) {
            $pgJdk = @($detected)[0]
            Write-Warn "  -ProguardJdkHome not set; auto-detected: $pgJdk"
        }
    }
    if (-not $pgJdk -or -not (Test-Path "$pgJdk\jmods\java.base.jmod")) {
        Write-Err "Cannot find a JDK 11 with jmods/ for ProGuard."
        Write-Err "Pass: .\dev.ps1 -Installer -ProguardJdkHome 'C:\...\jdk-11'"
        Write-Err "  or: set AXIOM_PROGUARD_JDK_HOME=C:\...\jdk-11"
        exit 1
    }
    Write-Ok "  ProGuard JDK: $pgJdk"

    # 4. Build: release profile + verify runs ProGuard + jlink + jpackage
    Write-Step "Running: mvn clean -P release verify -DskipTests ..."
    $pgJdkFwd = $pgJdk -replace '\\', '/'
    $jpJdkFwd = $jpJdk -replace '\\', '/'
    & mvn 'clean' '-P' 'release' 'verify' '-DskipTests' `
        "-Daxiom.proguardJdkHome=$pgJdkFwd" `
        "-Daxiom.jpackageHome=$jpJdkFwd"
    if ($LASTEXITCODE -ne 0) {
        Write-Err "Installer build failed (exit $LASTEXITCODE)"
        exit 1
    }

    # Report the produced .exe and open its folder
    $exes = @(Get-ChildItem 'axiom-launcher\dist\*.exe' -File -ErrorAction SilentlyContinue `
        | Sort-Object LastWriteTime -Descending)
    if ($exes.Count -eq 0) {
        Write-Warn "Build succeeded but no .exe found in axiom-launcher\target\dist\"
    } else {
        $exe       = $exes[0]
        $exeSizeMb = [math]::Round($exe.Length / 1MB, 1)
        Write-Ok ""
        Write-Ok "  Installer: $($exe.Name) ($exeSizeMb MB)"
        Write-Ok "  Path:      $($exe.FullName)"
        Start-Process explorer.exe -ArgumentList (Split-Path $exe.FullName)
    }

    $elapsed = (Get-Date) - $started
    Write-Ok ("Done in {0:N1}s" -f $elapsed.TotalSeconds)
    exit 0
}

# ── 1. Build ──────────────────────────────────────────────────────────────────
Write-Step "[1/3] Building (Maven)..."
$mvnArgs = @('clean', 'package')
if ($SkipTests) { $mvnArgs += '-DskipTests' }
if ($Release)   { $mvnArgs += @('-P', 'release') }

& mvn @mvnArgs
if ($LASTEXITCODE -ne 0) {
    Write-Err "Build failed (exit $LASTEXITCODE)"
    exit 1
}

# ── 2. Locate + deploy JAR ────────────────────────────────────────────────────
Write-Step "[2/3] Deploying..."

# @() forces array context — required under Set-StrictMode -Version Latest
# so single-item results still expose .Count and indexed access.
$candidates = @(Get-ChildItem -Path "axiom-plugin\target\axiom-plugin-*.jar" -File `
    | Where-Object { $_.Name -notmatch '(sources|javadoc|original)\.jar$' } `
    | Sort-Object LastWriteTime -Descending)

if ($candidates.Count -eq 0) {
    Write-Err "No axiom-plugin JAR found in axiom-plugin\target\"
    exit 1
}
$jar = $candidates[0]

$sideloadDir = Join-Path $env:USERPROFILE '.runelite\sideloaded-plugins'
if (-not (Test-Path $sideloadDir)) {
    New-Item -ItemType Directory -Path $sideloadDir -Force | Out-Null
}

# Remove any previous Axiom JARs so multiple builds don't pile up.
Get-ChildItem $sideloadDir -Filter 'axiom-plugin-*.jar' -File -ErrorAction SilentlyContinue `
    | ForEach-Object {
        Write-Warn "  Removing previous: $($_.Name)"
        Remove-Item -LiteralPath $_.FullName -Force
    }

$dest = Join-Path $sideloadDir $jar.Name
Copy-Item -LiteralPath $jar.FullName -Destination $dest -Force

$count = @(Get-ChildItem $sideloadDir -Filter 'axiom-plugin-*.jar' -File).Count
if ($count -ne 1) {
    Write-Warn "WARNING: $count Axiom JARs in sideload folder (expected 1)"
}

$sizeKb = [math]::Round($jar.Length / 1KB)
Write-Ok "  Deployed: $($jar.Name) ($sizeKb KB)"
Write-Ok "  Path:     $dest"

# ── 3. Launch RuneLite ────────────────────────────────────────────────────────
if ($NoLaunch) {
    Write-Warn "[3/3] -NoLaunch supplied; skipping RuneLite launch"
} else {
    Write-Step "[3/3] Launching RuneLite (developer-mode)..."
    if (-not (Test-Path $RuneLiteJar)) {
        Write-Err "RuneLite JAR not found: $RuneLiteJar"
        Write-Err "Pass -RuneLiteJar <path> or set the default in dev.ps1"
        exit 1
    }
    Start-Process -FilePath 'java' `
        -ArgumentList @('-ea', '-jar', $RuneLiteJar, '--developer-mode') `
        -WorkingDirectory (Split-Path -Parent $RuneLiteJar) | Out-Null
    Write-Ok "  RuneLite launched. Logs appear in the RuneLite console window."
}

$elapsed = (Get-Date) - $started
Write-Ok ("Done in {0:N1}s" -f $elapsed.TotalSeconds)
