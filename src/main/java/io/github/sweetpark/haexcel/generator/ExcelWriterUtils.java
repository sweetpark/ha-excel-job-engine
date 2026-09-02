package io.github.sweetpark.haexcel.generator;

import io.github.sweetpark.haexcel.core.domain.ExcelColumnDef;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;

/** Utility methods for generating Excel sheets with Apache POI SXSSF. */
public final class ExcelWriterUtils {

  private ExcelWriterUtils() {}

  public static int writeHeader(Sheet sheet, List<ExcelColumnDef> columns) {
    int maxDepth = calcMaxDepth(columns);
    CellStyle headerStyle = buildHeaderStyle(sheet.getWorkbook());

    Row[] headerRows = new Row[maxDepth];
    for (int i = 0; i < maxDepth; i++) {
      headerRows[i] = sheet.createRow(i);
    }

    writeHeaderDfs(sheet, headerRows, columns, 0, 0, maxDepth, headerStyle);
    return maxDepth;
  }

  private static int writeHeaderDfs(
      Sheet sheet,
      Row[] headerRows,
      List<ExcelColumnDef> columns,
      int depth,
      int colOffset,
      int maxDepth,
      CellStyle style) {
    for (ExcelColumnDef col : columns) {
      if (col.isLeaf()) {
        Cell cell = headerRows[depth].createCell(colOffset);
        cell.setCellValue(col.headerName());
        cell.setCellStyle(style);

        if (depth < maxDepth - 1) {
          sheet.addMergedRegion(new CellRangeAddress(depth, maxDepth - 1, colOffset, colOffset));
          for (int r = depth + 1; r < maxDepth; r++) {
            headerRows[r].createCell(colOffset).setCellStyle(style);
          }
        }
        colOffset++;
      } else {
        int startCol = colOffset;
        colOffset =
            writeHeaderDfs(
                sheet, headerRows, col.children(), depth + 1, colOffset, maxDepth, style);
        int endCol = colOffset - 1;

        Cell cell = headerRows[depth].createCell(startCol);
        cell.setCellValue(col.headerName());
        cell.setCellStyle(style);

        if (startCol < endCol) {
          sheet.addMergedRegion(new CellRangeAddress(depth, depth, startCol, endCol));
          for (int c = startCol + 1; c <= endCol; c++) {
            headerRows[depth].createCell(c).setCellStyle(style);
          }
        }
      }
    }
    return colOffset;
  }

  public static int calcMaxDepth(List<ExcelColumnDef> columns) {
    if (columns == null || columns.isEmpty()) {
      return 1;
    }
    int max = 1;
    for (ExcelColumnDef col : columns) {
      if (!col.isLeaf()) {
        max = Math.max(max, 1 + calcMaxDepth(col.children()));
      }
    }
    return max;
  }

  public static void applyColumnWidths(Sheet sheet, List<ExcelColumnDef> leafColumns) {
    for (int c = 0; c < leafColumns.size(); c++) {
      Integer widthPx = leafColumns.get(c).effectiveWidth();
      if (widthPx != null) {
        int poiWidth = Math.max(widthPx * 36, 2560);
        sheet.setColumnWidth(c, poiWidth);
      }
    }
  }

  public static List<ExcelColumnDef> flattenLeaves(List<ExcelColumnDef> columns) {
    List<ExcelColumnDef> leaves = new ArrayList<>();
    if (columns != null) {
      collectLeaves(columns, leaves);
    }
    return leaves;
  }

  private static void collectLeaves(List<ExcelColumnDef> columns, List<ExcelColumnDef> result) {
    for (ExcelColumnDef col : columns) {
      if (col.isLeaf()) {
        result.add(col);
      } else {
        collectLeaves(col.children(), result);
      }
    }
  }

  public static void writeRow(
      Sheet sheet,
      Map<String, Object> rowData,
      List<ExcelColumnDef> leafColumns,
      List<CellStyle> colStyles,
      int rowIdx) {
    Row row = sheet.createRow(rowIdx);
    for (int c = 0; c < leafColumns.size(); c++) {
      ExcelColumnDef col = leafColumns.get(c);
      Object val = (rowData != null) ? rowData.get(col.field()) : null;
      Cell cell = row.createCell(c);
      setCellValue(cell, col, val);

      if (colStyles != null && c < colStyles.size() && colStyles.get(c) != null) {
        cell.setCellStyle(colStyles.get(c));
      }
    }
  }

  public static void writeRows(
      Sheet sheet,
      List<Map<String, Object>> rows,
      List<ExcelColumnDef> leafColumns,
      int startRowIdx,
      IntConsumer onRowWritten) {
    List<CellStyle> colStyles = buildColumnStyles(sheet.getWorkbook(), leafColumns);
    for (int r = 0; r < rows.size(); r++) {
      writeRow(sheet, rows.get(r), leafColumns, colStyles, startRowIdx + r);
      if (onRowWritten != null) {
        onRowWritten.accept(r + 1);
      }
    }
  }

  public static List<CellStyle> buildColumnStyles(Workbook wb, List<ExcelColumnDef> leafColumns) {
    return leafColumns.stream()
        .map(
            col -> {
              Map<String, String> styleMap = col.cellStyle();
              if (styleMap == null || styleMap.isEmpty()) {
                return null;
              }
              return buildDataCellStyle(wb, styleMap);
            })
        .collect(Collectors.toList());
  }

