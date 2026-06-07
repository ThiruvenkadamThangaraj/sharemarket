package com.sharemarket.crypto.service;

import com.sharemarket.crypto.config.CryptoReportProperties;
import com.sharemarket.crypto.model.CoinSignalResponse;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class CryptoExcelReportService {

    private static final Logger log = LoggerFactory.getLogger(CryptoExcelReportService.class);

    private static final String[] HEADERS = {
        "Symbol", "Current Price", "ATH", "50% ATH Threshold", "RSI",
        "MA 50", "MA 100", "MA 200", "MA Hits", "Scenario", "Decision", "Strength",
        "ATH Signal", "RSI Signal", "MA Signal",
        "Sell Target", "Above All MAs", "Profit Target Met",
        "Reason"
    };

    private final CryptoReportProperties reportProperties;

    public CryptoExcelReportService(CryptoReportProperties reportProperties) {
        this.reportProperties = reportProperties;
    }

    public String writeReport(List<CoinSignalResponse> rows) {
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"));

        File dir = new File(reportProperties.getOutputDirectory());
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Failed to create report directory: " + dir.getAbsolutePath());
        }

        String filePath = dir.getAbsolutePath() + File.separator + "crypto-buy-signals-" + timestamp + ".xlsx";

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(filePath)) {

            XSSFSheet sheet = workbook.createSheet("Crypto Buy Signals");
            writeHeaderRow(workbook, sheet);
            writeDataRows(workbook, sheet, rows);

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(fos);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write report: " + ex.getMessage(), ex);
        }

        log.info("Crypto report written: {}", filePath);
        return filePath;
    }

    private void writeHeaderRow(XSSFWorkbook workbook, XSSFSheet sheet) {
        Row row = sheet.createRow(0);
        CellStyle style = buildHeaderStyle(workbook);

        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(style);
        }
    }

    private void writeDataRows(XSSFWorkbook workbook, XSSFSheet sheet, List<CoinSignalResponse> rows) {
        CellStyle base = buildBaseStyle(workbook);
        CellStyle number = buildNumberStyle(workbook);

        int rowNum = 1;
        for (CoinSignalResponse rowData : rows) {
            Row row = sheet.createRow(rowNum++);

            putString(row, 0, rowData.symbol(), base);
            putDouble(row, 1, rowData.currentPrice(), number);
            putDouble(row, 2, rowData.athPrice(), number);
            putDouble(row, 3, rowData.athDiscountThreshold(), number);
            putDouble(row, 4, rowData.rsi(), number);

            Map<Integer, Double> ma = rowData.movingAverages();
            putDouble(row, 5, ma.getOrDefault(50, Double.NaN), number);
            putDouble(row, 6, ma.getOrDefault(100, Double.NaN), number);
            putDouble(row, 7, ma.getOrDefault(200, Double.NaN), number);

            putDouble(row, 8, rowData.movingAverageHits(), number);
            putString(row, 9, rowData.scenario(), base);
            putString(row, 10, rowData.decision().name(), buildDecisionStyle(workbook, rowData.decision().name()));
            putString(row, 11, rowData.strength().name(), base);
            putString(row, 12, yesNo(rowData.signalAthDiscountMet()), base);
            putString(row, 13, yesNo(rowData.signalRsiMet()), base);
            putString(row, 14, yesNo(rowData.signalMovingAverageMet()), base);

            // Sell-signal columns
            if (rowData.sellTarget() != null) {
                putDouble(row, 15, rowData.sellTarget(), number);
            } else {
                putString(row, 15, "—", base);
            }
            putString(row, 16, yesNo(rowData.aboveAllMAs()), base);
            putString(row, 17, yesNo(rowData.profitTargetMet()), base);

            putString(row, 18, rowData.reason(), base);
        }
    }

    private CellStyle buildHeaderStyle(XSSFWorkbook workbook) {
        XSSFCellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(new XSSFColor(rgb(31, 73, 125), null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        addBorders(style);

        XSSFFont font = workbook.createFont();
        font.setBold(true);
        font.setColor(new XSSFColor(rgb(255, 255, 255), null));
        style.setFont(font);
        return style;
    }

    private CellStyle buildBaseStyle(XSSFWorkbook workbook) {
        XSSFCellStyle style = workbook.createCellStyle();
        addBorders(style);
        return style;
    }

    private CellStyle buildNumberStyle(XSSFWorkbook workbook) {
        XSSFCellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
        addBorders(style);
        return style;
    }

    private CellStyle buildDecisionStyle(XSSFWorkbook workbook, String decision) {
        XSSFCellStyle style = workbook.createCellStyle();
        addBorders(style);

        byte[] bg = switch (decision) {
            case "BUY"        -> rgb(0, 176, 80);
            case "SELL"       -> rgb(192, 0, 0);
            case "SELL_WATCH" -> rgb(255, 102, 0);
            default           -> rgb(255, 192, 0);
        };

        style.setFillForegroundColor(new XSSFColor(bg, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private static void addBorders(XSSFCellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private static void putString(Row row, int col, String val, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(val != null ? val : "");
        cell.setCellStyle(style);
    }

    private static void putDouble(Row row, int col, double val, CellStyle style) {
        Cell cell = row.createCell(col);
        if (Double.isNaN(val)) {
            cell.setCellValue("N/A");
        } else {
            cell.setCellValue(val);
        }
        cell.setCellStyle(style);
    }

    private static String yesNo(boolean value) {
        return value ? "YES" : "NO";
    }

    private static byte[] rgb(int r, int g, int b) {
        return new byte[]{(byte) r, (byte) g, (byte) b};
    }
}
