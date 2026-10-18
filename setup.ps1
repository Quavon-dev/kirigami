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
    $ErrorActionPreference = 'Continue'
    foreach ($e in 'docker', 'podman') {
        if (Get-Command $e -ErrorAction SilentlyContinue) {
            & $e info *> $null
            if ($LASTEXITCODE -eq 0) { return $e }
        }
    }
    $null
}

function Write-Utf8($Path, $Text) { [IO.File]::WriteAllText($Path, $Text, (New-Object Text.UTF8Encoding $false)) }

function Ask-Choice($Prompt, $Default, [string[]]$Options) {
    while ($true) {
        $reply = Ask "$Prompt ($($Options -join ' '))" $Default
        if ($Options -ccontains $reply) { return $reply }
        Write-Host "Pick one of: $($Options -join ' ')"
    }
}

function Ask-Number($Prompt, $Default, $Min, $Max) {
    while ($true) {
        $reply = Ask "$Prompt ($Min-$Max)" $Default
        if ($reply -match '^\d{1,9}$' -and [int]$reply -ge $Min -and [int]$reply -le $Max) { return $reply }
        Write-Host "Enter a number from $Min to $Max."
    }
}

function Ask-Text($Prompt, $Default = '') {
    while ($true) {
        $reply = Ask $Prompt $Default
        if ($reply -notmatch '\p{Cc}') { return $reply.Replace('\', '\\') }
        Write-Host 'Control characters are not allowed.'
    }
}

function Find-Uuid($Name) {
    try { $id = (Invoke-RestMethod -UseBasicParsing -TimeoutSec 15 "https://api.mojang.com/users/profiles/minecraft/$Name").id } catch { return $null }
    if ($id -notmatch '^[0-9a-f]{32}$') { return $null }
    '{0}-{1}-{2}-{3}-{4}' -f $id.Substring(0, 8), $id.Substring(8, 4), $id.Substring(12, 4), $id.Substring(16, 4), $id.Substring(20)
}

function Write-ServerFiles($Dir, $ServerPort) {
    $props = Join-Path $Dir 'server.properties'
    if (Test-Path $props) { Info 'Keeping existing server.properties' } else {
        Write-Utf8 $props ((@(
            "server-port=$ServerPort", "motd=$motd", "max-players=$maxPlayers", "difficulty=$difficulty", "gamemode=$gamemode",
            "pvp=$pvp", "online-mode=$onlineMode", "view-distance=$viewDistance", "simulation-distance=$simDistance",
            "level-seed=$seed", "white-list=$whitelist", "enforce-whitelist=$whitelist"
        ) -join "`n") + "`n")
    }
    if ($ops.Count -eq 0) { return }
    $opsFile = Join-Path $Dir 'ops.json'
    if (Test-Path $opsFile) { Info 'Keeping existing ops.json' } else {
        Write-Utf8 $opsFile ("[`n" + (($ops | ForEach-Object { "  {`"uuid`": `"$($_.Uuid)`", `"name`": `"$($_.Name)`", `"level`": 4, `"bypassesPlayerLimit`": false}" }) -join ",`n") + "`n]`n")
    }
    $whitelistFile = Join-Path $Dir 'whitelist.json'
    if ($whitelist -eq 'true' -and -not (Test-Path $whitelistFile)) {
        Write-Utf8 $whitelistFile ("[`n" + (($ops | ForEach-Object { "  {`"uuid`": `"$($_.Uuid)`", `"name`": `"$($_.Name)`"}" }) -join ",`n") + "`n]`n")
    }
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
        $url = "https://github.com/$Repo/releases/download/v$tag"
    }
}

$totalMb = [math]::Floor((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1MB)
$ramGb = [math]::Min([math]::Max([math]::Floor($totalMb / 2048), 2), 16)
$memory = Ask 'Heap size' "${ramGb}G"
if ($memory -cnotmatch '^\d{1,7}[MG]$') { Fail 'heap size must look like 8G or 4096M' }
$heapMb = [int]$memory.Substring(0, $memory.Length - 1)
if ($memory.EndsWith('G')) { $heapMb *= 1024 }
if ($heapMb -lt 1024) { Fail 'heap size must be at least 1G' }
if ($heapMb -gt $totalMb * 0.8) { Write-Host "warning: $memory is more than 80% of the ${totalMb}M RAM in this machine" -ForegroundColor Yellow }
$port = Ask-Number 'Port' '25565' 1 65535

Write-Host "`nServer settings (press Enter to keep a default):"
$motd = Ask-Text 'Message of the day' 'A Kirigami Server'
$maxPlayers = Ask-Number 'Max players' '20' 1 1000
$difficulty = Ask-Choice 'Difficulty' 'normal' @('peaceful', 'easy', 'normal', 'hard')
$gamemode = Ask-Choice 'Game mode' 'survival' @('survival', 'creative', 'adventure', 'spectator')
$pvp = Ask-Choice 'PvP' 'true' @('true', 'false')
$viewDistance = Ask-Number 'View distance' '10' 3 32
$simDistance = Ask-Number 'Simulation distance' '10' 3 32
$seed = Ask-Text 'World seed (empty for random)'
$onlineMode = Ask-Choice 'Online mode (verify accounts with Mojang)' 'true' @('true', 'false')
if ($onlineMode -eq 'false') { Write-Host 'warning: offline mode lets anyone join with any name; only use it behind a proxy that authenticates players' -ForegroundColor Yellow }
$whitelist = Ask-Choice 'Whitelist (only listed players can join)' 'false' @('true', 'false')

$ops = @()
if ($onlineMode -eq 'true') {
    foreach ($opName in ((Ask 'Operator player names, separated by spaces (empty for none)') -split '[\s,]+' | Where-Object { $_ })) {
        if ($opName -notmatch '^[A-Za-z0-9_]{3,16}$') { Write-Host "warning: skipping invalid player name '$opName'" -ForegroundColor Yellow; continue }
        $uuid = Find-Uuid $opName
        if ($uuid) { $ops += [pscustomobject]@{ Name = $opName; Uuid = $uuid }; Info "Operator $opName ($uuid)" }
        else { Write-Host "warning: no Minecraft account named '$opName' found; run 'op $opName' in the console later" -ForegroundColor Yellow }
    }
} else { Info "Offline mode: add operators with 'op <name>' in the server console." }

$tmp = Join-Path ([IO.Path]::GetTempPath()) ([IO.Path]::GetRandomFileName())
New-Item -ItemType Directory -Path $tmp | Out-Null
try {
    if ($method -like '*container') {
        $name = Ask 'Container name' 'kirigami'
        if ($name -notmatch '^[a-zA-Z0-9][a-zA-Z0-9_.-]*$') { Fail "invalid container name '$name'" }
        & { $ErrorActionPreference = 'Continue'; & $engine container inspect $name *> $null }
        if ($LASTEXITCODE -eq 0) { Fail "a container named '$name' already exists" }
        $volume = Ask 'Data volume name' "$name-data"
        if ($volume -notmatch '^[a-zA-Z0-9][a-zA-Z0-9_.-]*$') { Fail "invalid volume name '$volume'" }
        Info "Pulling ${Image}:$tag"
        & $engine pull "${Image}:$tag"
        if ($LASTEXITCODE -ne 0) { Fail "no $tag image found; see https://github.com/$Repo/releases" }
        Write-ServerFiles $tmp 25565 | Out-Null
        & $engine run --rm -v "${volume}:/data" -v "${tmp}:/seed:ro" --entrypoint sh "${Image}:$tag" -c 'for f in /seed/*; do [ -e "/data/${f##*/}" ] && echo "Keeping existing ${f##*/}" || cp "$f" /data/; done'
        if ($LASTEXITCODE -ne 0) { Fail 'could not write the server settings into the volume' }
        & $engine run -d -it --name $name --restart unless-stopped -p "${port}:25565" -e EULA=true -e "MEMORY=$memory" -v "${volume}:/data" "${Image}:$tag"
        if ($LASTEXITCODE -ne 0) { Fail 'container did not start' }
        Info "Server is starting. Console log: $engine logs -f $name"
        Info "Console: $engine attach $name (detach with Ctrl-P Ctrl-Q), stop with $engine stop $name"
        Info "Settings live in the '$volume' volume (server.properties, config/kirigami.yml)."
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

Write-Utf8 (Join-Path $dir 'eula.txt') "eula=true`n"
Write-ServerFiles $dir $port

$start = Join-Path $dir 'start.bat'
Write-Utf8 $start "@echo off`r`ncd /d `"%~dp0`"`r`n`"$java`" -Xms$memory -Xmx$memory $G1Flags -jar kirigami.jar --nogui %*`r`n"
Info "Installed to $dir. Start it with: $start"

$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if ($isAdmin -and (Confirm "Allow port $port/tcp in Windows Firewall?")) {
    New-NetFirewallRule -DisplayName "Kirigami $port" -Direction Inbound -Protocol TCP -LocalPort $port -Action Allow | Out-Null
    Info "Firewall rule 'Kirigami $port' added."
} else {
    Info "Allow port $port/tcp in Windows Firewall if players connect from outside (needs an administrator terminal)."
}

if (Confirm 'Start the server now?') { & $start }
