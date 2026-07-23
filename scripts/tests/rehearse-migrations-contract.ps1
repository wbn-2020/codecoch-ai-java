$ErrorActionPreference = 'Stop'

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$scriptPath = Join-Path $repoRoot 'scripts\rehearse-migrations.sh'
$verifySqlPath = Join-Path $repoRoot 'scripts\verify-migration-schema.sql'
$operationsDocPath = Join-Path $repoRoot 'docs\operations\isolated-migration-rehearsal.md'
$initSqlPath = Join-Path $repoRoot 'sql\init.sql'
$v3017Path = Join-Path $repoRoot 'sql\migration\V3_017__be09_be10_recommendation_practice_resume_apply.sql'

foreach ($requiredPath in @(
    $scriptPath,
    $verifySqlPath,
    $operationsDocPath,
    $initSqlPath,
    $v3017Path
)) {
    if (-not (Test-Path -LiteralPath $requiredPath)) {
        throw "Missing migration rehearsal contract input: $requiredPath"
    }
}

$script = Get-Content -Raw -Encoding UTF8 $scriptPath
$verifySql = Get-Content -Raw -Encoding UTF8 $verifySqlPath
$operationsDoc = Get-Content -Raw -Encoding UTF8 $operationsDocPath
$initSql = Get-Content -Raw -Encoding UTF8 $initSqlPath
$v3017Sql = Get-Content -Raw -Encoding UTF8 $v3017Path
$failures = [System.Collections.Generic.List[string]]::new()

function Get-ShellContractText {
    param([string]$Content)

    $activeLines = foreach ($line in ($Content -split "\r?\n")) {
        $builder = [System.Text.StringBuilder]::new()
        $inSingleQuote = $false
        $inDoubleQuote = $false
        $escaped = $false

        for ($index = 0; $index -lt $line.Length; $index++) {
            $character = $line[$index]
            if ($escaped) {
                [void]$builder.Append($character)
                $escaped = $false
                continue
            }
            if ($character -eq '\' -and -not $inSingleQuote) {
                [void]$builder.Append($character)
                $escaped = $true
                continue
            }
            if ($character -eq "'" -and -not $inDoubleQuote) {
                $inSingleQuote = -not $inSingleQuote
                [void]$builder.Append($character)
                continue
            }
            if ($character -eq '"' -and -not $inSingleQuote) {
                $inDoubleQuote = -not $inDoubleQuote
                [void]$builder.Append($character)
                continue
            }
            if ($character -eq '#' -and -not $inSingleQuote -and
                -not $inDoubleQuote -and
                ($index -eq 0 -or [char]::IsWhiteSpace($line[$index - 1]))) {
                break
            }
            [void]$builder.Append($character)
        }
        $builder.ToString()
    }
    return $activeLines -join "`n"
}

function Get-SqlContractText {
    param([string]$Content)

    $withoutBlockComments = [regex]::Replace(
        $Content,
        '(?s)/\*.*?\*/',
        ''
    )
    $activeLines = foreach ($line in ($withoutBlockComments -split "\r?\n")) {
        $builder = [System.Text.StringBuilder]::new()
        $inSingleQuote = $false
        $inDoubleQuote = $false
        $inBacktick = $false

        for ($index = 0; $index -lt $line.Length; $index++) {
            $character = $line[$index]
            if ($character -eq "'" -and -not $inDoubleQuote -and
                -not $inBacktick) {
                if ($inSingleQuote -and $index + 1 -lt $line.Length -and
                    $line[$index + 1] -eq "'") {
                    [void]$builder.Append("''")
                    $index++
                    continue
                }
                $inSingleQuote = -not $inSingleQuote
                [void]$builder.Append($character)
                continue
            }
            if ($character -eq '"' -and -not $inSingleQuote -and
                -not $inBacktick) {
                $inDoubleQuote = -not $inDoubleQuote
                [void]$builder.Append($character)
                continue
            }
            if ($character -eq '`' -and -not $inSingleQuote -and
                -not $inDoubleQuote) {
                $inBacktick = -not $inBacktick
                [void]$builder.Append($character)
                continue
            }
            if (-not $inSingleQuote -and -not $inDoubleQuote -and
                -not $inBacktick) {
                if ($character -eq '#') {
                    break
                }
                if ($character -eq '-' -and
                    $index + 1 -lt $line.Length -and
                    $line[$index + 1] -eq '-' -and
                    ($index + 2 -ge $line.Length -or
                     [char]::IsWhiteSpace($line[$index + 2]))) {
                    break
                }
            }
            [void]$builder.Append($character)
        }
        $builder.ToString()
    }
    return $activeLines -join "`n"
}

