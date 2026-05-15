[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [switch]$IncludeCombinedReports = $true,
    [switch]$IncludeSharemarketReports = $true,
    [switch]$IncludeSmcReports = $true,
    [switch]$IncludeCryptoReports = $true,
    [switch]$RemoveEmptyDirectories = $true
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Remove-ItemSafe {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [switch]$Recurse
    )

    try {
        Remove-Item -Path $Path -Force -Recurse:$Recurse -WhatIf:$WhatIfPreference -ErrorAction Stop
        return $true
    } catch {
        Write-Host "Skipped: $Path" -ForegroundColor DarkYellow
        Write-Host "  Reason: $($_.Exception.Message)" -ForegroundColor DarkYellow
        return $false
    }
}

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

$targets = @()

if ($IncludeSharemarketReports) {
    $targets += @{ Label = "Sharemarket reports"; Path = Join-Path $repoRoot "reports"; FileOnly = $true }
}
if ($IncludeSmcReports) {
    $targets += @{ Label = "SMC reports"; Path = Join-Path $repoRoot "smc-indicator-service\smc-reports"; FileOnly = $true }
}
if ($IncludeCryptoReports) {
    $targets += @{ Label = "Crypto reports"; Path = Join-Path $repoRoot "crypto-buy-signals-service\crypto-reports"; FileOnly = $true }
}
if ($IncludeCombinedReports) {
    $targets += @{ Label = "Combined reports"; Path = Join-Path $repoRoot "combined-reports"; FileOnly = $false }
}

Write-Host "Repository root: $repoRoot" -ForegroundColor Yellow
Write-Host "Starting report cleanup..." -ForegroundColor Cyan

foreach ($target in $targets) {
    $path = $target.Path
    $label = $target.Label

    if (-not (Test-Path $path)) {
        Write-Host "${label}: skipped (not found) -> $path" -ForegroundColor DarkYellow
        continue
    }

    if ($target.FileOnly) {
        $files = @(Get-ChildItem -Path $path -Filter "*.xlsx" -File -ErrorAction SilentlyContinue)
        if ($files.Count -eq 0) {
            Write-Host "${label}: no .xlsx files found" -ForegroundColor DarkYellow
        } else {
            $removed = 0
            foreach ($file in $files) {
                if (Remove-ItemSafe -Path $file.FullName) {
                    $removed++
                }
            }
            Write-Host "${label}: removed $removed of $($files.Count) .xlsx file(s)" -ForegroundColor Green
        }

        if ($RemoveEmptyDirectories) {
            $remaining = @(Get-ChildItem -Path $path -Force -ErrorAction SilentlyContinue)
            if ($remaining.Count -eq 0) {
                if (Remove-ItemSafe -Path $path) {
                    Write-Host "${label}: removed empty directory" -ForegroundColor Green
                }
            }
        }
    } else {
        $entries = @(Get-ChildItem -Path $path -Force -ErrorAction SilentlyContinue)
        if ($entries.Count -eq 0) {
            Write-Host "${label}: already empty" -ForegroundColor DarkYellow
            continue
        }

        $removed = 0
        foreach ($entry in $entries) {
            if (Remove-ItemSafe -Path $entry.FullName -Recurse) {
                $removed++
            }
        }
        Write-Host "${label}: removed $removed of $($entries.Count) entr(y/ies)" -ForegroundColor Green
    }
}

Write-Host "Cleanup completed." -ForegroundColor Cyan
Write-Host "Tip: use -WhatIf to preview deletions before executing." -ForegroundColor Yellow
