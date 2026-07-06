param(
    [string]$NacosHome = $(if ($env:NACOS_HOME) { $env:NACOS_HOME } else { "" }),
    [int]$Port = 8848,
    [switch]$Start,
    [switch]$ImportConfig,
    [switch]$ConfirmImport
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
    $importScript = Join-Path (Resolve-Path (Join-Path $PSScriptRoot "..\..")) "scripts\nacos\import-nacos-config.ps1"
    & $importScript -ConfirmWrite
}