function Get-ShellLogicalCommands {
    param([string]$Content)

    $commands = [System.Collections.Generic.List[string]]::new()
    $builder = [System.Text.StringBuilder]::new()

    foreach ($line in ($Content -split "\r?\n")) {
        $trimmedEnd = $line.TrimEnd()
        if ($trimmedEnd.EndsWith('\')) {
            [void]$builder.Append(
                $trimmedEnd.Substring(0, $trimmedEnd.Length - 1)
            )
            [void]$builder.Append(' ')
            continue
        }

        [void]$builder.Append($line)
        $command = $builder.ToString().Trim()
        if ($command.Length -gt 0) {
            $commands.Add($command)
        }
        [void]$builder.Clear()
    }

    $remaining = $builder.ToString().Trim()
    if ($remaining.Length -gt 0) {
        $commands.Add($remaining)
    }
    return $commands
}

function Get-ShellFunctionBlock {
    param(
        [string]$Content,
        [string]$Name
    )

    $pattern = '(?ms)^' + [regex]::Escape($Name) + '\(\) \{\r?\n.*?^\}'
    $match = [regex]::Match($Content, $pattern)
    if (-not $match.Success) {
        return $null
    }
    return $match.Value
}

function Split-ShellCommandSegments {
    param([string]$Command)

    $segments = [System.Collections.Generic.List[string]]::new()
    $builder = [System.Text.StringBuilder]::new()
    $inSingleQuote = $false
    $inDoubleQuote = $false
    $escaped = $false

    $flush = {
        $segment = $builder.ToString().Trim()
        if ($segment.Length -gt 0) {
            $segments.Add($segment)
        }
        [void]$builder.Clear()
    }

    for ($index = 0; $index -lt $Command.Length; $index++) {
        $character = $Command[$index]
        if ($escaped) {
            [void]$builder.Append($character)
            $escaped = $false
            continue
        }
        if ($character -eq '\' -and -not $inSingleQuote) {
            [void]$builder.Append($character)
            $escaped = $true
            continue
        }
        if ($character -eq "'" -and -not $inDoubleQuote) {
            $inSingleQuote = -not $inSingleQuote
            [void]$builder.Append($character)
            continue
        }
        if ($character -eq '"' -and -not $inSingleQuote) {
            $inDoubleQuote = -not $inDoubleQuote
            [void]$builder.Append($character)
            continue
        }
        if (-not $inSingleQuote -and -not $inDoubleQuote) {
            if ($character -eq ';' -or $character -eq '|') {
                & $flush
                if ($index + 1 -lt $Command.Length -and
                    $Command[$index + 1] -eq $character) {
                    $index++
                }
                continue
            }
            if ($character -eq '&' -and
                $index + 1 -lt $Command.Length -and
                $Command[$index + 1] -eq '&') {
                & $flush
                $index++
                continue
            }
            if ($character -eq '&') {
                $previousCharacter = if ($index -gt 0) {
                    $Command[$index - 1]
                } else {
                    [char]0
                }
                $nextCharacter = if ($index + 1 -lt $Command.Length) {
                    $Command[$index + 1]
                } else {
                    [char]0
                }
                if ($previousCharacter -ne '>' -and
                    $nextCharacter -ne '>') {
                    & $flush
                    continue
                }
            }
        }
        [void]$builder.Append($character)
    }

    & $flush
    return $segments
}

function Get-DockerCommandSegments {
    param([string]$Content)

    $dockerCommands = [System.Collections.Generic.List[string]]::new()
    foreach ($logicalCommand in (Get-ShellLogicalCommands -Content $Content)) {
        foreach ($rawSegment in (Split-ShellCommandSegments -Command $logicalCommand)) {
            $segment = $rawSegment.Trim()
            do {
                $previous = $segment
                $segment = [regex]::Replace(
                    $segment,
                    '^(?:if|then|elif|while|until|do|else)\s+',
                    ''
                ).TrimStart()
                $segment = [regex]::Replace(
                    $segment,
                    '^(?:!|\{|\})\s*',
                    ''
                ).TrimStart()
                $segment = [regex]::Replace(
                    $segment,
                    '^command\s+',
                    ''
                ).TrimStart()
                $segment = [regex]::Replace(
                    $segment,
                    '^env(?:\s+(?:-[^\s]+|[A-Za-z_][A-Za-z0-9_]*=' +
                        '(?:"[^"]*"|''[^'']*''|[^\s]+)))*\s+',
                    ''
                ).TrimStart()
            } while ($segment -ne $previous)

            if ($segment -match '^docker(?:\s|$)') {
                $dockerCommands.Add($segment)
            }
        }
    }
    return $dockerCommands
}

function ConvertFrom-ShellWords {
    param([string]$Command)

    $words = [System.Collections.Generic.List[string]]::new()
    $builder = [System.Text.StringBuilder]::new()
    $inSingleQuote = $false
    $inDoubleQuote = $false
    $escaped = $false

    $flush = {
        if ($builder.Length -gt 0) {
            $words.Add($builder.ToString())
            [void]$builder.Clear()
        }
    }

    foreach ($character in $Command.ToCharArray()) {
        if ($escaped) {
            [void]$builder.Append($character)
            $escaped = $false
            continue
        }
        if ($character -eq '\' -and -not $inSingleQuote) {
            $escaped = $true
            continue
        }
        if ($character -eq "'" -and -not $inDoubleQuote) {
            $inSingleQuote = -not $inSingleQuote
            continue
        }
        if ($character -eq '"' -and -not $inSingleQuote) {
            $inDoubleQuote = -not $inDoubleQuote
            continue
        }
        if (-not $inSingleQuote -and -not $inDoubleQuote -and
            [char]::IsWhiteSpace($character)) {
            & $flush
            continue
        }
        [void]$builder.Append($character)
    }

    & $flush
    return $words
}

function Get-DockerRunImageToken {
    param([string]$Command)

    $words = @(ConvertFrom-ShellWords -Command $Command)
    if ($words.Count -lt 3 -or
        $words[0] -ne 'docker' -or
        $words[1] -ne 'run') {
        return $null
    }

    $optionsWithValues = [System.Collections.Generic.HashSet[string]]::new(
        [string[]]@(
            '--add-host',
            '--entrypoint',
            '--env',
            '--env-file',
            '--hostname',
            '--label',
            '--mount',
            '--name',
            '--network',
            '--pull',
            '--user',
            '--volume',
            '--workdir',
            '-e',
            '-h',
            '-l',
            '-u',
            '-v',
            '-w'
        )
    )

    $index = 2
    while ($index -lt $words.Count) {
        $word = $words[$index]
        if ($word -eq '--') {
            $index++
            break
        }
        if ($word.StartsWith('--')) {
            if ($word.Contains('=')) {
                $index++
                continue
            }
            if ($optionsWithValues.Contains($word)) {
                $index += 2
                continue
            }
            $index++
            continue
        }
        if ($word.StartsWith('-') -and $word -ne '-') {
            if ($optionsWithValues.Contains($word)) {
                $index += 2
                continue
            }
            $index++
            continue
        }
        break
    }

    if ($index -ge $words.Count) {
        return $null
    }
    return $words[$index]
}

function Get-ImageRuntimeContractFailures {
    param([string]$Content)

    $issues = [System.Collections.Generic.List[string]]::new()
    $dockerCommands = @(Get-DockerCommandSegments -Content $Content)
    $dockerRunCommands = @(
        $dockerCommands |
            Where-Object { $_ -match '^docker\s+run\b' }
    )

    if ($dockerRunCommands.Count -eq 0) {
        $issues.Add('no docker run commands were found')
    }

    $dockerRunImages = [System.Collections.Generic.List[string]]::new()
    foreach ($command in $dockerRunCommands) {
        if ($command -notmatch '(?:^|\s)--pull=never(?:\s|$)') {
            $issues.Add("docker run omits --pull=never: $command")
        }
        $imageToken = Get-DockerRunImageToken -Command $command
        if ($null -eq $imageToken) {
            $issues.Add("docker run image argument could not be parsed: $command")
            continue
        }
        $dockerRunImages.Add($imageToken)
        if ($imageToken -notmatch
            '^\$\{?(?:MYSQL|MAVEN)_IMAGE_ID\}?$') {
            $issues.Add(
                "docker run image is not a validated image ID: " +
                "$imageToken in $command"
            )
        }
    }

    foreach ($binding in @(
        @{
            Name = 'MySQL'
            Assignment = '(?m)^\s*MYSQL_IMAGE_ID\s*='
            Binding = '(?m)^\s*MYSQL_IMAGE_ID="\$\{?VALIDATED_IMAGE_ID\}?"\s*$'
        },
        @{
            Name = 'Maven'
            Assignment = '(?m)^\s*MAVEN_IMAGE_ID\s*='
            Binding = '(?m)^\s*MAVEN_IMAGE_ID="\$\{?VALIDATED_IMAGE_ID\}?"\s*$'
        }
    )) {
        $assignments = [regex]::Matches($Content, $binding.Assignment)
        $bindings = [regex]::Matches($Content, $binding.Binding)
        if ($assignments.Count -ne 1 -or $bindings.Count -ne 1) {
            $issues.Add(
                "$($binding.Name) image ID must bind exactly once to " +
                'VALIDATED_IMAGE_ID'
            )
        }
    }

    $pullFunctionPattern = '(?ms)^pull_image_with_digest\(\) \{\r?\n.*?^\}'
    $pullFunctionMatch = [regex]::Match($Content, $pullFunctionPattern)
    if (-not $pullFunctionMatch.Success) {
        $issues.Add('pull_image_with_digest function is missing')
    } else {
        $pullCommands = @(
            Get-DockerCommandSegments -Content $pullFunctionMatch.Value |
                Where-Object {
                    $_ -match '^docker\s+(?:image\s+)?pull\b'
                }
        )
        if ($pullCommands.Count -ne 1 -or
            $pullCommands[0] -notmatch
                '^docker\s+(?:image\s+)?pull\s+"\$\{?image\}?"(?:\s|$)') {
            $issues.Add(
                'pull_image_with_digest must contain exactly one controlled pull'
            )
        }

        $outsidePullFunction = $Content.Remove(
            $pullFunctionMatch.Index,
            $pullFunctionMatch.Length
        )
        $outsidePullCommands = @(
            Get-DockerCommandSegments -Content $outsidePullFunction |
                Where-Object {
                    $_ -match '^docker\s+(?:image\s+)?pull\b'
                }
        )
        if ($outsidePullCommands.Count -gt 0) {
            $issues.Add('docker pull exists outside pull_image_with_digest')
        }
    }

    $mysqlIdRuns = @(
        $dockerRunImages |
            Where-Object { $_ -match '^\$\{?MYSQL_IMAGE_ID\}?$' }
    )
    if ($mysqlIdRuns.Count -eq 0) {
        $issues.Add('no Docker runtime binds MYSQL_IMAGE_ID')
    }

    $mavenIdRuns = @(
        $dockerRunImages |
            Where-Object { $_ -match '^\$\{?MAVEN_IMAGE_ID\}?$' }
    )
    if ($mavenIdRuns.Count -eq 0) {
        $issues.Add('no Docker runtime binds MAVEN_IMAGE_ID')
    }

    return $issues
}

function Invoke-ImageResolutionBehaviorProbe {
    param([string]$Content)

    $functionNames = @(
        'repo_digest_line_valid',
        'image_id_valid',
        'repo_digest_lines_valid',
        'pull_image_with_digest',
        'inspect_preloaded_image',
        'resolve_image_identity'
    )
    $functionBlocks = foreach ($functionName in $functionNames) {
        $functionBlock = Get-ShellFunctionBlock `
            -Content $Content `
            -Name $functionName
        if ($null -eq $functionBlock) {
            return @{
                ExitCode = 90
                Output = "Missing function in behavior probe: $functionName"
            }
        }
        $functionBlock
    }

    $probe = ($functionBlocks -join "`n") + "`n" + @'
set -Eeuo pipefail

digest_hex='aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
image_hex='bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb'
repo_digest="registry.example/codecoachai@sha256:${digest_hex}"
mock_image_id="sha256:${image_hex}"
probe_dir="$(mktemp -d)"
docker_log="${probe_dir}/docker.log"
trap 'rm -rf -- "$probe_dir"' EXIT

docker() {
  printf '%s\n' "$*" >> "$docker_log"
  if [[ "$1" == "image" && "$2" == "inspect" && "$3" == "--format" ]]; then
    case "$4" in
      '{{json .RepoDigests}}')
        printf '["%s"]\n' "$repo_digest"
        ;;
      '{{range .RepoDigests}}{{println .}}{{end}}')
        printf '%s\n' "$repo_digest"
        ;;
      '{{.Id}}')
        printf '%s\n' "$mock_image_id"
        ;;
      *)
        return 91
        ;;
    esac
  fi
}

