package com.sharemarket.service;

import com.sharemarket.config.MarketConfig;
import com.sharemarket.model.MarketSignal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Writes the daily market signals into a colour-coded Excel (.xlsx) dashboard.
 *
 * Layout
 * ──────
 *  Row 0 : Title banner (merged)
 *  Row 1 : Column headers
 *  Row 2+ : One row per symbol
 *
 * Signal cell colours
 * ───────────────────
 *  BUY CONFIRM → deep green
 *  BULLISH START → medium green
 *  BUY         → light green
 *  SHORT CONFIRM → red
 *  OVERBOUGHT  → orange
 *  OVERSOLD    → blue
 *  WAIT / other→ amber
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelReportService {

    private final MarketConfig config;

    private static final String[] HEADERS = {
        "Symbol", "Type", "Sector", "Current Price", "RSI (Blue)",
        "RSI MA (Yellow)", "Signal", "Reason", "Support", "Resistance"
    };

    // ── Signal colours (RGB bytes) ─────────────────────────────────────────
    private static final byte[] CLR_BUY_CONFIRM = rgb(0x00, 0xB0, 0x50);  // deep green
    private static final byte[] CLR_BULLISH_START = rgb(0x2E, 0x7D, 0x32); // medium green
    private static final byte[] CLR_BUY         = rgb(0x92, 0xD0, 0x50);  // light green
    private static final byte[] CLR_SELL        = rgb(0xC0, 0x00, 0x00);  // red
    private static final byte[] CLR_OVERBOUGHT  = rgb(0xFF, 0x66, 0x00);  // orange
    private static final byte[] CLR_EXTREME_OB   = rgb(0xB7, 0x00, 0x00);  // deep red
    private static final byte[] CLR_CRITICAL_OB  = rgb(0x66, 0x00, 0x00);  // very dark red/maroon
    private static final byte[] CLR_EXTREME_OS   = rgb(0x00, 0x4D, 0xB7);  // deep blue
    private static final byte[] CLR_CRITICAL_OS  = rgb(0x00, 0x1A, 0x66);  // very dark navy
    private static final byte[] CLR_OVERSOLD    = rgb(0x00, 0x70, 0xC0);  // blue
    private static final byte[] CLR_WAIT        = rgb(0xFF, 0xC0, 0x00);  // amber

    // ── Row background colours ─────────────────────────────────────────────
    private static final byte[] CLR_HEADER   = rgb(0x1F, 0x49, 0x7D);  // dark navy
    private static final byte[] CLR_TITLE_BG = rgb(0x17, 0x37, 0x5E);  // darker navy

    // ── Column widths (in 1/256 of a character width, POI unit) ───────────
    private static final int[] COL_WIDTHS = {
        14, 10, 18, 18, 14, 16, 16, 60, 14, 14
    };

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Writes the report and returns the full path to the generated file.
     */
    public String writeReport(List<MarketSignal> signals) throws IOException {
        // Use date+time so multiple same-day runs never conflict with an open file
        LocalDateTime now   = LocalDateTime.now();
        String date         = now.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String timestamp    = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"));
        File   dir          = new File(config.getOutput().getDirectory());
        if (!dir.exists()) {
            dir.mkdirs();
        }
        double rangeMin       = config.getRsi().getBullishRangeMin();
        double rangeMax       = config.getRsi().getBullishRangeMax();
        double overboughtLevel = config.getRsi().getOverboughtLevel();
        String configSuffix   = String.format("_RSI%.0f-%.0f_OB%.0f", rangeMin, rangeMax, overboughtLevel);
        String filePath = dir.getAbsolutePath() + File.separator
            + "market-report-" + timestamp + configSuffix + ".xlsx";

        try (XSSFWorkbook  workbook = new XSSFWorkbook();
             FileOutputStream fos  = new FileOutputStream(filePath)) {

            XSSFSheet sheet = workbook.createSheet("Market Signals " + date);
            sheet.createFreezePane(0, 2);   // freeze title + header rows

            for (int i = 0; i < COL_WIDTHS.length; i++) {
                sheet.setColumnWidth(i, COL_WIDTHS[i] * 256);
            }

            writeTitleRow(workbook, sheet, date);
            writeHeaderRow(workbook, sheet);
            writeDataRows(workbook, sheet, signals);

            workbook.write(fos);
        }

        log.info("Excel report written → {}", filePath);
        return filePath;
    }

    // ── Row writers ────────────────────────────────────────────────────────

    private void writeTitleRow(XSSFWorkbook wb, XSSFSheet sheet, String date) {
        Row row = sheet.createRow(0);
        row.setHeightInPoints(28);

        Cell cell = row.createCell(0);
        cell.setCellValue("DAILY MARKET SIGNAL REPORT  ·  " + date
            + "  ·  Interval: " + config.getDataInterval()
            + "  ·  RSI-" + config.getRsi().getPeriod()
            + " vs SMA-" + config.getRsi().getMaPeriod());
        cell.setCellStyle(buildTitleStyle(wb));
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, HEADERS.length - 1));
    }

    private void writeHeaderRow(XSSFWorkbook wb, XSSFSheet sheet) {
        Row         row   = sheet.createRow(1);
        CellStyle   style = buildHeaderStyle(wb);
        row.setHeightInPoints(20);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(style);
        }
    }

    private void writeDataRows(XSSFWorkbook wb, XSSFSheet sheet,
                               List<MarketSignal> signals) {
        int    rowNum        = 2;
        String currentSector = null;

        for (MarketSignal s : signals) {
            String sector = s.getSector() != null ? s.getSector() : s.getType();

            // Insert a coloured sector-header divider row when the sector changes
            if (!sector.equals(currentSector)) {
                currentSector = sector;
                Row  hdrRow  = sheet.createRow(rowNum++);
                hdrRow.setHeightInPoints(20);
                Cell hdrCell = hdrRow.createCell(0);
                hdrCell.setCellValue("\u25B6  " + sector.toUpperCase());
                hdrCell.setCellStyle(buildSectorHeaderStyle(wb, sector));
                sheet.addMergedRegion(new CellRangeAddress(
                    rowNum - 1, rowNum - 1, 0, HEADERS.length - 1));
            }

            Row  row    = sheet.createRow(rowNum++);
            int lines = estimateWrappedLines(s.getReason(), COL_WIDTHS[7]);
            row.setHeightInPoints(lines * 15f);

            byte[]    rowBg  = getSectorRowColor(sector);
            CellStyle base   = buildDataStyle(wb, rowBg);
            CellStyle number = buildNumberStyle(wb, rowBg);
            CellStyle signal = buildSignalStyle(wb, s.getSignal());

            putString(row, 0, s.getSymbol(),       base);
            putString(row, 1, s.getType(),         base);
            putString(row, 2, sector,              base);
            putDouble(row, 3, s.getCurrentPrice(), number);
            putDouble(row, 4, s.getRsi(),          number);
            putDouble(row, 5, s.getRsiMA(),        number);
            putString(row, 6, s.getSignal(),       signal);
            putString(row, 7, s.getReason(),       base);
            putDouble(row, 8, s.getSupport(),      number);
            putDouble(row, 9, s.getResistance(),   number);
        }
    }

    // ── Style builders ─────────────────────────────────────────────────────

    private CellStyle buildTitleStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(CLR_TITLE_BG, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFFont f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 14);
        f.setColor(new XSSFColor(rgb(0xFF, 0xFF, 0xFF), null));
        s.setFont(f);
        return s;
    }

    private CellStyle buildHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(CLR_HEADER, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        addBorders(s);
        XSSFFont f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 11);
        f.setColor(new XSSFColor(rgb(0xFF, 0xFF, 0xFF), null));
        s.setFont(f);
        return s;
    }

    private CellStyle buildDataStyle(XSSFWorkbook wb, byte[] rowBg) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(rowBg, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setVerticalAlignment(VerticalAlignment.TOP);
        s.setWrapText(true);
        addBorders(s);
        return s;
    }

    private CellStyle buildNumberStyle(XSSFWorkbook wb, byte[] rowBg) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(rowBg, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.RIGHT);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
        addBorders(s);
        return s;
    }

    private CellStyle buildSignalStyle(XSSFWorkbook wb, String signal) {
        byte[] color = switch (signal) {
            case "BUY CONFIRM" -> CLR_BUY_CONFIRM;
            case "BULLISH START" -> CLR_BULLISH_START;
            case "BUY"        -> CLR_BUY;
            case "SHORT CONFIRM" -> CLR_SELL;
            case "CRITICAL OB" -> CLR_CRITICAL_OB;
            case "EXTREME OB"  -> CLR_EXTREME_OB;
            case "OVERBOUGHT" -> CLR_OVERBOUGHT;
            case "EXTREME OS"  -> CLR_EXTREME_OS;
            case "CRITICAL OS" -> CLR_CRITICAL_OS;
            case "OVERSOLD"   -> CLR_OVERSOLD;
            default           -> CLR_WAIT;          // WAIT, ERROR, …
        };
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(color, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        addBorders(s);
        XSSFFont f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 11);
        // White text on dark colours; black text on light colours
        boolean darkBg = Signal.isDark(signal);
        f.setColor(new XSSFColor(darkBg ? rgb(0xFF,0xFF,0xFF) : rgb(0x00,0x00,0x00), null));
        s.setFont(f);
        return s;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /** Light pastel row background colour keyed by sector display name. */
    private static byte[] getSectorRowColor(String sector) {
        return switch (sector != null ? sector : "") {
            case "Crypto"         -> rgb(0xED, 0xED, 0xFF);  // lavender
            case "Big Tech"       -> rgb(0xE8, 0xF5, 0xE9);  // light green
            case "Semiconductors" -> rgb(0xFF, 0xFD, 0xE7);  // light yellow
            case "Financials"     -> rgb(0xE3, 0xF2, 0xFD);  // light blue
            case "Consumer"       -> rgb(0xFF, 0xF3, 0xE0);  // light orange
            case "Healthcare"     -> rgb(0xFC, 0xE4, 0xEC);  // light pink
            case "Energy"         -> rgb(0xF3, 0xE5, 0xF5);  // light purple
            case "Industrial"     -> rgb(0xF5, 0xF5, 0xF5);  // light gray
            default               -> rgb(0xFF, 0xFF, 0xFF);
        };
    }

    /** Dark header-row colour keyed by sector display name. */
    private static byte[] getSectorHeaderColor(String sector) {
        return switch (sector != null ? sector : "") {
            case "Crypto"         -> rgb(0x5C, 0x35, 0xC0);  // purple
            case "Big Tech"       -> rgb(0x1B, 0x5E, 0x20);  // dark green
            case "Semiconductors" -> rgb(0xF5, 0x7F, 0x17);  // gold
            case "Financials"     -> rgb(0x0D, 0x47, 0xA1);  // dark blue
            case "Consumer"       -> rgb(0xE6, 0x51, 0x00);  // dark orange
            case "Healthcare"     -> rgb(0xAD, 0x14, 0x57);  // dark pink/red
            case "Energy"         -> rgb(0x4A, 0x14, 0x8C);  // dark purple
            case "Industrial"     -> rgb(0x42, 0x42, 0x42);  // dark gray
            default               -> rgb(0x33, 0x33, 0x33);
        };
    }

    private CellStyle buildSectorHeaderStyle(XSSFWorkbook wb, String sector) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(getSectorHeaderColor(sector), null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.LEFT);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFFont f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 11);
        f.setColor(new XSSFColor(rgb(0xFF, 0xFF, 0xFF), null));
        s.setFont(f);
        return s;
    }

    private enum Signal {
        BUY_CONFIRM, BULLISH_START, BUY, SHORT_CONFIRM, OVERBOUGHT, OVERSOLD, WAIT;

        static boolean isDark(String signalStr) {
            return switch (signalStr) {
                case "BUY CONFIRM", "BULLISH START", "SHORT CONFIRM", "CRITICAL OB", "EXTREME OB", "OVERBOUGHT", "EXTREME OS", "OVERSOLD", "CRITICAL OS" -> true;
                default -> false;  // BUY (light green), WAIT (amber) → black text
            };
        }
    }

    private static void addBorders(XSSFCellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private static void putString(Row row, int col, String val, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(val != null ? val : "");
        c.setCellStyle(style);
    }

    private static void putDouble(Row row, int col, double val, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(val);
        c.setCellStyle(style);
    }

    /** Estimates how many wrapped lines a text will need given a column width in chars. */
    private static int estimateWrappedLines(String text, int colWidthChars) {
        if (text == null || text.isEmpty()) return 1;
        // Each character is ~1 unit; column width in chars is approximate
        int charsPerLine = Math.max(colWidthChars, 10);
        int lines = (int) Math.ceil((double) text.length() / charsPerLine);
        return Math.min(Math.max(lines, 1), 5);
    }

    private static byte[] rgb(int r, int g, int b) {
        return new byte[]{(byte) r, (byte) g, (byte) b};
    }
}
