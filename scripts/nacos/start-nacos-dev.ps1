param(
    [string]$NacosHome = $(if ($env:NACOS_HOME) { $env:NACOS_HOME } else { "" }),
    [int]$Port = 8848,
    [string]$Namespace = $(if ($env:NACOS_NAMESPACE) { $env:NACOS_NAMESPACE } else { "" }),
    [ValidateSet("namespace")]
    [string]$Target = "namespace",
    [switch]$Start,
    [switch]$ImportConfig,
    [switch]$ConfirmImport,
    [switch]$AllowCreateConfig
)

$ErrorActionPreference = "Stop"

if (-not $NacosHome) {
    throw "Nacos home is not configured. Set NACOS_HOME or pass -NacosHome."
}

if (-not (Test-Path -LiteralPath $NacosHome)) {
    throw "Nacos home not found: $NacosHome"
}

$startup = Join-Path $NacosHome "bin\startup.cmd"
if (-not (Test-Path -LiteralPath $startup)) {
    throw "Nacos startup script not found: $startup"
}

$listening = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue
if ($listening) {
    Write-Host "Nacos already listens on port $Port, process=$($listening[0].OwningProcess)"
} else {
    if (-not $Start) {
        Write-Host "Nacos is not listening on port $Port. Dry run only; pass -Start to launch it."
        return
    }

    $command = "set JAVA_TOOL_OPTIONS=--add-opens=java.base/java.io=ALL-UNNAMED && `"$startup`" -m standalone"
    Start-Process -FilePath "cmd.exe" `
        -ArgumentList "/c", $command `
        -WorkingDirectory (Join-Path $NacosHome "bin") `
        -WindowStyle Hidden

    $deadline = (Get-Date).AddSeconds(90)
    do {
        Start-Sleep -Seconds 3
        $listening = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue
    } while (-not $listening -and (Get-Date) -lt $deadline)

    if (-not $listening) {
        $logPath = Join-Path $NacosHome "logs\nacos.log"
        throw "Nacos did not listen on port $Port within 90 seconds. Check log: $logPath"
    }

    Write-Host "Nacos started on port $Port, process=$($listening[0].OwningProcess)"
}

if ($ImportConfig) {
    if (-not $ConfirmImport) {
        throw "ImportConfig writes docs/nacos/*.yml into Nacos. Re-run with -ImportConfig -ConfirmImport after checking address, namespace and group."
    }
    if (-not $Namespace -or $Namespace -eq "public") {
        throw "ImportConfig requires a non-empty dedicated -Namespace value other than public."
    }
    $profile = if ($env:SPRING_PROFILES_ACTIVE) { $env:SPRING_PROFILES_ACTIVE } else { "dev" }
    if ($profile -notmatch '^[A-Za-z0-9_-]+$') {
        throw "SPRING_PROFILES_ACTIVE must be one simple profile name."
    }
    $currentDataIds = @(
        "codecoachai-common-$profile.yml",
        "codecoachai-redis-$profile.yml",
        "codecoachai-gateway-$profile.yml",
        "codecoachai-core-$profile.yml",
        "codecoachai-ai-$profile.yml",
        "codecoachai-search-$profile.yml"
    )
    $importScript = Join-Path (Resolve-Path (Join-Path $PSScriptRoot "..\..")) "scripts\nacos\import-nacos-config.ps1"
    & $importScript `
        -Namespace $Namespace `
        -Target $Target `
        -DataId $currentDataIds `
        -ConfirmWrite `
        -AllowCreateConfig:$AllowCreateConfig `
        -CreateMissingOnly
}