: > "$docker_log"
PRELOADED_IMAGES=0
resolve_image_identity 'mysql:default-probe'
grep -Fx 'pull mysql:default-probe' "$docker_log" >/dev/null || exit 41
[[ "$VALIDATED_IMAGE_ID" == "$mock_image_id" ]] || exit 42
[[ "$VALIDATED_IMAGE_SOURCE" == "registry-pull" ]] || exit 43

: > "$docker_log"
PRELOADED_IMAGES=1
resolve_image_identity 'mysql:preloaded-probe'
if grep -q '^pull ' "$docker_log"; then
  exit 44
fi
[[ "$VALIDATED_IMAGE_ID" == "$mock_image_id" ]] || exit 45
[[ "$VALIDATED_IMAGE_SOURCE" == "preloaded" ]] || exit 46
'@

    $probeOutput = $probe | & bash 2>&1
    return @{
        ExitCode = $LASTEXITCODE
        Output = ($probeOutput -join "`n")
    }
}

$contractScript = Get-ShellContractText -Content $script
$contractVerifySql = Get-SqlContractText -Content $verifySql
$contractInitSql = Get-SqlContractText -Content $initSql
$contractV3017Sql = Get-SqlContractText -Content $v3017Sql

function Assert-Matches {
    param(
        [string]$Content,
        [string]$Pattern,
        [string]$Contract
    )

    if ($Content -notmatch $Pattern) {
        $failures.Add("Missing contract '$Contract'")
    }
}

