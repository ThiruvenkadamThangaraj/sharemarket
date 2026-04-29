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

function Stop-ListenerOnPort {
    param([int]$Port)

    $listeners = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    foreach ($listener in $listeners) {
        $procId = $listener.OwningProcess
        if ($procId -and (Get-Process -Id $procId -ErrorAction SilentlyContinue)) {
            Write-Host "Stopping existing listener on port $Port (PID: $procId)" -ForegroundColor Yellow
            Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
        }
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

function Invoke-CryptoReportRun {
    param(
        [string]$WorkingDirectory,
        [string]$PomPath,
        [string]$ReportDirectory,
        [string]$ServiceUrl = "http://localhost:8091/api/v1/crypto/signals/analyze/report",
        [string[]]$Symbols = @("BTC", "ETH", "BNB", "SOL", "XRP", "AAPL", "MSFT", "AMZN", "GOOGL", "META", "NVDA", "TSLA", "NFLX", "ADBE", "CRM", "AMD", "INTC", "AVGO", "QCOM", "MU", "TXN", "AMAT", "LRCX", "KLAC", "MRVL", "JPM", "BAC", "WFC", "GS", "MS", "V", "MA", "AXP", "WMT", "COST", "TGT", "HD", "LOW", "NKE", "MCD", "SBUX", "JNJ", "PFE", "UNH", "ABBV", "MRK", "XOM", "CVX", "COP", "SLB", "BA", "CAT", "GE", "UBER", "PLTR"),
        [int]$StartupTimeoutSeconds = 180,
        [int]$ReportTimeoutSeconds = 180
    )

    $runStart = Get-Date
    $analyzeUrl = $ServiceUrl -replace '/analyze/report$', '/analyze'
    $servicePort = [int]([uri]$ServiceUrl).Port
    Stop-ListenerOnPort -Port $servicePort
    Write-Host ""
    Write-Host "=== Generating Crypto Buy Signals Report ===" -ForegroundColor Cyan

    $argumentList = @(
        "-f", $PomPath,
        "spring-boot:run"
    )

    $process = Start-Process -FilePath "mvn" -ArgumentList $argumentList -WorkingDirectory $WorkingDirectory -PassThru
    Write-Host "Crypto report process started (PID: $($process.Id))"

    $startupDeadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
    $bodyJson = @{ symbols = $Symbols } | ConvertTo-Json -Compress
    $analysisRows = $null

    while ((Get-Date) -lt $startupDeadline) {
        Start-Sleep -Seconds 3

        if ($process.HasExited) {
            throw "Crypto service process exited before the REST endpoint became available."
        }

        try {
            $analysisRows = Invoke-RestMethod -Method Post -Uri $analyzeUrl -ContentType "application/json" -Body $bodyJson
            break
        }
        catch {
            # Service may still be starting; keep polling until timeout.
        }
    }

    if ($null -eq $analysisRows) {
        Stop-ProcessTree -ParentId $process.Id
        throw "Crypto report endpoint was not reachable within $StartupTimeoutSeconds seconds."
    }

    $reportResponse = Invoke-RestMethod -Method Post -Uri $ServiceUrl -ContentType "application/json" -Body $bodyJson

    $rows = @($analysisRows)
    $buyCount = @($rows | Where-Object { $_.decision -eq 'BUY' }).Count
    $waitCount = @($rows | Where-Object { $_.decision -eq 'WAIT' }).Count
    $sellWatchCount = @($rows | Where-Object { $_.decision -eq 'SELL_WATCH' }).Count
    $noDataCount = @($rows | Where-Object { $_.scenario -eq 'NO_DATA' }).Count
    $insufficientDataCount = @($rows | Where-Object { $_.scenario -eq 'INSUFFICIENT_DATA' }).Count

    Write-Host "Crypto analysis summary: total=$($rows.Count), BUY=$buyCount, WAIT=$waitCount, SELL_WATCH=$sellWatchCount, NO_DATA=$noDataCount, INSUFFICIENT_DATA=$insufficientDataCount" -ForegroundColor Yellow

    $report = $null
    $reportDeadline = (Get-Date).AddSeconds($ReportTimeoutSeconds)
    while ((Get-Date) -lt $reportDeadline) {
        Start-Sleep -Seconds 2

        if ($null -ne $reportResponse.reportPath -and (Test-Path $reportResponse.reportPath)) {
            $report = Get-Item $reportResponse.reportPath
            break
        }

        $report = Get-LatestReport -Directory $ReportDirectory -After $runStart.AddSeconds(-2) -Label "Crypto"
        if ($null -ne $report) {
            break
        }
    }

    Stop-ProcessTree -ParentId $process.Id
    Start-Sleep -Seconds 1

    if ($null -eq $report) {
        throw "Crypto report not generated within $ReportTimeoutSeconds seconds."
    }

    Write-Host "Crypto report: $($report.FullName)" -ForegroundColor Green
    return $report
}

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$sharemarketPom = Join-Path $repoRoot "pom.xml"
$smcPom = Join-Path $repoRoot "smc-indicator-service\pom.xml"
$cryptoPom = Join-Path $repoRoot "crypto-buy-signals-service\pom.xml"
$sharemarketReportsDir = Join-Path $repoRoot "reports"
$smcReportsDir = Join-Path $repoRoot "smc-indicator-service\smc-reports"
$cryptoReportsDir = Join-Path $repoRoot "crypto-reports"

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw "Maven (mvn) is not available in PATH."
}

if (-not (Test-Path $sharemarketPom)) {
    throw "sharemarket pom.xml not found: $sharemarketPom"
}

if (-not (Test-Path $smcPom)) {
    throw "smc-indicator-service pom.xml not found: $smcPom"
}

if (-not (Test-Path $cryptoPom)) {
    throw "crypto-buy-signals-service pom.xml not found: $cryptoPom"
}

Write-Host "Repository root: $repoRoot" -ForegroundColor Yellow

$sharemarketReport = Invoke-ReportRun -Label "Sharemarket RSI Report" -WorkingDirectory $repoRoot -PomPath $sharemarketPom -JvmArgs "-Dmarket.run-on-startup=true -Dspring.task.scheduling.enabled=false -Dspring.main.web-application-type=none" -ReportDirectory $sharemarketReportsDir -TimeoutSeconds 240
$smcReport = Invoke-ReportRun -Label "SMC Zone Report" -WorkingDirectory $repoRoot -PomPath $smcPom -JvmArgs "-Dsmc.run-on-startup=true -Dspring.main.web-application-type=none" -ReportDirectory $smcReportsDir -TimeoutSeconds 240
$cryptoReport = Invoke-CryptoReportRun -WorkingDirectory $repoRoot -PomPath $cryptoPom -ReportDirectory $cryptoReportsDir -Symbols @("BTC", "ETH", "BNB", "SOL", "XRP", "AAPL", "MSFT", "AMZN", "GOOGL", "META", "NVDA", "TSLA", "NFLX", "ADBE", "CRM", "AMD", "INTC", "AVGO", "QCOM", "MU", "TXN", "AMAT", "LRCX", "KLAC", "MRVL", "JPM", "BAC", "WFC", "GS", "MS", "V", "MA", "AXP", "WMT", "COST", "TGT", "HD", "LOW", "NKE", "MCD", "SBUX", "JNJ", "PFE", "UNH", "ABBV", "MRK", "XOM", "CVX", "COP", "SLB", "BA", "CAT", "GE", "UBER", "PLTR") -StartupTimeoutSeconds 180 -ReportTimeoutSeconds 180

$combinedRoot = Join-Path $repoRoot "combined-reports"
$runFolderName = (Get-Date).ToString("yyyy-MM-dd_HH-mm-ss")
$runFolder = Join-Path $combinedRoot $runFolderName
New-Item -Path $runFolder -ItemType Directory -Force | Out-Null

$sharemarketOut = Join-Path $runFolder "sharemarket-report.xlsx"
$smcOut = Join-Path $runFolder "smc-report.xlsx"
$cryptoOut = Join-Path $runFolder "crypto-report.xlsx"

Copy-Item -Path $sharemarketReport.FullName -Destination $sharemarketOut -Force
Copy-Item -Path $smcReport.FullName -Destination $smcOut -Force
Copy-Item -Path $cryptoReport.FullName -Destination $cryptoOut -Force

Write-Host ""
Write-Host "Reports generated successfully." -ForegroundColor Green
Write-Host "Sharemarket: $($sharemarketReport.FullName)"
Write-Host "SMC:         $($smcReport.FullName)"
Write-Host "Crypto:      $($cryptoReport.FullName)"
Write-Host "Combined:    $runFolder"
Write-Host ""
Write-Host "Copied files:" -ForegroundColor Green
Write-Host " - $sharemarketOut"
Write-Host " - $smcOut"
Write-Host " - $cryptoOut"