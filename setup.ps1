#Requires -Version 5.1
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$Repo = 'Quavon-dev/kirigami'
$Image = 'ghcr.io/quavon-dev/kirigami'
$G1Flags = '-XX:+AlwaysPreTouch -XX:+DisableExplicitGC -XX:+ParallelRefProcEnabled -XX:+PerfDisableSharedMem -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:G1HeapRegionSize=8M -XX:G1HeapWastePercent=5 -XX:G1MaxNewSizePercent=40 -XX:G1MixedGCCountTarget=4 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1NewSizePercent=30 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:G1ReservePercent=20 -XX:InitiatingHeapOccupancyPercent=15 -XX:MaxGCPauseMillis=200 -XX:MaxTenuringThreshold=1 -XX:SurvivorRatio=32'

function Fail($Message) { Write-Host "error: $Message" -ForegroundColor Red; exit 1 }
function Info($Message) { Write-Host "==> $Message" }

function Ask($Prompt, $Default = '') {
    $label = if ($Default) { "$Prompt [$Default]" } else { $Prompt }
    $reply = Read-Host $label
    if ([string]::IsNullOrWhiteSpace($reply)) { $Default } else { $reply.Trim() }
}

function Confirm($Prompt) { (Ask "$Prompt (y/n)" 'n') -match '^(y|yes)$' }

function Choose($Prompt, [string[]]$Options) {
    for ($i = 0; $i -lt $Options.Count; $i++) { Write-Host "  $($i + 1)) $($Options[$i])" }
    while ($true) {
        $reply = Ask $Prompt '1'
        if ($reply -match '^\d+$' -and [int]$reply -ge 1 -and [int]$reply -le $Options.Count) { return $Options[[int]$reply - 1] }
        Write-Host "Pick a number from 1 to $($Options.Count)."
    }
}

function Get-JavaMajor($Path) {
    $ErrorActionPreference = 'Continue'
    $out = & $Path -version 2>&1 | Out-String
    if ($out -match 'version "(\d+)') { [int]$Matches[1] } else { 0 }
}

function Find-Java {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += Join-Path $env:JAVA_HOME 'bin\java.exe' }
    $onPath = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($onPath) { $candidates += $onPath.Source }
    foreach ($root in @("$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Java", "$env:ProgramFiles\Microsoft")) {
        if (Test-Path $root) { $candidates += Get-ChildItem $root -Directory | ForEach-Object { Join-Path $_.FullName 'bin\java.exe' } }
    }
    foreach ($c in $candidates) {
        if ((Test-Path $c) -and (Get-JavaMajor $c) -ge 25) { return $c }
    }
    $null
}

function Find-Engine {
    foreach ($e in 'docker', 'podman') {
        if (Get-Command $e -ErrorAction SilentlyContinue) {
            & $e info *> $null
            if ($LASTEXITCODE -eq 0) { return $e }
        }
    }
    $null
}

Write-Host "Kirigami server setup`n"

$engine = Find-Engine
$java = Find-Java
$methods = @()
if ($engine) { $methods += "$engine container" } else { Info 'No running Docker or Podman found; container setup is not offered.' }
$plain = 'plain jar in a directory'
if ($java) { $plain += " (found Java: $java)" }
$methods += $plain
Write-Host 'How do you want to run the server?'
$method = Choose 'Method' $methods

Write-Host "`nMinecraft requires you to accept its EULA: https://aka.ms/MinecraftEULA"
if ((Ask "Type 'yes' to accept the EULA") -ne 'yes') { Fail 'the EULA was not accepted' }

Write-Host "`nRelease channel:"
$channel = Choose 'Channel' @('stable', 'edge (every commit on main, untested)', 'exact release tag')
switch -Wildcard ($channel) {
    'stable' { $tag = 'latest'; $url = "https://github.com/$Repo/releases/latest/download" }
    'edge*' { $tag = 'edge'; $url = "https://github.com/$Repo/releases/download/edge" }
    default {
        $tag = Ask 'Release tag (for example 26.3-4)'
        if ($tag -notmatch '^\d+(\.\d+)*-\d+$') { Fail "invalid tag '$tag'" }
        $url = "https://github.com/$Repo/releases/download/$tag"
    }
}

