Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-LatestReport {
    param(
        [string]$Directory,
        [datetime]$After,
        [string]$Label
    )

    if (-not (Test-Path $Directory)) {
        throw "$Label report directory not found: $Directory"
    }

    return Get-ChildItem -Path $Directory -Filter "*.xlsx" -File |
        Where-Object { $_.Name -notlike '~$*' -and $_.LastWriteTime -ge $After } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
}

function Stop-ProcessTree {
    param([int]$ParentId)

    $children = Get-CimInstance Win32_Process -Filter "ParentProcessId=$ParentId" -ErrorAction SilentlyContinue
    foreach ($child in $children) {
        Stop-ProcessTree -ParentId $child.ProcessId
    }

    if (Get-Process -Id $ParentId -ErrorAction SilentlyContinue) {
        Stop-Process -Id $ParentId -Force -ErrorAction SilentlyContinue
    }
}

function Invoke-ReportRun {
    param(
        [string]$Label,
        [string]$WorkingDirectory,
        [string]$PomPath,
        [string]$JvmArgs,
        [string]$ReportDirectory,
        [int]$TimeoutSeconds = 240
    )

    $runStart = Get-Date
    Write-Host ""
    Write-Host "=== Generating $Label ===" -ForegroundColor Cyan

    $argumentList = @(
        "-f", $PomPath,
        "spring-boot:run",
        "-Dspring-boot.run.jvmArguments=$JvmArgs"
    )

    $process = Start-Process -FilePath "mvn" -ArgumentList $argumentList -WorkingDirectory $WorkingDirectory -PassThru
    Write-Host "$Label process started (PID: $($process.Id))"

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $report = $null
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 2
        $report = Get-LatestReport -Directory $ReportDirectory -After $runStart.AddSeconds(-2) -Label $Label
        if ($null -ne $report) {
            break
        }

        if ($process.HasExited -and $null -eq $report) {
            throw "$Label process exited before report was found."
        }
    }

    if ($null -eq $report) {
        Stop-ProcessTree -ParentId $process.Id
        throw "$Label report not generated within $TimeoutSeconds seconds."
    }

    Stop-ProcessTree -ParentId $process.Id
    Start-Sleep -Seconds 1

    Write-Host "$Label report: $($report.FullName)" -ForegroundColor Green
    return $report
}

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$sharemarketPom = Join-Path $repoRoot "pom.xml"
$smcPom = Join-Path $repoRoot "smc-indicator-service\pom.xml"
$sharemarketReportsDir = Join-Path $repoRoot "reports"
$smcReportsDir = Join-Path $repoRoot "smc-indicator-service\smc-reports"

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw "Maven (mvn) is not available in PATH."
}

if (-not (Test-Path $sharemarketPom)) {
    throw "sharemarket pom.xml not found: $sharemarketPom"
}

if (-not (Test-Path $smcPom)) {
    throw "smc-indicator-service pom.xml not found: $smcPom"
}

Write-Host "Repository root: $repoRoot" -ForegroundColor Yellow

$sharemarketReport = Invoke-ReportRun -Label "Sharemarket RSI Report" -WorkingDirectory $repoRoot -PomPath $sharemarketPom -JvmArgs "-Dmarket.run-on-startup=true -Dspring.task.scheduling.enabled=false -Dspring.main.web-application-type=none" -ReportDirectory $sharemarketReportsDir -TimeoutSeconds 240
$smcReport = Invoke-ReportRun -Label "SMC Zone Report" -WorkingDirectory $repoRoot -PomPath $smcPom -JvmArgs "-Dsmc.run-on-startup=true -Dspring.main.web-application-type=none" -ReportDirectory $smcReportsDir -TimeoutSeconds 240

$combinedRoot = Join-Path $repoRoot "combined-reports"
$runFolderName = (Get-Date).ToString("yyyy-MM-dd_HH-mm-ss")
$runFolder = Join-Path $combinedRoot $runFolderName
New-Item -Path $runFolder -ItemType Directory -Force | Out-Null

$sharemarketOut = Join-Path $runFolder "sharemarket-report.xlsx"
$smcOut = Join-Path $runFolder "smc-report.xlsx"

Copy-Item -Path $sharemarketReport.FullName -Destination $sharemarketOut -Force
Copy-Item -Path $smcReport.FullName -Destination $smcOut -Force

Write-Host ""
Write-Host "Reports generated successfully." -ForegroundColor Green
Write-Host "Sharemarket: $($sharemarketReport.FullName)"
Write-Host "SMC:         $($smcReport.FullName)"
Write-Host "Combined:    $runFolder"
Write-Host ""
Write-Host "Copied files:" -ForegroundColor Green
Write-Host " - $sharemarketOut"
Write-Host " - $smcOut"