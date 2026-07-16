param(
    [string]$NacosAddr = $(if ($env:NACOS_ADDR) { $env:NACOS_ADDR } else { "http://127.0.0.1:8848" }),
    [string]$Group = $(if ($env:NACOS_GROUP) { $env:NACOS_GROUP } else { "DEFAULT_GROUP" }),
    [string]$Namespace = $(if ($env:NACOS_NAMESPACE) { $env:NACOS_NAMESPACE } else { "" }),
    [string]$Username = $(if ($env:NACOS_USERNAME) { $env:NACOS_USERNAME } else { "" }),
    [string]$Password = $(if ($env:NACOS_PASSWORD) { $env:NACOS_PASSWORD } else { "" }),
    [string]$AccessToken = $(if ($env:NACOS_ACCESS_TOKEN) { $env:NACOS_ACCESS_TOKEN } else { "" }),
    [ValidateSet("auto", "builtin-public", "literal-public", "mirror-public", "namespace")]
    [string]$Target = $(if ($env:NACOS_TARGET) { $env:NACOS_TARGET } else { "auto" }),
    [string]$AuditDir = $(if ($env:NACOS_AUDIT_DIR) { $env:NACOS_AUDIT_DIR } else { "" }),
    [string[]]$DataId = @(),
    [switch]$ConfirmWrite,
    [switch]$AllowCreateConfig
)

$ErrorActionPreference = "Stop"
$rootDir = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$configDir = Join-Path $rootDir "docs\nacos"
$guardScript = Join-Path $PSScriptRoot "nacos_config_guard.py"

if (-not (Test-Path -LiteralPath $guardScript)) {
    throw "Nacos config guard not found: $guardScript"
}

function Test-Python3Command {
    param(
        [System.Management.Automation.CommandInfo]$Command,
        [string[]]$Prefix
    )
    if (-not $Command) {
        return $false
    }
    & $Command.Source @Prefix -c "import sys; raise SystemExit(sys.version_info < (3, 9))" *> $null
    return $LASTEXITCODE -eq 0
}

$pythonCommand = Get-Command python -ErrorAction SilentlyContinue
$pythonPrefix = @()
if (-not (Test-Python3Command -Command $pythonCommand -Prefix $pythonPrefix)) {
    $pythonCommand = Get-Command py -ErrorAction SilentlyContinue
    $pythonPrefix = @("-3")
}
if (-not (Test-Python3Command -Command $pythonCommand -Prefix $pythonPrefix)) {
    throw "Python 3 is required to run $guardScript"
}

if ($ConfirmWrite -and -not $AuditDir) {
    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $AuditDir = Join-Path ([System.IO.Path]::GetTempPath()) "codecoachai-nacos-audit-$timestamp"
}

$mode = if ($ConfirmWrite) { "publish" } else { "audit" }
$arguments = @(
    $guardScript,
    $mode,
    "--nacos-addr", $NacosAddr,
    "--group", $Group,
    "--config-dir", $configDir,
    "--target", $Target
)
if ($Namespace) {
    $arguments += @("--namespace-id", $Namespace)
}
if ($AuditDir) {
    $arguments += @("--audit-dir", $AuditDir)
}
foreach ($selectedDataId in $DataId) {
    $arguments += @("--data-id", $selectedDataId)
}
if ($ConfirmWrite) {
    $arguments += "--confirm-write"
}
if ($AllowCreateConfig) {
    $arguments += "--allow-create-config"
}

if (-not $ConfirmWrite) {
    Write-Host "DRY-RUN: exact namespace audit only; no Nacos config will be written."
} else {
    Write-Host "WRITE ENABLED: CAS publish with exact namespace readback."
    Write-Host "Audit directory: $AuditDir"
}
Write-Host "Target: $NacosAddr, group: $Group, selector: $Target, namespaceId: $Namespace"

$previousUsername = $env:NACOS_USERNAME
$previousPassword = $env:NACOS_PASSWORD
$previousToken = $env:NACOS_ACCESS_TOKEN
try {
    $env:NACOS_USERNAME = $Username
    $env:NACOS_PASSWORD = $Password
    $env:NACOS_ACCESS_TOKEN = $AccessToken
    & $pythonCommand.Source @pythonPrefix @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Nacos config guard failed with exit code $LASTEXITCODE"
    }
} finally {
    $env:NACOS_USERNAME = $previousUsername
    $env:NACOS_PASSWORD = $previousPassword
    $env:NACOS_ACCESS_TOKEN = $previousToken
}
