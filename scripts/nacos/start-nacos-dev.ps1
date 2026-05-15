param(
    [string]$NacosHome = $(if ($env:NACOS_HOME) { $env:NACOS_HOME } else { "C:\my-claude\comware\nacos-server-2.5.2" }),
    [int]$Port = 8848,
    [switch]$ImportConfig
)

$ErrorActionPreference = "Stop"

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
    $importScript = Join-Path (Resolve-Path (Join-Path $PSScriptRoot "..\..")) "scripts\nacos\import-nacos-config.ps1"
    & $importScript
}

