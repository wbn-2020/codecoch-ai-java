param(
    [string]$NacosAddr = $(if ($env:NACOS_ADDR) { $env:NACOS_ADDR } else { "http://127.0.0.1:8848" }),
    [string]$Group = $(if ($env:NACOS_GROUP) { $env:NACOS_GROUP } else { "DEFAULT_GROUP" }),
    [string]$Namespace = $(if ($env:NACOS_NAMESPACE) { $env:NACOS_NAMESPACE } else { "" }),
    [string]$Username = $(if ($env:NACOS_USERNAME) { $env:NACOS_USERNAME } else { "" }),
    [string]$Password = $(if ($env:NACOS_PASSWORD) { $env:NACOS_PASSWORD } else { "" }),
    [string]$AccessToken = $(if ($env:NACOS_ACCESS_TOKEN) { $env:NACOS_ACCESS_TOKEN } else { "" }),
    [switch]$ConfirmWrite
)

$ErrorActionPreference = "Stop"
$rootDir = Resolve-Path (Join-Path $PSScriptRoot "..\..")
# Official Nacos source directory. config/nacos is kept only as historical/manual templates.
$configDir = Join-Path $rootDir "docs\nacos"

if (-not $ConfirmWrite) {
    Write-Host "DRY-RUN: no config will be written to Nacos."
    Write-Host "Target: $NacosAddr, group: $Group, namespace: $Namespace"
    Write-Host "Run again with -ConfirmWrite only after confirming the target environment."
}

if ($ConfirmWrite -and -not $AccessToken -and $Username -and $Password) {
    $loginBody = @{
        username = $Username
        password = $Password
    }
    $loginResp = Invoke-RestMethod -Method Post -Uri "$NacosAddr/nacos/v1/auth/users/login" -Body $loginBody
    $AccessToken = $loginResp.accessToken
}

$configFiles = Get-ChildItem -Path $configDir -Filter "*.yml" | Sort-Object Name
foreach ($configFile in $configFiles) {
    $dataId = $configFile.Name

    if (-not $ConfirmWrite) {
        Write-Host "DRY-RUN $dataId"
        continue
    }

    $content = Get-Content -LiteralPath $configFile.FullName -Raw -Encoding UTF8
    $body = @{
        dataId = $dataId
        group = $Group
        content = $content
        type = "yaml"
    }
    if ($Namespace) {
        $body.tenant = $Namespace
    }
    if ($AccessToken) {
        $body.accessToken = $AccessToken
    }

    $result = Invoke-RestMethod -Method Post -Uri "$NacosAddr/nacos/v1/cs/configs" -Body $body
    if ($result -eq $true -or $result -eq "true") {
        Write-Host "SUCCESS $dataId"
    } else {
        throw "FAILED ${dataId}: $result"
    }
}