function Assert-NotMatches {
    param(
        [string]$Content,
        [string]$Pattern,
        [string]$Contract
    )

    if ($Content -match $Pattern) {
        $failures.Add("Forbidden contract '$Contract'")
    }
}

foreach ($entry in ([ordered]@{
    strictMode = 'set -Eeuo pipefail'
    secureUmask = 'umask 077'
    validateOnlyDefault = 'VALIDATE_ONLY=0'
    preloadedImagesDefault = 'PRELOADED_IMAGES="\$\{MIGRATION_REHEARSAL_PRELOADED_IMAGES:-0\}"'
    preloadedImagesValidation = 'MIGRATION_REHEARSAL_PRELOADED_IMAGES must be 0 or 1'
    validateOnlyArgument = '--validate-only\)'
    validateOnlyEarlyExit = 'if \[\[ "\$VALIDATE_ONLY" -eq 1 \]\]; then[\s\S]*Migration rehearsal validation passed[\s\S]*exit 0[\s\S]*fi[\s\S]*OWNER_TOKEN='
    unpredictableOwnerToken = 'OWNER_TOKEN=.*openssl rand -hex'
    dockerOwnershipLabel = '--label "\$\{OWNER_LABEL\}=\$\{OWNER_TOKEN\}"'
    cleanupOwnershipInspection = 'inspect.*OWNER_LABEL'
    isolatedNetwork = 'docker network create'
    isolatedDatabase = 'mysql:8\.0'
    noPortPublishing = '--network'
    independentSecretDirectory = 'SECRET_DIR=.*mktemp -d'
    secretFile = 'MYSQL_ROOT_PASSWORD_FILE'
    dockerDaemonPreflight = 'docker info'
    hostDiskPreflight = 'df -Pk'
    imagePreflight = 'docker (image inspect|pull)'
    imagePullBeforeDigest = 'docker pull "\$image"'
    preloadedImageInspection = 'using preloaded image without registry pull'
    preloadedImageIdEvidence = 'VALIDATED_IMAGE_ID='
    repoDigestsInspection = "docker image inspect --format '\{\{json \.RepoDigests\}\}'"
    emptyRepoDigestsGuard = 'case "\$repo_digests" in[\s\S]*""\|"null"\|"\[\]"\)'
    emptyRepoDigestsFailure = 'RepoDigests is empty after pull/inspect'
    validatedMysqlDigestEvidence = 'MYSQL_REPO_DIGESTS='
    validatedMavenDigestEvidence = 'MAVEN_REPO_DIGESTS='
    dependencyPreflight = 'help:describe[\s\S]*flyway-maven-plugin'
    imageDigestEvidence = 'image-digests\.tsv'
    imageSourceEvidence = 'source'
    preflightEvidenceCapture = 'exec > >\(tee -a "\$EVIDENCE/preflight\.log"\)'
    preflightErrorCapture = '2> >\(tee -a "\$EVIDENCE/error\.log" >&2\)'
    mysqlVersionEvidence = 'mysql-version\.txt'
    tcpReadiness = 'mysqladmin[\s\S]*--protocol=TCP[\s\S]*--host=127\.0\.0\.1'
    baselineSentinelTable = "table_name\s*=\s*'system_config'"
    baselineSentinelRow = "config_key\s*=\s*'ai\.timeout\.seconds'"
    v2BaselineFileInfoTable = "table_name\s*=\s*'file_info'"
    v2FinalSentinelTable = "table_name\s*=\s*'study_task'"
    v2FinalSentinelColumn = "column_name\s*=\s*'planned_date'"
    v2BaselineFileInfoEvidence = 'baseline_v2_file_info_table'
    v2FinalSentinelEvidence = 'baseline_v2_final_column'
    nonRecursiveMaven = 'mvn\s+-B\s+-N\s+-DskipTests'
    rootMigrationLocation = '-Dflyway\.locations=filesystem:/workspace/sql/migration'
    namedFlywayContainer = 'FLYWAY_CONTAINER='
    cleanupFlywayContainer = 'remove_owned_container "\$FLYWAY_CONTAINER"'
    cleanupTrap = 'trap\s+.+cleanup.+EXIT'
    failureSchemaCapture = 'capture_schema'
    flywayMigrate = 'flyway:migrate'
    flywayValidate = 'flyway:validate'
    schemaDump = 'final-schema\.sql'
    historyEvidence = 'flyway-history\.tsv'
    indexEvidence = 'indexes\.tsv'
    exactMigrationCount = 'assert_metric\s+migration_4_058_4_071_success_count\s+14'
    noMissingMigration = 'assert_metric\s+migration_4_058_4_071_missing_count\s+0'
    v4067Columns = 'assert_metric\s+v4_067_evidence_columns_exact_count\s+9'
    v4067Dimension = 'assert_metric\s+v4_067_readiness_dimension_exact\s+1'
    v4067Index = 'assert_metric\s+v4_067_evidence_project_index_exact\s+1'
    v4069Indexes = 'assert_metric\s+v4_069_active_unique_index_count\s+3'
    v4070MediumText = 'assert_metric\s+career_import_row_mediumtext_count\s+2'
    v4071RubricSeed = 'assert_metric\s+v4_071_rubric_seed_exact\s+1'
    v4071ScenarioSeed = 'assert_metric\s+v4_071_scenario_seed_exact_count\s+8'
    exactActiveAtsTemplates = 'assert_metric\s+ats_active_template_exact_count\s+3'
}).GetEnumerator()) {
    Assert-Matches -Content $contractScript -Pattern $entry.Value -Contract $entry.Key
}

