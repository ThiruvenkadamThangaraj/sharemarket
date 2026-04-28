package com.sharemarket.smc.service;

import com.sharemarket.smc.config.SmcMarketConfig;
import com.sharemarket.smc.model.SmcReportRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmcExcelReportService {

    private final SmcMarketConfig config;

    public String writeReport(List<SmcReportRow> rows) throws Exception {
        LocalDateTime now = LocalDateTime.now();
        String ts = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"));

        File dir = new File(config.getOutput().getDirectory());
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String filePath = dir.getAbsolutePath() + File.separator + "smc-report-" + ts + ".xlsx";

        try (XSSFWorkbook wb = new XSSFWorkbook(); FileOutputStream fos = new FileOutputStream(filePath)) {
            XSSFSheet sheet = wb.createSheet("SMC Status");

            String[] headers = {
                "Type", "Sector", "Symbol", "Current Price", "Buy Zone (Demand)", "Sell Zone (Supply)", "Action"
            };

            Row header = sheet.createRow(0);
            CellStyle headerStyle = headerStyle(wb);
            for (int i = 0; i < headers.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }

            CellStyle base = baseStyle(wb);
            CellStyle num = numberStyle(wb);
            CellStyle buyText = textFillStyle(wb, IndexedColors.LIGHT_GREEN);
            CellStyle sellText = textFillStyle(wb, IndexedColors.ROSE);
            CellStyle actionStyle = textFillStyle(wb, IndexedColors.LIGHT_YELLOW);

            int r = 1;
            for (SmcReportRow row : rows) {
                Row x = sheet.createRow(r++);
                putText(x, 0, row.getType(), base);
                putText(x, 1, row.getSector(), base);
                putText(x, 2, row.getSymbol(), base);
                putNum(x, 3, row.getCurrentPrice(), num);
                putText(x, 4, row.getBuyZone(), buyText);
                putText(x, 5, row.getSellZone(), sellText);
                putText(x, 6, row.getAction(), actionStyle);
            }

            int[] widths = {10, 16, 12, 13, 24, 24, 14};
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }
            sheet.createFreezePane(0, 1);

            wb.write(fos);
        }

        log.info("SMC report generated: {}", filePath);
        return filePath;
    }

    private CellStyle headerStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorders(s);

        Font f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(f);
        return s;
    }

    private CellStyle baseStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setWrapText(true);
        s.setVerticalAlignment(VerticalAlignment.TOP);
        setBorders(s);
        return s;
    }

    private CellStyle numberStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setAlignment(HorizontalAlignment.RIGHT);
        s.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
        setBorders(s);
        return s;
    }

    private CellStyle numberFillStyle(XSSFWorkbook wb, IndexedColors color) {
        CellStyle s = numberStyle(wb);
        s.setFillForegroundColor(color.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    private CellStyle textFillStyle(XSSFWorkbook wb, IndexedColors color) {
        CellStyle s = baseStyle(wb);
        s.setFillForegroundColor(color.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    private void setBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private void putText(Row row, int col, String val, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(val == null ? "" : val);
        cell.setCellStyle(style);
    }

    private void putNum(Row row, int col, double val, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(val);
        cell.setCellStyle(style);
    }
}
