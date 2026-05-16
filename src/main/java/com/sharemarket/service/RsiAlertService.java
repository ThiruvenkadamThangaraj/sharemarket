package com.sharemarket.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Sends RSI alert emails and enforces per-symbol cooldowns so the same
 * alert is not repeated every hour while RSI stays pinned at an extreme.
 *
 * Cooldown state is held in memory — it resets when the app restarts,
 * which is acceptable for a personal alerting tool.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RsiAlertService {

    private final JavaMailSender mailSender;

    @Value("${alert.email.to}")
    private String emailTo;

    @Value("${spring.mail.username}")
    private String emailFrom;

    @Value("${alert.email.from-name:RSI Alert Bot}")
    private String emailFromName;

    @Value("${alert.cooldown-hours:4}")
    private int cooldownHours;

    // ── Cooldown tracking ─────────────────────────────────────────────────────
    // Key format:  "SYMBOL:DIRECTION"   e.g.  "BTC-USD:OVERBOUGHT"
    private final Map<String, ZonedDateTime> lastAlertTime = new ConcurrentHashMap<>();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Evaluates the RSI and sends an email alert if the threshold is crossed
     * and the cooldown for that symbol+direction has expired.
     *
     * @param symbol     Yahoo Finance ticker, e.g. "BTC-USD"
     * @param rsi        current RSI value (1-hour candle)
     * @param overbought threshold above which an overbought alert fires
     * @param oversold   threshold below which an oversold alert fires
     * @param price      current close price (informational)
     * @param support    nearest support level (4h)
     * @param resistance nearest resistance level (4h)
     * @param pivots     Traditional Pivot Points (daily); may be null
     */
    public void evaluateAndAlert(String symbol, double rsi, double overbought,
                                  double oversold, double price,
                                  double support, double resistance,
                                  IndicatorService.PivotPoints pivots) {
        if (rsi >= overbought) {
            maybeSendAlert(symbol, rsi, price, support, resistance, pivots, AlertType.OVERBOUGHT);
        } else if (rsi <= oversold) {
            maybeSendAlert(symbol, rsi, price, support, resistance, pivots, AlertType.OVERSOLD);
        } else {
            // RSI is back in neutral zone — reset cooldown so next crossing fires
            resetCooldown(symbol, AlertType.OVERBOUGHT);
            resetCooldown(symbol, AlertType.OVERSOLD);
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void maybeSendAlert(String symbol, double rsi, double price,
                                 double support, double resistance,
                                 IndicatorService.PivotPoints pivots, AlertType type) {
        String key = symbol + ":" + type.name();
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime last = lastAlertTime.get(key);

        if (last != null && now.isBefore(last.plusHours(cooldownHours))) {
            log.info("Skipping {} alert for {} — cooldown active (last sent: {})", type, symbol, last);
            return;
        }

        lastAlertTime.put(key, now);
        sendEmail(symbol, rsi, price, support, resistance, pivots, type, now);
    }

    private void resetCooldown(String symbol, AlertType type) {
        lastAlertTime.remove(symbol + ":" + type.name());
    }

    private void sendEmail(String symbol, double rsi, double price,
                            double support, double resistance,
                            IndicatorService.PivotPoints pivots,
                            AlertType type, ZonedDateTime timestamp) {
        String subject = buildSubject(symbol, rsi, type);
        String body    = buildHtmlBody(symbol, rsi, price, support, resistance, pivots, type, timestamp);

        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");

            helper.setFrom(emailFrom, emailFromName);
            helper.setTo(emailTo);
            helper.setSubject(subject);
            helper.setText(body, true); // true = HTML

            mailSender.send(msg);
            log.info("Alert email sent → {} | {} | RSI={}", symbol, type, String.format("%.2f", rsi));

        } catch (MailException | MessagingException | java.io.UnsupportedEncodingException e) {
            log.error("Failed to send alert email for {}: {}", symbol, e.getMessage(), e);
        }
    }

    // ── Email content ─────────────────────────────────────────────────────────

    private String buildSubject(String symbol, double rsi, AlertType type) {
        String icon = (type == AlertType.OVERBOUGHT) ? "🔴" : "🟢";
        String dir  = (type == AlertType.OVERBOUGHT) ? "OVERBOUGHT" : "OVERSOLD";
        return String.format("%s RSI Alert: %s is %s (RSI=%.1f)", icon, symbol, dir, rsi);
    }

    private String buildHtmlBody(String symbol, double rsi, double price,
                                  double support, double resistance,
                                  IndicatorService.PivotPoints pivots,
                                  AlertType type, ZonedDateTime timestamp) {
        boolean isOver    = (type == AlertType.OVERBOUGHT);
        String  color     = isOver ? "#c0392b" : "#27ae60";
        String  direction = isOver ? "OVERBOUGHT (RSI ≥ 80)" : "OVERSOLD (RSI ≤ 30)";
        String  meaning   = isOver
            ? "RSI has reached overbought territory (≥ 80). Price is approaching resistance — consider reviewing for a potential pullback or sell signal."
            : "RSI has reached oversold territory (≤ 30). Price is approaching support — consider reviewing for a potential bounce or buy signal.";
        String  timeStr   = timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z"));

        // Distance from support/resistance
        String distToSupport    = support > 0
            ? String.format("%.2f  <span style='color:#888;font-size:0.9em;'>(%.1f%% away)</span>",
                support, ((price - support) / support) * 100.0)
            : "N/A";
        String distToResistance = resistance > 0
            ? String.format("%.2f  <span style='color:#888;font-size:0.9em;'>(%.1f%% away)</span>",
                resistance, ((resistance - price) / price) * 100.0)
            : "N/A";

        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='font-family:Arial,sans-serif;padding:20px;max-width:500px;'>")
          .append("<h2 style='color:").append(color).append(";'>RSI Alert: ").append(symbol).append("</h2>")
          .append("<table style='border-collapse:collapse;width:100%;'>")
          .append(sectionHeader("Price &amp; RSI", "#34495e"))
          .append(row("Symbol",         symbol))
          .append(row("Interval",       "1-Hour Candle"))
          .append(row("Current Price",  String.format("<b>$%.4f</b>", price)))
          .append(row("RSI (14)",        String.format("<b style='color:" + color + ";font-size:1.3em;'>%.2f</b>", rsi)))
          .append(row("RSI Oversold",   "<span style='color:#27ae60;font-weight:bold;'>30</span> → Buy zone"))
          .append(row("RSI Overbought", "<span style='color:#c0392b;font-weight:bold;'>80</span> → Sell zone"))
          .append(row("Signal",         "<b style='color:" + color + ";'>" + direction + "</b>"))
          .append(sectionHeader("4H Support &amp; Resistance", "#2c3e50"))
          .append(row("Support (4H)",    distToSupport))
          .append(row("Resistance (4H)", distToResistance));

        // ── Pivot Points section ──────────────────────────────────────────────
        if (pivots != null) {
            sb.append(sectionHeader("🔴 Red Zone (Resistance) &nbsp;&nbsp;🔵 Blue Zone (Support)", "#1a1a2e"));

            // Resistance levels — R1 is the start of the red zone
            sb.append(pivotRow("R5", pivots.r5(), price, false, false))
              .append(pivotRow("R4", pivots.r4(), price, false, false))
              .append(pivotRow("R3", pivots.r3(), price, false, false))
              .append(pivotRow("R2", pivots.r2(), price, false, false))
              .append(pivotRow("R1 🔴 Red Zone Start", pivots.r1(), price, false, true));

            // Pivot
            sb.append(pivotRow("P (Pivot)", pivots.p(), price, false, false));

            // Support levels — S4 is the start of the blue zone
            sb.append(pivotRow("S1", pivots.s1(), price, false, false))
              .append(pivotRow("S2", pivots.s2(), price, false, false))
              .append(pivotRow("S3", pivots.s3(), price, false, false))
              .append(pivotRow("S4 🔵 Blue Zone Start", pivots.s4(), price, true, false))
              .append(pivotRow("S5", pivots.s5(), price, false, false));
        }

        sb.append(sectionHeader("Time", "#2c3e50"))
          .append(row("Time (UTC)", timeStr))
          .append("</table>")
          .append("<p style='color:#555;margin-top:16px;'>").append(meaning).append("</p>")
          .append("<p style='color:#aaa;font-size:11px;'>This is an automated alert from your RSI Alert Bot. "
              + "Not financial advice.</p>")
          .append("</body></html>");

        return sb.toString();
    }

    /** Renders a pivot level row, optionally highlighted as the red or blue zone boundary. */
    private String pivotRow(String label, double level, double price,
                             boolean isBlueZone, boolean isRedZone) {
        String bg    = isRedZone  ? "#fff0f0"
                     : isBlueZone ? "#f0f4ff"
                     : "";
        String badge = isRedZone  ? " <span style='color:#c0392b;font-weight:bold;'>▲ SELL ZONE</span>"
                     : isBlueZone ? " <span style='color:#2563eb;font-weight:bold;'>▼ BUY ZONE</span>"
                     : "";
        String dist  = price > 0
            ? String.format("<span style='color:#888;font-size:0.85em;'> (%.1f%% from price)</span>",
                ((level - price) / price) * 100.0)
            : "";
        String tdStyle = "padding:5px 12px;border:1px solid #ddd;" + (bg.isEmpty() ? "" : "background:" + bg + ";");
        return "<tr>"
            + "<td style='" + tdStyle + "font-weight:bold;'>" + label + badge + "</td>"
            + "<td style='" + tdStyle + "'>" + String.format("%.2f", level) + dist + "</td>"
            + "</tr>";
    }

    private String sectionHeader(String title, String bg) {
        return "<tr><td colspan='2' style='padding:8px 12px;background:" + bg
            + ";color:#fff;font-weight:bold;font-size:0.9em;letter-spacing:0.05em;'>"
            + title + "</td></tr>";
    }

    private String row(String label, String value) {
        return "<tr>"
            + "<td style='padding:6px 12px;border:1px solid #ddd;background:#f8f8f8;font-weight:bold;'>"
            + label + "</td>"
            + "<td style='padding:6px 12px;border:1px solid #ddd;'>" + value + "</td>"
            + "</tr>";
    }

    // ── Types ─────────────────────────────────────────────────────────────────

    public enum AlertType { OVERBOUGHT, OVERSOLD }
}