$imageResolutionProbe = Invoke-ImageResolutionBehaviorProbe `
    -Content $contractScript
if ($imageResolutionProbe.ExitCode -ne 0) {
    $failures.Add(
        "Image resolution behavior probe failed with exit code " +
        "$($imageResolutionProbe.ExitCode): $($imageResolutionProbe.Output)"
    )
}

$invertedConditionScript = [regex]::Replace(
    $contractScript,
    'if \[\[ "\$PRELOADED_IMAGES" -eq 1 \]\]; then',
    'if [[ "$PRELOADED_IMAGES" -eq 0 ]]; then',
    1
)
if ($invertedConditionScript -eq $contractScript) {
    $failures.Add(
        "Condition reversal regression setup could not find image mode branch"
    )
} else {
    $invertedConditionProbe = Invoke-ImageResolutionBehaviorProbe `
        -Content $invertedConditionScript
    if ($invertedConditionProbe.ExitCode -ne 41) {
        $failures.Add(
            "Condition-reversed image mode branch was not rejected by the " +
            "default-pull contract: exit code " +
            "$($invertedConditionProbe.ExitCode)"
        )
    }
}

$imageRuntimeIssues = @(
    Get-ImageRuntimeContractFailures -Content $contractScript
)
foreach ($issue in $imageRuntimeIssues) {
    $failures.Add("Image runtime contract failed: $issue")
}

$imageRuntimeMutations = @(
    @{
        Name = 'MySQL alias rebound to mutable tag'
        Content = [regex]::Replace(
            $contractScript,
            '(?m)^\s*MYSQL_IMAGE_ID="\$\{?VALIDATED_IMAGE_ID\}?"\s*$',
            'MYSQL_IMAGE_ID="$MYSQL_IMAGE"',
            1
        )
    },
    @{
        Name = 'Maven alias rebound to unvalidated image ID'
        Content = [regex]::Replace(
            $contractScript,
            '(?m)^\s*MAVEN_IMAGE_ID="\$\{?VALIDATED_IMAGE_ID\}?"\s*$',
            'MAVEN_IMAGE_ID="sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"',
            1
        )
    },
    @{
        Name = 'MySQL alias rebound after the validated binding'
        Content = [regex]::Replace(
            $contractScript,
            '(?m)^\s*MYSQL_IMAGE_ID="\$\{?VALIDATED_IMAGE_ID\}?"\s*$',
            "MYSQL_IMAGE_ID=`"`$VALIDATED_IMAGE_ID`"`n" +
                'MYSQL_IMAGE_ID="$MYSQL_IMAGE"',
            1
        )
    },
    @{
        Name = 'unconditional pull inserted into main flow'
        Content = $contractScript.Replace(
            'resolve_image_identity "$MYSQL_IMAGE"',
            "docker pull `"`$MYSQL_IMAGE`"`nresolve_image_identity `"`$MYSQL_IMAGE`""
        )
    },
    @{
        Name = 'unconditional docker image pull inserted into main flow'
        Content = $contractScript.Replace(
            'resolve_image_identity "$MYSQL_IMAGE"',
            "docker image pull `"`$MYSQL_IMAGE`"`n" +
            'resolve_image_identity "$MYSQL_IMAGE"'
        )
    },
    @{
        Name = 'wrapped docker run uses a mutable tag and permits pulling'
        Content = $contractScript.Replace(
            'docker_free_kb="$(',
            "if docker run --rm `"`$MYSQL_IMAGE`" true; then :; fi`n" +
            'docker_free_kb="$('
        )
    },
    @{
        Name = 'second docker run on the same line omits never-pull'
        Content = $contractScript.Replace(
            'docker_free_kb="$(',
            'docker run --pull=never "$MYSQL_IMAGE_ID" true; ' +
            "docker run `"`$MYSQL_IMAGE_ID`" true`n" +
            'docker_free_kb="$('
        )
    },
    @{
        Name = 'second docker run on the same line binds a hard-coded tag'
        Content = $contractScript.Replace(
            'docker_free_kb="$(',
            'docker run --pull=never "$MYSQL_IMAGE_ID" true; ' +
            "docker run --pull=never mysql:8.0 true`n" +
            'docker_free_kb="$('
        )
    },
    @{
        Name = 'hard-coded image includes a validated ID decoy'
        Content = $contractScript.Replace(
            'docker_free_kb="$(',
            'docker run --rm --pull=never ' +
            '-e VERIFIED_IMAGE_ID="$MYSQL_IMAGE_ID" mysql:8.0 true' +
            "`n" +
            'docker_free_kb="$('
        )
    },
    @{
        Name = 'background docker run hides a second unsafe run'
        Content = $contractScript.Replace(
            'docker_free_kb="$(',
            'docker run --pull=never "$MYSQL_IMAGE_ID" true & ' +
            "docker run --rm `"`$MYSQL_IMAGE_ID`" true`n" +
            'docker_free_kb="$('
        )
    },
    @{
        Name = 'env wrapper hides an unsafe docker run'
        Content = $contractScript.Replace(
            'docker_free_kb="$(',
            "env docker run --rm `"`$MYSQL_IMAGE`" true`n" +
            'docker_free_kb="$('
        )
    }
)
foreach ($mutation in $imageRuntimeMutations) {
    if ($mutation.Content -eq $contractScript) {
        $failures.Add(
            "Image runtime mutation setup failed: $($mutation.Name)"
        )
        continue
    }
    $mutationIssues = @(
        Get-ImageRuntimeContractFailures -Content $mutation.Content
    )
    if ($mutationIssues.Count -eq 0) {
        $failures.Add(
            "Image runtime mutation was not rejected: $($mutation.Name)"
        )
    }
}

