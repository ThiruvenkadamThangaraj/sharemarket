package com.sharemarket.crypto.service;

import com.sharemarket.crypto.model.CoinSignalResponse;
import com.sharemarket.crypto.model.Decision;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Sends the crypto buy-signals Excel report as an email attachment.
 */
@Slf4j
@Service
public class CryptoEmailReportService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String emailFrom;

    @Value("${alert.email.to}")
    private String emailTo;

    @Value("${alert.email.from-name:Crypto Signal Bot}")
    private String emailFromName;

    public CryptoEmailReportService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Sends the crypto buy-signals report as an HTML email (no attachment).
     */
    public void sendReport(String reportPath, List<CoinSignalResponse> rows) {
        String subject = buildSubject(rows);
        String body    = buildHtmlBody(rows);

        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
            helper.setFrom(emailFrom, emailFromName);
            helper.setTo(emailTo);
            helper.setSubject(subject);
            helper.setText(body, true);
            mailSender.send(msg);
            log.info("Crypto report email sent → {} | BUY: {} | SELL: {}", emailTo,
                rows.stream().filter(r -> r.decision() == Decision.BUY).count(),
                rows.stream().filter(r -> r.decision() == Decision.SELL).count());
        } catch (MailException | MessagingException | java.io.UnsupportedEncodingException e) {
            log.error("Failed to send crypto report email: {}", e.getMessage(), e);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String buildSubject(List<CoinSignalResponse> rows) {
        long buyCount  = rows.stream().filter(r -> r.decision() == Decision.BUY).count();
        long sellCount = rows.stream().filter(r -> r.decision() == Decision.SELL).count();
        String ts = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z"));
        return String.format("Crypto Signals — %d BUY | %d SELL | %s", buyCount, sellCount, ts);
    }

    private String buildHtmlBody(List<CoinSignalResponse> rows) {
        String ts = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z"));

        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='font-family:Arial,sans-serif;font-size:14px;'>");
        sb.append("<h2 style='color:#1F497D;'>Crypto Buy Signals Report</h2>");
        sb.append("<p style='color:#555;'>Generated: ").append(ts).append("</p>");

        // ── SELL signals ──────────────────────────────────────────────────────
        List<CoinSignalResponse> sells = rows.stream()
            .filter(r -> r.decision() == Decision.SELL)
            .toList();

        if (!sells.isEmpty()) {
            sb.append("<h3 style='color:#cc0000;'>&#x1F534; SELL Signals (").append(sells.size()).append(")</h3>");
            sb.append("<table border='1' cellpadding='6' cellspacing='0' style='border-collapse:collapse;'>");
            sb.append("<tr style='background:#cc0000;color:white;'>");
            sb.append("<th>Symbol</th><th>Price</th><th>Sell Target</th><th>RSI</th><th>Above All MAs</th><th>Reason</th>");
            sb.append("</tr>");
            for (CoinSignalResponse r : sells) {
                sb.append("<tr style='background:#ffebee;'>");
                sb.append("<td><b>").append(r.symbol()).append("</b></td>");
                sb.append("<td>").append(String.format("%.4f", r.currentPrice())).append("</td>");
                sb.append("<td>").append(r.sellTarget() != null
                    ? String.format("%.4f", r.sellTarget()) : "—").append("</td>");
                sb.append("<td>").append(String.format("%.2f", r.rsi())).append("</td>");
                sb.append("<td>").append(r.aboveAllMAs() ? "YES" : "NO").append("</td>");
                sb.append("<td style='font-size:12px;'>").append(r.reason()).append("</td>");
                sb.append("</tr>");
            }
            sb.append("</table>");
        }

        // ── SELL_WATCH signals ────────────────────────────────────────────────
        List<CoinSignalResponse> sellWatches = rows.stream()
            .filter(r -> r.decision() == Decision.SELL_WATCH)
            .toList();

        if (!sellWatches.isEmpty()) {
            sb.append("<h3 style='color:#e65c00;'>&#x1F7E0; SELL_WATCH Signals (").append(sellWatches.size()).append(")</h3>");
            sb.append("<table border='1' cellpadding='6' cellspacing='0' style='border-collapse:collapse;'>");
            sb.append("<tr style='background:#e65c00;color:white;'>");
            sb.append("<th>Symbol</th><th>Price</th><th>Sell Target</th><th>RSI</th><th>Reason</th>");
            sb.append("</tr>");
            for (CoinSignalResponse r : sellWatches) {
                sb.append("<tr style='background:#fff3e0;'>");
                sb.append("<td><b>").append(r.symbol()).append("</b></td>");
                sb.append("<td>").append(String.format("%.4f", r.currentPrice())).append("</td>");
                sb.append("<td>").append(r.sellTarget() != null
                    ? String.format("%.4f", r.sellTarget()) : "—").append("</td>");
                sb.append("<td>").append(String.format("%.2f", r.rsi())).append("</td>");
                sb.append("<td style='font-size:12px;'>").append(r.reason()).append("</td>");
                sb.append("</tr>");
            }
            sb.append("</table>");
        }

        // ── BUY signals ───────────────────────────────────────────────────────
        List<CoinSignalResponse> buys = rows.stream()
            .filter(r -> r.decision() == Decision.BUY)
            .toList();

        if (buys.isEmpty()) {
            sb.append("<p><b>No BUY signals at this time.</b></p>");
        } else {
            sb.append("<h3 style='color:#1a7a1a;'>&#x2705; BUY Signals (").append(buys.size()).append(")</h3>");
            sb.append("<table border='1' cellpadding='6' cellspacing='0' style='border-collapse:collapse;'>");
            sb.append("<tr style='background:#1F497D;color:white;'>");
            sb.append("<th>Symbol</th><th>Price</th><th>RSI</th><th>Strength</th><th>Reason</th>");
            sb.append("</tr>");
            for (CoinSignalResponse r : buys) {
                sb.append("<tr style='background:#e8f5e9;'>");
                sb.append("<td><b>").append(r.symbol()).append("</b></td>");
                sb.append("<td>").append(String.format("%.4f", r.currentPrice())).append("</td>");
                sb.append("<td>").append(String.format("%.2f", r.rsi())).append("</td>");
                sb.append("<td>").append(r.strength().name()).append("</td>");
                sb.append("<td style='font-size:12px;'>").append(r.reason()).append("</td>");
                sb.append("</tr>");
            }
            sb.append("</table>");
        }

        // WAIT signals summary
        long waitCount = rows.stream().filter(r -> r.decision() == Decision.WAIT).count();
        sb.append("<p style='color:#888;margin-top:16px;'>").append(waitCount)
            .append(" symbol(s) in WAIT.</p>");

        sb.append("<hr/><p style='font-size:11px;color:#aaa;'>"
            + "This is an automated hourly report.</p>");
        sb.append("</body></html>");
        return sb.toString();
    }
}
