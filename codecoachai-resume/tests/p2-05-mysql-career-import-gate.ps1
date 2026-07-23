[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^codecoachai_p205_[a-z0-9_]{1,40}$')]
    [string]$Database,

    [string]$HostName = '127.0.0.1',
    [ValidateRange(1, 65535)]
    [int]$Port = 3306,
    [string]$User = 'root',
    [string]$MysqlExe = 'mysql',
    [switch]$AllowRemoteIsolatedDatabase,
    [switch]$KeepDatabase
)

$ErrorActionPreference = 'Stop'
$allowedLocalHosts = @('127.0.0.1', 'localhost', '::1')
if ($HostName -notin $allowedLocalHosts -and -not $AllowRemoteIsolatedDatabase) {
    throw 'Remote MySQL is disabled by default. Use a dedicated disposable database and pass -AllowRemoteIsolatedDatabase explicitly.'
}
if (-not (Get-Command $MysqlExe -ErrorAction SilentlyContinue)) {
    throw "MySQL client was not found: $MysqlExe"
}

function Invoke-MySql {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Sql,
        [switch]$AllowFailure
    )

    $arguments = @(
        '--protocol=tcp',
        "--host=$HostName",
        "--port=$Port",
        "--user=$User",
        '--batch',
        '--raw',
        '--skip-column-names',
        "--execute=$Sql"
    )
    $output = & $MysqlExe @arguments 2>&1
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0 -and -not $AllowFailure) {
        throw "MySQL command failed with exit code $exitCode`n$($output -join "`n")"
    }
    return [pscustomobject]@{
        ExitCode = $exitCode
        Output = @($output)
    }
}

function Assert-SingleValue {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Sql,
        [Parameter(Mandatory = $true)]
        [string]$Expected,
        [Parameter(Mandatory = $true)]
        [string]$Label
    )

    $result = Invoke-MySql -Sql $Sql
    $actual = ($result.Output | Select-Object -Last 1).ToString().Trim()
    if ($actual -ne $Expected) {
        throw "$Label failed. Expected '$Expected', got '$actual'."
    }
}

$quotedDatabase = "``$Database``"
$created = $false
try {
    $schemaSql = @"
DROP DATABASE IF EXISTS $quotedDatabase;
CREATE DATABASE $quotedDatabase CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
USE $quotedDatabase;
CREATE TABLE career_import_dedupe_guard (
    user_id BIGINT NOT NULL,
    identity_hash CHAR(64) NOT NULL,
    PRIMARY KEY (user_id, identity_hash)
) ENGINE=InnoDB;
CREATE TABLE job_application (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    company_name VARCHAR(120) NULL,
    job_title VARCHAR(120) NOT NULL,
    applied_at DATETIME NULL,
    import_fingerprint CHAR(64) NULL,
    external_ref VARCHAR(80) NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_application_import_fingerprint
        (user_id, import_fingerprint, deleted),
    UNIQUE KEY uk_job_application_external_ref (external_ref)
) ENGINE=InnoDB;
"@
    [void](Invoke-MySql -Sql $schemaSql)
    $created = $true

    $createValues = for ($index = 1; $index -le 500; $index++) {
        "(10,'Company-$index','Backend Engineer $index','2026-07-03 09:00:00',NULL,'create-$index',0)"
    }
    $createSql = @"
USE $quotedDatabase;
INSERT INTO job_application
    (user_id, company_name, job_title, applied_at, import_fingerprint, external_ref, deleted)
VALUES
$($createValues -join ",`n");
"@
    [void](Invoke-MySql -Sql $createSql)
    Assert-SingleValue `
        -Sql "USE $quotedDatabase; SELECT COUNT(*) FROM job_application WHERE external_ref LIKE 'create-%';" `
        -Expected '500' `
        -Label 'CREATE 500-row boundary'
    Assert-SingleValue `
        -Sql "USE $quotedDatabase; SELECT COUNT(*) FROM job_application WHERE external_ref LIKE 'create-%' AND import_fingerprint IS NOT NULL;" `
        -Expected '0' `
        -Label 'CREATE fingerprint contract'

    [void](Invoke-MySql -Sql @"
USE $quotedDatabase;
INSERT INTO job_application
    (user_id, company_name, job_title, applied_at, import_fingerprint, external_ref, deleted)
VALUES
    (10,'Constraint Co','Backend Engineer','2026-07-03 09:00:00',NULL,'unrelated-unique',0);
"@)
    $unrelatedFailure = Invoke-MySql -AllowFailure -Sql @"
USE $quotedDatabase;
INSERT INTO job_application
    (user_id, company_name, job_title, applied_at, import_fingerprint, external_ref, deleted)
VALUES
    (10,'Other Co','Data Engineer','2026-07-04 09:00:00',NULL,'unrelated-unique',0);
"@
    if ($unrelatedFailure.ExitCode -eq 0) {
        throw 'Expected the unrelated unique-key insert to fail.'
    }
    if (($unrelatedFailure.Output -join "`n") -notmatch 'uk_job_application_external_ref') {
        throw 'The unrelated unique-key failure did not preserve its constraint identity.'
    }

    $identityHash = 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
    $fingerprint = 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb'
    $workerSql = @"
USE $quotedDatabase;
SET SESSION innodb_lock_wait_timeout = 5;
START TRANSACTION;
INSERT IGNORE INTO career_import_dedupe_guard (user_id, identity_hash)
VALUES (10, '$identityHash');
SELECT identity_hash
  FROM career_import_dedupe_guard
 WHERE user_id = 10 AND identity_hash = '$identityHash'
 FOR UPDATE;
DO SLEEP(0.25);
INSERT IGNORE INTO job_application
    (user_id, company_name, job_title, applied_at, import_fingerprint, external_ref, deleted)
VALUES
    (10,'Concurrent Co','Platform Engineer','2026-07-10 09:00:00','$fingerprint',NULL,0);
SELECT CONCAT('application_rows=', ROW_COUNT());
COMMIT;
"@
    $jobs = 1..2 | ForEach-Object {
        Start-Job -ScriptBlock {
            param($Exe, $WorkerHost, $WorkerPort, $WorkerUser, $Sql)
            $output = & $Exe `
                '--protocol=tcp' `
                "--host=$WorkerHost" `
                "--port=$WorkerPort" `
                "--user=$WorkerUser" `
                '--batch' `
                '--raw' `
                '--skip-column-names' `
                "--execute=$Sql" 2>&1
            [pscustomobject]@{
                ExitCode = $LASTEXITCODE
                Output = @($output)
            }
        } -ArgumentList $MysqlExe, $HostName, $Port, $User, $workerSql
    }
    try {
        $completed = @(Wait-Job -Job $jobs -Timeout 20)
        if ($completed.Count -ne 2) {
            throw 'Concurrent SKIP gate exceeded the 20-second bounded timeout.'
        }
        $workerResults = @($jobs | Receive-Job)
        if ($workerResults.ExitCode | Where-Object { $_ -ne 0 }) {
            $details = $workerResults.Output -join "`n"
            throw "Concurrent SKIP worker failed.`n$details"
        }
        $rowCounts = @(
            $workerResults.Output |
                ForEach-Object {
                    if ($_ -match '^application_rows=(0|1)$') {
                        [int]$Matches[1]
                    }
                } |
                Sort-Object
        )
        if (($rowCounts -join ',') -ne '0,1') {
            throw "Concurrent SKIP gate expected one insert and one duplicate, got: $($rowCounts -join ',')"
        }
    } finally {
        $jobs | Stop-Job -ErrorAction SilentlyContinue
        $jobs | Remove-Job -Force -ErrorAction SilentlyContinue
    }
    Assert-SingleValue `
        -Sql "USE $quotedDatabase; SELECT COUNT(*) FROM job_application WHERE import_fingerprint = '$fingerprint';" `
        -Expected '1' `
        -Label 'Concurrent SKIP persisted row count'

    Write-Host 'P2-05 isolated MySQL career-import gate passed.'
} finally {
    if ($created -and -not $KeepDatabase) {
        [void](Invoke-MySql -Sql "DROP DATABASE IF EXISTS $quotedDatabase;")
    } elseif ($created) {
        Write-Host "Kept isolated database: $Database"
    }
}