$imageRuntimeAcceptedVariants = @(
    @{
        Name = 'braced validated image ID binding'
        Content = [regex]::Replace(
            $contractScript,
            '(?m)^\s*MYSQL_IMAGE_ID="\$\{?VALIDATED_IMAGE_ID\}?"\s*$',
            'MYSQL_IMAGE_ID="${VALIDATED_IMAGE_ID}"',
            1
        )
    },
    @{
        Name = 'quoted Docker audit text'
        Content = $contractScript.Replace(
            'echo "Evidence directory: $EVIDENCE"',
            "printf '%s\n' 'docker run is audited'`n" +
            "printf '%s\n' 'docker pull is controlled'`n" +
            'echo "Evidence directory: $EVIDENCE"'
        )
    },
    @{
        Name = 'wrapped compliant Docker run'
        Content = $contractScript.Replace(
            'docker_free_kb="$(',
            "if docker run --rm --pull=never `"`$MYSQL_IMAGE_ID`" true; " +
            "then :; fi`n" +
            'docker_free_kb="$('
        )
    }
)
foreach ($variant in $imageRuntimeAcceptedVariants) {
    if ($variant.Content -eq $contractScript) {
        $failures.Add(
            "Image runtime accepted-variant setup failed: $($variant.Name)"
        )
        continue
    }
    $variantIssues = @(
        Get-ImageRuntimeContractFailures -Content $variant.Content
    )
    if ($variantIssues.Count -gt 0) {
        $failures.Add(
            "Image runtime accepted variant was rejected: $($variant.Name): " +
            ($variantIssues -join '; ')
        )
    }
}

foreach ($entry in ([ordered]@{
    baselineFileInfoTable = 'CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS\s+file_info'
    baselineFileStoragePath = 'storage_path\s+VARCHAR\(500\)\s+NOT\s+NULL'
    baselineFileProvider = "storage_provider\s+VARCHAR\(32\)\s+NOT\s+NULL\s+DEFAULT\s+'LOCAL'"
    baselineFileUserIndex = 'KEY\s+idx_file_info_user\s+\(user_id\)'
    baselineResumeAnalysisTable = 'CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS\s+resume_analysis_record'
    baselineResumeAnalysisRawText = 'raw_text\s+MEDIUMTEXT\s+DEFAULT\s+NULL'
    baselineResumeAnalysisFileIndex = 'KEY\s+idx_resume_analysis_file\s+\(file_id\)'
}).GetEnumerator()) {
    Assert-Matches -Content $contractInitSql -Pattern $entry.Value -Contract $entry.Key
}

Assert-Matches `
    -Content $contractV3017Sql `
    -Pattern "practice_status\s*=\s*'NOT_PRACTICABLE'" `
    -Contract 'V3_017 executable practice status literal'
Assert-NotMatches `
    -Content $contractV3017Sql `
    -Pattern "practice_status\s*=\s*''NOT_PRACTICABLE''" `
    -Contract 'V3_017 doubled executable practice status literal'

$imageIdValidatorMatch = [regex]::Match(
    $contractScript,
    '(?ms)^image_id_valid\(\) \{\r?\n.*?^\}'
)
if (-not $imageIdValidatorMatch.Success) {
    $failures.Add("Missing contract 'exact Docker image ID validator'")
} else {
    $validImageId = 'sha256:' + ('a' * 64)
    $shortImageId = 'sha256:' + ('b' * 63)
    $longImageId = 'sha256:' + ('c' * 65)
    $imageIdBoundaryProbe = @"
$($imageIdValidatorMatch.Value)
image_id_valid '$validImageId' || exit 31
image_id_valid '$shortImageId' && exit 32
image_id_valid '$longImageId' && exit 33
exit 0
"@
    $imageIdBoundaryProbe | & bash
    if ($LASTEXITCODE -ne 0) {
        $failures.Add(
            "Docker image ID validator boundary probe failed with exit code $LASTEXITCODE"
        )
    }
}

