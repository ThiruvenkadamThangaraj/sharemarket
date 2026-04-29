# Crypto Buy Signals Service (Java)

A standalone Spring Boot microservice that evaluates crypto buy opportunities with a 3-signal framework:

1. Current price is at least 50% below ATH
2. RSI is below 40
3. Price is at/below MA 50, 100, or 200

## API

`POST /api/v1/crypto/signals/analyze`

`POST /api/v1/crypto/signals/analyze/report`

Request example:

```json
{
  "symbols": ["BTC", "ETH", "XRP", "BNB"]
}
```

Only these four symbols are supported in this service:
- `BTC-USD`
- `ETH-USD`
- `XRP-USD`
- `BNB-USD`

If any other symbol is sent, API returns `400 Bad Request`.

Response contains per-symbol details for:
- ATH discount check
- RSI value and threshold check
- MA 50/100/200 values and MA hit count
- Decision (`BUY`, `WAIT`, `SELL_WATCH`)
- Buy strength (`NONE`, `BASIC`, `STRONG`, `VERY_STRONG`)

The report endpoint also writes an Excel file to `crypto.report.output-directory`
and returns the generated file path.

## Run

```bash
mvn spring-boot:run
```

## Test

```bash
mvn test
```

## Run Through Main Orchestrator

From the repository root, you can generate all reports (Sharemarket + SMC + Crypto) with one command:

```powershell
.\run-all-reports.ps1
```

The crypto output is copied into the latest folder under `combined-reports` as `crypto-report.xlsx`.