$ramGb = [math]::Floor((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 2GB)
$ramGb = [math]::Min([math]::Max($ramGb, 2), 16)
$memory = Ask 'Heap size' "${ramGb}G"
if ($memory -notmatch '^\d+[MG]$') { Fail 'heap size must look like 8G or 4096M' }
$port = Ask 'Port' '25565'
if ($port -notmatch '^\d+$' -or [int]$port -lt 1 -or [int]$port -gt 65535) { Fail "invalid port '$port'" }

if ($method -like '*container') {
    $name = Ask 'Container name' 'kirigami'
    if ($name -notmatch '^[a-zA-Z0-9][a-zA-Z0-9_.-]*$') { Fail "invalid container name '$name'" }
    & $engine container inspect $name *> $null
    if ($LASTEXITCODE -eq 0) { Fail "a container named '$name' already exists" }
    $volume = Ask 'Data volume name' "$name-data"
    if ($volume -notmatch '^[a-zA-Z0-9][a-zA-Z0-9_.-]*$') { Fail "invalid volume name '$volume'" }
    Info "Pulling ${Image}:$tag"
    & $engine pull "${Image}:$tag"
    if ($LASTEXITCODE -ne 0) { Fail "no $tag image found; see https://github.com/$Repo/releases" }
    & $engine run -d -it --name $name --restart unless-stopped -p "${port}:25565" -e EULA=true -e "MEMORY=$memory" -v "${volume}:/data" "${Image}:$tag"
    if ($LASTEXITCODE -ne 0) { Fail 'container did not start' }
    Info "Server is starting. Console log: $engine logs -f $name"
    Info "Commands: $engine attach $name (detach with Ctrl-P Ctrl-Q), stop with $engine stop $name"
    exit 0
}

if (-not $java) {
    if ((Get-Command winget -ErrorAction SilentlyContinue) -and (Confirm "Java 25 is missing. Install it with 'winget install --exact --id EclipseAdoptium.Temurin.25.JDK'?")) {
        winget install --exact --id EclipseAdoptium.Temurin.25.JDK
        $java = Find-Java
    }
    if (-not $java) { Fail 'Java 25 or newer is required. Get it from https://adoptium.net, open a new terminal and run this script again.' }
}
Info "Using Java $(Get-JavaMajor $java) at $java"

$dir = Ask 'Server directory' (Join-Path $HOME 'kirigami')
New-Item -ItemType Directory -Force -Path $dir | Out-Null
$dir = (Resolve-Path $dir).Path
$jar = Join-Path $dir 'kirigami.jar'
if ((Test-Path $jar) -and -not (Confirm "$jar exists. Replace the jar (world data is kept)?")) { Fail 'aborted' }

$tmp = Join-Path ([IO.Path]::GetTempPath()) ([IO.Path]::GetRandomFileName())
New-Item -ItemType Directory -Path $tmp | Out-Null
try {
    Info "Downloading kirigami.jar ($tag)"
    $ProgressPreference = 'SilentlyContinue'
    try {
        Invoke-WebRequest -UseBasicParsing -Uri "$url/kirigami.jar" -OutFile "$tmp\kirigami.jar"
        Invoke-WebRequest -UseBasicParsing -Uri "$url/kirigami.jar.sha256" -OutFile "$tmp\kirigami.jar.sha256"
    } catch { Fail "no $tag release found; see https://github.com/$Repo/releases" }
    $expected = ((Get-Content "$tmp\kirigami.jar.sha256" -Raw).Trim() -split '\s+')[0]
    $actual = (Get-FileHash "$tmp\kirigami.jar" -Algorithm SHA256).Hash
    if ($actual -ne $expected) { Fail 'checksum mismatch; the download is not trusted' }
    Info 'Checksum verified'
    Move-Item -Force "$tmp\kirigami.jar" $jar
} finally {
    Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
}

Set-Content -Encoding ASCII -Path (Join-Path $dir 'eula.txt') -Value 'eula=true'
$props = Join-Path $dir 'server.properties'
if (Test-Path $props) { Info 'Keeping existing server.properties' } else { Set-Content -Encoding ASCII -Path $props -Value "server-port=$port" }

$start = Join-Path $dir 'start.bat'
Set-Content -Encoding ASCII -Path $start -Value "@echo off`r`ncd /d `"%~dp0`"`r`n`"$java`" -Xms$memory -Xmx$memory $G1Flags -jar kirigami.jar --nogui %*`r`n"

Info "Installed to $dir. Start it with: $start"
Info "Allow port $port/tcp in Windows Firewall if players connect from outside."
if (Confirm 'Start the server now?') { & $start }