  private static CellStyle buildDataCellStyle(Workbook wb, Map<String, String> styleMap) {
    CellStyle style = wb.createCellStyle();
    Font font = wb.createFont();
    boolean fontChanged = false;

    String textAlign = styleMap.get("textAlign");
    if (textAlign != null) {
      style.setAlignment(
          switch (textAlign.trim().toLowerCase()) {
            case "right" -> HorizontalAlignment.RIGHT;
            case "center" -> HorizontalAlignment.CENTER;
            default -> HorizontalAlignment.LEFT;
          });
    }

    if ("bold".equalsIgnoreCase(styleMap.get("fontWeight"))) {
      font.setBold(true);
      fontChanged = true;
    }

    if ("italic".equalsIgnoreCase(styleMap.get("fontStyle"))) {
      font.setItalic(true);
      fontChanged = true;
    }

    byte[] fontRgb = hexToRgb(styleMap.get("color"));
    if (fontRgb != null && font instanceof XSSFFont xssfFont) {
      xssfFont.setColor(new XSSFColor(fontRgb, null));
      fontChanged = true;
    }

    if (fontChanged) {
      style.setFont(font);
    }

    byte[] bgRgb = hexToRgb(styleMap.get("backgroundColor"));
    if (bgRgb != null && style instanceof XSSFCellStyle xssfStyle) {
      xssfStyle.setFillForegroundColor(new XSSFColor(bgRgb, null));
      xssfStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    }

    return style;
  }

  public static byte[] hexToRgb(String hex) {
    if (hex == null || !hex.matches("^#[0-9A-Fa-f]{6}$")) {
      return null;
    }
    String h = hex.substring(1);
    return new byte[] {
      (byte) Integer.parseInt(h.substring(0, 2), 16),
      (byte) Integer.parseInt(h.substring(2, 4), 16),
      (byte) Integer.parseInt(h.substring(4, 6), 16)
    };
  }

  public static void setCellValue(Cell cell, ExcelColumnDef col, Object val) {
    if (col.excelCodeMap() != null && !col.excelCodeMap().isEmpty()) {
      String raw = val != null ? Objects.toString(val, "").trim() : "";
      String mapped = col.excelCodeMap().get(raw);
      cell.setCellValue(mapped != null ? mapped : raw);
      return;
    }

    String fmt = col.excelFormat();
    if (fmt != null && !fmt.isBlank()) {
      cell.setCellValue(applyExcelFormat(fmt, val));
      return;
    }

    if (val instanceof Number n) {
      cell.setCellValue(n.doubleValue());
    } else if (val instanceof Boolean b) {
      cell.setCellValue(b);
    } else if (val != null) {
      cell.setCellValue(String.valueOf(val));
    }
  }

  public static String applyExcelFormat(String format, Object value) {
    if (value == null) {
      return "";
    }
    String str = Objects.toString(value, "").trim();

    return switch (format) {
      case "krw" -> {
        try {
          long num = Double.valueOf(str).longValue();
          yield String.format("%,d", num);
        } catch (NumberFormatException e) {
          yield str;
        }
      }
      case "ymd" -> {
        String digits = str.replaceAll("\\D", "");
        yield digits.length() >= 8
            ? digits.substring(0, 4) + "/" + digits.substring(4, 6) + "/" + digits.substring(6, 8)
            : str;
      }
      case "tm" -> {
        String digits = str.replaceAll("\\D", "");
        yield digits.length() == 6
            ? digits.substring(0, 2) + ":" + digits.substring(2, 4) + ":" + digits.substring(4)
            : str;
      }
      case "datetime" -> {
        String digits = str.replaceAll("\\D", "");
        yield digits.length() >= 14
            ? digits.substring(0, 4)
                + "/"
                + digits.substring(4, 6)
                + "/"
                + digits.substring(6, 8)
                + " "
                + digits.substring(8, 10)
                + ":"
                + digits.substring(10, 12)
                + ":"
                + digits.substring(12, 14)
            : str;
      }
      case "bizno" -> {
        String digits = str.replaceAll("\\D", "");
        yield digits.length() == 10
            ? digits.substring(0, 3) + "-" + digits.substring(3, 5) + "-" + digits.substring(5)
            : str;
      }
      case "phone" -> {
        String digits = str.replaceAll("\\D", "");
        yield digits.length() == 11
            ? digits.substring(0, 3) + "-" + digits.substring(3, 7) + "-" + digits.substring(7)
            : digits.length() == 10
                ? digits.substring(0, 3) + "-" + digits.substring(3, 6) + "-" + digits.substring(6)
                : str;
      }
      case "tel" -> {
        String digits = str.replaceAll("\\D", "");
        if (digits.startsWith("02")) {
          yield digits.length() == 9
              ? digits.substring(0, 2) + "-" + digits.substring(2, 5) + "-" + digits.substring(5)
              : digits.length() == 10
                  ? digits.substring(0, 2)
                      + "-"
                      + digits.substring(2, 6)
                      + "-"
                      + digits.substring(6)
                  : str;
        }
        yield digits.length() == 11
            ? digits.substring(0, 3) + "-" + digits.substring(3, 7) + "-" + digits.substring(7)
            : digits.length() == 10
                ? digits.substring(0, 3) + "-" + digits.substring(3, 6) + "-" + digits.substring(6)
                : str;
      }
      case "pct" -> {
        try {
          long num = Double.valueOf(str).longValue();
          yield num == 0 ? "-" : num + "%";
        } catch (NumberFormatException e) {
          yield str;
        }
      }
      default -> str;
    };
  }

  private static CellStyle buildHeaderStyle(Workbook wb) {
    Font font = wb.createFont();
    font.setBold(true);

    CellStyle style = wb.createCellStyle();
    style.setFont(font);
    style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
    style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    style.setAlignment(HorizontalAlignment.CENTER);
    style.setVerticalAlignment(VerticalAlignment.CENTER);
    style.setBorderTop(BorderStyle.THIN);
    style.setBorderBottom(BorderStyle.THIN);
    style.setBorderLeft(BorderStyle.THIN);
    style.setBorderRight(BorderStyle.THIN);
    return style;
  }
}
