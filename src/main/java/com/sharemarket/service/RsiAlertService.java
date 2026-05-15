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
     */
    public void evaluateAndAlert(String symbol, double rsi, double overbought,
                                  double oversold, double price) {
        if (rsi >= overbought) {
            maybeSendAlert(symbol, rsi, price, AlertType.OVERBOUGHT);
        } else if (rsi <= oversold) {
            maybeSendAlert(symbol, rsi, price, AlertType.OVERSOLD);
        } else {
            // RSI is back in neutral zone — reset cooldown so next crossing fires
            resetCooldown(symbol, AlertType.OVERBOUGHT);
            resetCooldown(symbol, AlertType.OVERSOLD);
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void maybeSendAlert(String symbol, double rsi, double price, AlertType type) {
        String key = symbol + ":" + type.name();
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime last = lastAlertTime.get(key);

        if (last != null && now.isBefore(last.plusHours(cooldownHours))) {
            log.info("Skipping {} alert for {} — cooldown active (last sent: {})", type, symbol, last);
            return;
        }

        lastAlertTime.put(key, now);
        sendEmail(symbol, rsi, price, type, now);
    }

    private void resetCooldown(String symbol, AlertType type) {
        lastAlertTime.remove(symbol + ":" + type.name());
    }

    private void sendEmail(String symbol, double rsi, double price,
                            AlertType type, ZonedDateTime timestamp) {
        String subject = buildSubject(symbol, rsi, type);
        String body    = buildHtmlBody(symbol, rsi, price, type, timestamp);

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
                                  AlertType type, ZonedDateTime timestamp) {
        boolean isOver    = (type == AlertType.OVERBOUGHT);
        String  color     = isOver ? "#c0392b" : "#27ae60";
        String  direction = isOver ? "OVERBOUGHT" : "OVERSOLD";
        String  meaning   = isOver
            ? "RSI is in overbought territory. Consider reviewing for a potential pullback or sell signal."
            : "RSI is in oversold territory. Consider reviewing for a potential bounce or buy signal.";
        String  timeStr   = timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z"));

        return "<html><body style='font-family:Arial,sans-serif;padding:20px;'>"
            + "<h2 style='color:" + color + ";'>RSI Alert: " + symbol + " is " + direction + "</h2>"
            + "<table style='border-collapse:collapse;width:320px;'>"
            + row("Symbol",    symbol)
            + row("Interval",  "1-Hour Candle")
            + row("RSI (14)",  String.format("<b style='color:" + color + ";font-size:1.3em;'>%.2f</b>", rsi))
            + row("Price",     String.format("$%.4f", price))
            + row("Direction", "<b>" + direction + "</b>")
            + row("Time (UTC)", timeStr)
            + "</table>"
            + "<p style='color:#555;margin-top:16px;'>" + meaning + "</p>"
            + "<p style='color:#aaa;font-size:11px;'>This is an automated alert from your RSI Alert Bot. "
            + "Not financial advice.</p>"
            + "</body></html>";
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
