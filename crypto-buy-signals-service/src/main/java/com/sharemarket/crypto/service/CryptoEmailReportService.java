package com.sharemarket.crypto.service;

import com.sharemarket.crypto.model.CoinSignalResponse;
import com.sharemarket.crypto.model.Decision;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.File;
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
     * Sends the Excel report at {@code reportPath} as an email attachment,
     * with a short HTML summary of BUY signals in the body.
     */
    public void sendReport(String reportPath, List<CoinSignalResponse> rows) {
        String subject = buildSubject(rows);
        String body    = buildHtmlBody(rows);
        File   file    = new File(reportPath);

        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(emailFrom, emailFromName);
            helper.setTo(emailTo);
            helper.setSubject(subject);
            helper.setText(body, true);
            helper.addAttachment(file.getName(), new FileSystemResource(file));
            mailSender.send(msg);
            log.info("Crypto report email sent → {} | BUY signals: {}", emailTo,
                rows.stream().filter(r -> r.decision() == Decision.BUY).count());
        } catch (MailException | MessagingException | java.io.UnsupportedEncodingException e) {
            log.error("Failed to send crypto report email: {}", e.getMessage(), e);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String buildSubject(List<CoinSignalResponse> rows) {
        long buyCount = rows.stream().filter(r -> r.decision() == Decision.BUY).count();
        String ts = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z"));
        return String.format("Crypto Buy Signals Report — %d BUY signal%s | %s",
            buyCount, buyCount == 1 ? "" : "s", ts);
    }

    private String buildHtmlBody(List<CoinSignalResponse> rows) {
        String ts = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z"));

        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='font-family:Arial,sans-serif;font-size:14px;'>");
        sb.append("<h2 style='color:#1F497D;'>Crypto Buy Signals Report</h2>");
        sb.append("<p style='color:#555;'>Generated: ").append(ts).append("</p>");

        // BUY signals table
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
            .append(" symbol(s) in WAIT — see attached Excel for full details.</p>");

        sb.append("<hr/><p style='font-size:11px;color:#aaa;'>")
            .append("This is an automated report. Full data is attached as an Excel file.</p>");
        sb.append("</body></html>");
        return sb.toString();
    }
}
