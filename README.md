# Share Market Daily Signal Generator

A Spring Boot service that runs every day after market close, fetches price data from Yahoo Finance, calculates RSI-14 and its moving average with TA4J, applies BUY / WAIT / SELL logic, and writes a colour-coded Excel dashboard.

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
ExcelReportService         ← Apache POI → market-report-YYYY-MM-DD.xlsx
      ▲
      │
DailyMarketJob             ← @Scheduled cron (default 21:05 UTC daily)
```

---

## Signal Logic

| Condition | Signal |
|-----------|--------|
| RSI < 30 | **OVERSOLD** (potential buy zone) |
| RSI > 70 | **OVERBOUGHT** (potential sell zone) |
| RSI blue > yellow AND price near support | **BUY CONFIRM** ✅ |
| RSI blue > yellow | **BUY** |
| RSI blue < yellow AND price near resistance | **SELL** ❌ |
| RSI blue < yellow | **WAIT** |

*"Near support/resistance" = within 3 % of the 20-day low/high (configurable).*

---

## Excel Dashboard Output

| Symbol | Type | Current Price | RSI (Blue) | RSI MA (Yellow) | Signal | Reason | Support | Resistance |
|--------|------|--------------|------------|-----------------|--------|--------|---------|------------|
| ETH-USD | CRYPTO | 2,350.00 | 52.0 | 49.0 | **BUY CONFIRM** | RSI blue above yellow + near support | 2,200 | 2,650 |
| BTC-USD | CRYPTO | 67,000.00 | 48.0 | 51.0 | **WAIT** | RSI below yellow | 62,000 | 71,000 |
| NFLX | STOCK | 93.00 | 45.0 | 48.0 | **WAIT** | No bullish confirmation | 88 | 102 |

Signal cells are colour-coded:
- 🟢 **BUY CONFIRM** — deep green
- 🟩 **BUY** — light green
- 🔴 **SELL** — red
- 🟠 **OVERBOUGHT** — orange
- 🔵 **OVERSOLD** — blue
- 🟡 **WAIT** — amber

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 17+ |
| Maven | 3.8+ |

No external API key is needed — Yahoo Finance public endpoint is used.

---

## Running

### Build

```bash
mvn clean package -DskipTests
```

### Run once immediately (test / ad-hoc)

```bash
# Edit application.properties:  market.run-on-startup=true
mvn spring-boot:run
```

Or with the packaged jar:

```bash
java -jar target/sharemarket-1.0.0.jar
```

### Run as a daemon (scheduled daily)

```bash
# Keep market.run-on-startup=false (default)
java -jar target/sharemarket-1.0.0.jar
```

The scheduler keeps the JVM alive and fires every day at 21:05 UTC.

### Override the schedule at runtime

```bash
java -jar target/sharemarket-1.0.0.jar --market.scheduler.cron="0 0 22 * * *"
```

---

## Configuration

All settings live in `src/main/resources/application.properties`:

| Property | Default | Description |
|----------|---------|-------------|
| `market.scheduler.cron` | `0 5 21 * * *` | Cron trigger (sec min hr day month weekday) |
| `market.run-on-startup` | `false` | Set `true` for immediate one-shot run |
| `market.output.directory` | `./reports` | Folder for Excel files |
| `market.symbols.crypto` | `BTC-USD,ETH-USD,…` | Comma-separated crypto tickers |
| `market.symbols.stocks` | `AAPL,MSFT,…` | Comma-separated stock tickers |
| `market.rsi.period` | `14` | RSI look-back period |
| `market.rsi.ma-period` | `9` | SMA period applied to RSI |
| `market.data-range` | `3mo` | Historical range fetched from Yahoo Finance |
| `market.support-resistance-lookback` | `20` | Bars used for support/resistance |
| `market.range-threshold-percent` | `3.0` | % band around support/resistance |

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

| Library | Purpose |
|---------|---------|
| Spring Boot 3 | DI, scheduler, configuration binding |
| TA4J 0.14 | RSI-14, SMA indicators |
| Apache POI 5 | Write .xlsx files |
| Jackson | Parse Yahoo Finance JSON |
| Lombok | Boilerplate reduction |
