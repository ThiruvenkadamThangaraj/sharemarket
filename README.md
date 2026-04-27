# Share Market Daily Signal Generator

A Spring Boot service that runs every day after market close, fetches OHLC price data from Yahoo Finance, calculates RSI-14 and its 9-period moving average using TA4J, applies a multi-level signal decision tree, and writes a colour-coded Excel dashboard grouped by sector.

---

## Architecture

```
Yahoo Finance API
      │
      ▼
PriceDataService          ← HTTP fetch, JSON parse → List<OHLCData>
      │
      ▼
IndicatorService           ← TA4J: RSI-14 (blue line), SMA-9 of RSI (yellow line)
      │                          Support / Resistance (lowest low / highest high)
      ▼
SignalService              ← Decision logic → MarketSignal (BUY CONFIRM / BUY / WAIT / SELL …)
      │
      ▼
ExcelReportService         ← Apache POI → market-report-YYYY-MM-DD_HH-mm_RSI30-40_OB70.xlsx
      ▲
      │
DailyMarketJob             ← @Scheduled cron (default 21:05 UTC daily)
```

---

## Signal Logic

Signals are evaluated in priority order (top = highest priority):

| Signal | Condition | Colour |
|--------|-----------|--------|
| **CRITICAL OS** | RSI < 20 | Dark navy |
| **EXTREME OS** | RSI < 25 | Deep blue |
| **OVERSOLD** | RSI < 30 | Blue |
| **BUY CONFIRM** | RSI blue > yellow AND RSI in [30–40] AND price near support | Deep green |
| **BULLISH START** | RSI blue > yellow AND RSI in [30–40] | Medium green |
| **WAIT** | RSI blue > yellow but outside [30–40], or below yellow | Amber |
| **SELL** | RSI blue < yellow AND price near resistance | Red |
| **OVERBOUGHT** | RSI > 70 | Orange |
| **EXTREME OB** | RSI > 80 | Deep red |
| **CRITICAL OB** | RSI > 95 | Maroon |

*"Near support/resistance" = within 3% of the 20-bar low/high (configurable).*  
*RSI filename suffix shows active config, e.g. `_RSI30-40_OB70`.*

---

## Covered Symbols

### Crypto (5)
`BTC-USD`, `ETH-USD`, `BNB-USD`, `SOL-USD`, `XRP-USD`

### Stocks by Sector (55)

| Sector | Symbols |
|--------|---------|
| Big Tech | AAPL, MSFT, AMZN, GOOGL, META, NVDA, TSLA, NFLX, ADBE, CRM |
| Semiconductors | AMD, INTC, AVGO, QCOM, MU, TXN, AMAT, LRCX, KLAC, MRVL |
| Financials | JPM, BAC, WFC, GS, MS, V, MA, AXP |
| Consumer | WMT, COST, TGT, HD, LOW, NKE, MCD, SBUX |
| Healthcare | JNJ, PFE, UNH, ABBV, MRK |
| Energy | XOM, CVX, COP, SLB |
| Industrial | BA, CAT, GE, UBER, PLTR |

---

## Excel Dashboard Output

- Rows grouped by sector with colour-coded divider headers
- Signal cells colour-coded per the table above
- Reason column explains exactly why each signal was triggered
- Dynamic row heights — long Reason text never overlaps other rows
- Filename includes active RSI config for traceability, e.g.:  
  `market-report-2026-04-27_14-12_RSI30-40_OB70.xlsx`

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 17+ |
| Maven | 3.8+ |

No external API key is needed — Yahoo Finance public endpoint is used.

---

## Running

### Run once immediately (test / ad-hoc)

```powershell
mvn spring-boot:run "-Dspring-boot.run.jvmArguments=-Dmarket.run-on-startup=true"
```

### Open the latest report automatically

```powershell
$latest = Get-ChildItem ".\reports\*.xlsx" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
Start-Process $latest.FullName
```