foreach ($hostCommand in @('chmod', 'mkdir', 'seq', 'sleep')) {
    Assert-Matches `
        -Content $contractScript `
        -Pattern "for command in [^\r\n]*\b$hostCommand\b" `
        -Contract "host dependency $hostCommand"
}

Assert-NotMatches -Content $contractScript -Pattern '(?m)(^|\s)-p\s*\d+:' -Contract 'published MySQL host port'
Assert-NotMatches -Content $contractScript -Pattern '--publish' -Contract 'published Docker port'
Assert-NotMatches -Content $contractScript -Pattern 'codecoachai-mysql|codecoachai_default|docker-compose' -Contract 'shared CodeCoachAI Docker resource'
Assert-NotMatches -Content $contractScript -Pattern 'SECRET="\$\{EVIDENCE\}' -Contract 'secret stored in evidence directory'
Assert-NotMatches -Content $contractScript -Pattern 'RUN_ID="ccai-migration-\$\(date .*\)-\$\$"' -Contract 'predictable timestamp and PID resource identity'
Assert-NotMatches -Content $contractScript -Pattern 'docker pull "\$image"\s*>/dev/null' -Contract 'suppressed image pull evidence'
Assert-NotMatches -Content $contractScript -Pattern '> "\$EVIDENCE/preflight\.log" 2>&1' -Contract 'truncated preflight evidence'
if ($contractScript.Contains('print \\\$4')) {
    $failures.Add("Forbidden contract 'over-escaped Docker disk free-space field'")
}
if ($contractScript.IndexOf("trap 'cleanup `$?' EXIT", [System.StringComparison]::Ordinal) -gt
    $contractScript.IndexOf('openssl rand -hex 32 > "$SECRET"', [System.StringComparison]::Ordinal)) {
    $failures.Add("Missing contract 'cleanup trap installed before secret creation'")
}
if ($contractScript.IndexOf("trap 'cleanup `$?' EXIT", [System.StringComparison]::Ordinal) -gt
    $contractScript.IndexOf('SECRET_DIR="$(mktemp -d', [System.StringComparison]::Ordinal)) {
    $failures.Add("Missing contract 'cleanup trap installed before secret directory creation'")
}

$preflightCaptureIndex = $contractScript.IndexOf(
    'exec > >(tee -a "$EVIDENCE/preflight.log")',
    [System.StringComparison]::Ordinal
)
foreach ($preflightMarker in @(
    'docker info',
    'docker pull "$image"',
    'MYSQL_REPO_DIGESTS='
)) {
    $markerIndex = $contractScript.IndexOf(
        $preflightMarker,
        [System.StringComparison]::Ordinal
    )
    if ($preflightCaptureIndex -lt 0 -or
        $markerIndex -lt 0 -or
        $preflightCaptureIndex -gt $markerIndex) {
        $failures.Add(
            "Missing contract 'preflight evidence starts before $preflightMarker'"
        )
    }
}

foreach ($commentRegression in @(
    @{
        Name = 'strict mode'
        LinePattern = '(?m)^set -Eeuo pipefail\s*$'
        RequiredPattern = 'set -Eeuo pipefail'
    },
    @{
        Name = 'Flyway migrate'
        LinePattern = '(?m)^\s*flyway:info flyway:migrate flyway:validate flyway:info\s*$'
        RequiredPattern = 'flyway:migrate'
    },
    @{
        Name = 'migration assertion'
        LinePattern = '(?m)^assert_metric migration_4_058_4_071_success_count 14\s*$'
        RequiredPattern = 'assert_metric\s+migration_4_058_4_071_success_count\s+14'
    }
)) {
    $lineRegex = [regex]::new($commentRegression.LinePattern)
    $commentedScript = $lineRegex.Replace(
        $script,
        { param($match) '# ' + $match.Value },
        1
    )
    if ($commentedScript -eq $script) {
        $failures.Add(
            "Comment regression setup could not find $($commentRegression.Name)"
        )
    } elseif ((Get-ShellContractText -Content $commentedScript) -match
        $commentRegression.RequiredPattern) {
        $failures.Add(
            "Commented-out $($commentRegression.Name) still satisfies contract"
        )
    }
}

$commentedSql = [regex]::Replace(
    $verifySql,
    "(?m)^\s*AND data_type = 'mediumtext'\s*$",
    "-- AND data_type = 'mediumtext'",
    1
)
if ($commentedSql -eq $verifySql) {
    $failures.Add('Comment regression setup could not find SQL definition')
} elseif ((Get-SqlContractText -Content $commentedSql) -match
    "data_type\s*=\s*'mediumtext'") {
    $failures.Add('Commented-out SQL definition still satisfies contract')
}

$digestValidatorMatch = [regex]::Match(
    $contractScript,
    '(?ms)^repo_digest_line_valid\(\) \{\r?\n.*?^\}'
)
if (-not $digestValidatorMatch.Success) {
    $failures.Add("Missing contract 'exact RepoDigest line validator'")
} else {
    $validDigest = 'registry.example/codecoachai@sha256:' + ('a' * 64)
    $sixtyFiveHexDigest = 'registry.example/codecoachai@sha256:' + ('b' * 65)
    $trailingHexDigest = 'registry.example/codecoachai@sha256:' + ('c' * 64) + 'deadbeef'
    $digestBoundaryProbe = @"
$($digestValidatorMatch.Value)
repo_digest_line_valid '$validDigest' || exit 11
repo_digest_line_valid '$sixtyFiveHexDigest' && exit 12
repo_digest_line_valid '$trailingHexDigest' && exit 13
exit 0
"@
    $digestBoundaryProbe | & bash
    if ($LASTEXITCODE -ne 0) {
        $failures.Add(
            "RepoDigest validator boundary probe failed with exit code $LASTEXITCODE"
        )
    }
}

$digestLinesValidatorMatch = [regex]::Match(
    $contractScript,
    '(?ms)^repo_digest_lines_valid\(\) \{\r?\n.*?^\}'
)
if (-not $digestLinesValidatorMatch.Success) {
    $failures.Add("Missing contract 'RepoDigest multi-line validator'")
} elseif ($digestValidatorMatch.Success) {
    $validDigest = 'registry.example/codecoachai@sha256:' + ('d' * 64)
    $invalidDigest = 'registry.example/codecoachai@sha256:' + ('e' * 65)
    $digestLinesProbe = $digestValidatorMatch.Value + "`n" +
        $digestLinesValidatorMatch.Value + "`n" + @'
valid_line='__VALID_DIGEST__'
invalid_line='__INVALID_DIGEST__'
empty_line=$'\n'
valid_multi="${valid_line}"$'\n'"${valid_line}"
valid_plus_empty="${valid_line}"$'\n\n'
valid_plus_invalid="${valid_line}"$'\n'"${invalid_line}"
repo_digest_lines_valid "$valid_multi" || exit 21
repo_digest_lines_valid "" && exit 22
repo_digest_lines_valid "$empty_line" && exit 23
repo_digest_lines_valid "$valid_plus_empty" && exit 24
repo_digest_lines_valid "$valid_plus_invalid" && exit 25
exit 0
'@
    $digestLinesProbe = $digestLinesProbe.
        Replace('__VALID_DIGEST__', $validDigest).
        Replace('__INVALID_DIGEST__', $invalidDigest)
    $digestLinesProbe | & bash
    if ($LASTEXITCODE -ne 0) {
        $failures.Add(
            "RepoDigest multi-line probe failed with exit code $LASTEXITCODE"
        )
    }
}