### Run as a scheduled daemon (fires daily at 21:05 UTC)

```powershell
# Keep market.run-on-startup=false (default)
mvn spring-boot:run
```

### Build a fat JAR

```bash
mvn clean package -DskipTests
java -jar target/sharemarket-1.0.0.jar
```

### Override the schedule at runtime

```bash
java -jar target/sharemarket-1.0.0.jar --market.scheduler.cron="0 0 22 * * *"
```

---

## Configuration Reference

All settings live in `src/main/resources/application.properties`:

### Scheduler & Output

| Property | Default | Description |
|----------|---------|-------------|
| `market.scheduler.cron` | `0 5 21 * * *` | Cron trigger (21:05 UTC = 4:05 PM ET) |
| `market.run-on-startup` | `false` | Set `true` for immediate one-shot run |
| `market.output.directory` | `./reports` | Folder for Excel files |

### Data Interval

| Property | Default | Description |
|----------|---------|-------------|
| `market.data-interval` | `1h` | Bar interval (`1h`, `1d`, `1wk`, `1mo`) |
| `market.data-range` | `3mo` | Historical range (`5d`, `1mo`, `3mo`, `1y`, `2y`, `5y`) |

**Recommended pairings:**

| Interval | Recommended Range |
|----------|-------------------|
| `1h` | `3mo` |
| `1d` | `3mo` |
| `1wk` | `1y` |
| `1mo` | `5y` |

### RSI Thresholds

| Property | Default | Description |
|----------|---------|-------------|
| `market.rsi.period` | `14` | RSI look-back period |
| `market.rsi.ma-period` | `9` | SMA period applied to RSI |
| `market.rsi.bullish-range-min` | `30` | Lower bound of bullish RSI zone |
| `market.rsi.bullish-range-max` | `40` | Upper bound of bullish RSI zone |
| `market.rsi.overbought-level` | `70` | RSI threshold for OVERBOUGHT |
| `market.rsi.extreme-overbought-level` | `80` | RSI threshold for EXTREME OB |
| `market.rsi.critical-overbought-level` | `95` | RSI threshold for CRITICAL OB |
| `market.rsi.extreme-oversold-level` | `25` | RSI threshold for EXTREME OS |
| `market.rsi.critical-oversold-level` | `20` | RSI threshold for CRITICAL OS |

### Support / Resistance

| Property | Default | Description |
|----------|---------|-------------|
| `market.support-resistance-lookback` | `20` | Bars used for S/R calculation |
| `market.range-threshold-percent` | `3.0` | % band for "near support/resistance" |

---

## Project Structure

```
src/main/java/com/sharemarket/
├── SharemarketApplication.java          Main class + @EnableScheduling
├── config/
│   └── MarketConfig.java                @ConfigurationProperties binding
├── model/
│   ├── OHLCData.java                    One daily OHLCV bar
│   └── MarketSignal.java                Final signal + reason for one symbol
├── service/
│   ├── PriceDataService.java            Yahoo Finance HTTP fetch + JSON parse
│   ├── IndicatorService.java            TA4J RSI-14, SMA-9 of RSI, S/R levels
│   ├── SignalService.java               BUY / WAIT / SELL decision logic
│   └── ExcelReportService.java          Apache POI Excel writer
└── scheduler/
    ├── DailyMarketJob.java              @Scheduled cron job
    └── StartupRunner.java               Optional immediate run on startup
```

---

## Libraries Used

| Library | Version | Purpose |
|---------|---------|---------|
| Spring Boot | 3.2.5 | DI, scheduler, configuration binding |
| TA4J | 0.14 | RSI-14, SMA indicators |
| Apache POI | 5.2.5 | Write `.xlsx` files |
| Jackson | 2.x | Parse Yahoo Finance JSON |
| Lombok | latest | Boilerplate reduction (`@Data`, `@Builder`) |
| Lombok | Boilerplate reduction |