$expectedVersions = 58..71 | ForEach-Object { '4.{0:d3}' -f $_ }
foreach ($version in $expectedVersions) {
    Assert-Matches -Content $contractVerifySql -Pattern ([regex]::Escape("'$version'")) -Contract "successful migration $version"
}

foreach ($entry in ([ordered]@{
    exactMigrationCountMetric = 'migration_4_058_4_071_success_count'
    missingMigrationMetric = 'migration_4_058_4_071_missing_count'
    exactIndexColumnOrder = 'GROUP_CONCAT\(column_name ORDER BY seq_in_index'
    v4067EvidenceIndexName = 'idx_requirement_evidence_project'
    v4067OrdinalPosition = 'ordinal_position'
    v4067ColumnDefault = 'column_default'
    v4067CharacterLength = 'character_maximum_length'
    v4067CharacterSet = 'character_set_name'
    v4067Collation = 'collation_name'
    v4067TableCollation = 'table_collation'
    v4067Extra = '\bextra\b'
    v4067GenerationExpression = 'generation_expression'
    v4067ColumnComment = 'column_comment'
    v4067ProjectEvidenceComment = 'optional project evidence id'
    v4067EvidenceTypeDefault = 'PROJECT_EVIDENCE'
    v4067DimensionComment = 'five-dimension readiness snapshot'
    v4069JobApplicationIndexName = 'uk_job_application_import_fingerprint'
    v4069AssignmentIndexName = 'uk_jea_hypothesis_application'
    v4069CalendarIndexName = 'uk_cce_user_external_uid'
    v4070RawDataColumn = 'raw_data_json'
    v4070DuplicateCandidatesColumn = 'duplicate_candidates_json'
    v4070MediumTextType = "data_type\s*=\s*'mediumtext'"
    v4071JsonValidation = 'JSON_VALID'
    v4071HashValidation = 'SHA2'
    v4071RubricChinese = '\u8868\u8FBE\u7ED3\u6784'
    v4071HrChinese = 'HR \u521D\u7B5B'
    v4071SystemDesignChinese = '\u7CFB\u7EDF\u8BBE\u8BA1'
    v4071StressChinese = '\u538B\u529B\u8FFD\u95EE\u4E0E\u7EFC\u5408\u9762\u8BD5'
    v4071RubricHash = 'a0e18c609f82c8285037c800b0331109454e5255a269bc48a4bea2f8b4c7683b'
    v4071HrHash = 'f40b118fdcdef5b3c1ccaf15358c28101eefca0f3891c870e3bbd29ed7ee8022'
    v4071StressHash = '5602d667d0fd67d4c6b13fb315fcef4629fc26a87959401b6b6bf015e7c991de'
    atsMetric = 'ats_active_template_exact_count'
    atsSingleColumn = 'ATS_SINGLE_COLUMN'
    atsCompact = 'ATS_COMPACT'
    atsProjectFocus = 'ATS_PROJECT_FOCUS'
    atsActive = "status\s*=\s*'ACTIVE'"
}).GetEnumerator()) {
    Assert-Matches -Content $contractVerifySql -Pattern $entry.Value -Contract $entry.Key
}

Assert-NotMatches -Content $contractVerifySql -Pattern 'uk_job_application_active_import_fingerprint' -Contract 'incorrect V4_069 job application index name'

foreach ($entry in ([ordered]@{
    preflightDocumentation = '\u9884\u68C0'
    digestDocumentation = 'digest'
    mysqlVersionDocumentation = 'MySQL \u7248\u672C'
    ownershipDocumentation = 'ownership'
    failureSchemaDocumentation = '\u5931\u8D25.*schema'
    secretDirectoryDocumentation = '\u72EC\u7ACB\u4E34\u65F6\u76EE\u5F55'
    exactMigrationDocumentation = '14'
    validateOnlyDocumentation = '--validate-only'
    validateOnlyNoResourcesDocumentation = '\u4E0D\u521B\u5EFA Docker \u8D44\u6E90'
    repoDigestsGateDocumentation = 'RepoDigests'
    emptyDigestDocumentation = '\[\]'
    digestGateDocumentation = '\u786C\u95E8\u7981'
    earlyPreflightEvidenceDocumentation = 'preflight\.log'
    errorEvidenceDocumentation = 'error\.log'
    exactV4067Documentation = 'ordinal|default|comment|collation'
    activeAtsDocumentation = 'ACTIVE'
    preloadedImagesDocumentation = 'MIGRATION_REHEARSAL_PRELOADED_IMAGES=1'
    preloadedImageIdDocumentation = 'image ID'
    v2BaselineDocumentation = '2\.999'
    v2BaselineFileInfoDocumentation = 'file_info'
}).GetEnumerator()) {
    Assert-Matches -Content $operationsDoc -Pattern $entry.Value -Contract "documentation $($entry.Key)"
}

if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Error $_ -ErrorAction Continue }
    throw "Migration rehearsal static contract failed with $($failures.Count) issue(s)."
}

Write-Host 'Migration rehearsal static contract passed.'
