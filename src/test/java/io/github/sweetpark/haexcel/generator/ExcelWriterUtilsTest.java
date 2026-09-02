package io.github.sweetpark.haexcel.generator;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sweetpark.haexcel.core.domain.ExcelColumnDef;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExcelWriterUtilsTest {

  @Test
  @DisplayName("calcMaxDepth returns 1 for flat columns and correct depth for nested columns")
  void testCalcMaxDepth() {
    List<ExcelColumnDef> flat =
        List.of(new ExcelColumnDef("id", "ID"), new ExcelColumnDef("name", "Name"));
    assertEquals(1, ExcelWriterUtils.calcMaxDepth(flat));

    List<ExcelColumnDef> nested =
        List.of(
            new ExcelColumnDef(
                "user",
                "User Info",
                100,
                List.of(
                    new ExcelColumnDef("firstName", "First"),
                    new ExcelColumnDef("lastName", "Last"))),
            new ExcelColumnDef("amount", "Amount"));
    assertEquals(2, ExcelWriterUtils.calcMaxDepth(nested));
  }

  @Test
  @DisplayName("flattenLeaves extracts leaf columns properly")
  void testFlattenLeaves() {
    List<ExcelColumnDef> nested =
        List.of(
            new ExcelColumnDef(
                "user",
                "User Info",
                100,
                List.of(
                    new ExcelColumnDef("firstName", "First"),
                    new ExcelColumnDef("lastName", "Last"))),
            new ExcelColumnDef("amount", "Amount"));
    List<ExcelColumnDef> leaves = ExcelWriterUtils.flattenLeaves(nested);
    assertEquals(3, leaves.size());
    assertEquals("firstName", leaves.get(0).field());
    assertEquals("lastName", leaves.get(1).field());
    assertEquals("amount", leaves.get(2).field());
  }

  @Test
  @DisplayName("applyExcelFormat formats various data types correctly")
  void testApplyExcelFormat() {
    assertEquals("1,234,567", ExcelWriterUtils.applyExcelFormat("krw", 1234567));
    assertEquals("2026/09/02", ExcelWriterUtils.applyExcelFormat("ymd", "20260902"));
    assertEquals("12:30:45", ExcelWriterUtils.applyExcelFormat("tm", "123045"));
    assertEquals(
        "2026/09/02 12:30:45", ExcelWriterUtils.applyExcelFormat("datetime", "20260902123045"));
    assertEquals("123-45-67890", ExcelWriterUtils.applyExcelFormat("bizno", "1234567890"));
    assertEquals("010-1234-5678", ExcelWriterUtils.applyExcelFormat("phone", "01012345678"));
    assertEquals("02-123-4567", ExcelWriterUtils.applyExcelFormat("tel", "021234567"));
    assertEquals("15%", ExcelWriterUtils.applyExcelFormat("pct", "15"));
    assertEquals("-", ExcelWriterUtils.applyExcelFormat("pct", "0"));
    assertEquals("raw", ExcelWriterUtils.applyExcelFormat("unknown", "raw"));
    assertEquals("", ExcelWriterUtils.applyExcelFormat("krw", null));
  }

  @Test
  @DisplayName("writeHeader and writeRows produce valid Excel workbook")
  void testWriteWorkbook() throws Exception {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      var sheet = wb.createSheet("Test");
      List<ExcelColumnDef> columns =
          List.of(
              new ExcelColumnDef("id", "ID", 80),
              new ExcelColumnDef("name", "Name", 120),
              new ExcelColumnDef(
                  "amount",
                  "Amount",
                  100,
                  null,
                  null,
                  "krw",
                  Map.of("textAlign", "right", "fontWeight", "bold", "color", "#0000FF"),
                  null),
              new ExcelColumnDef(
                  "status",
                  "Status",
                  100,
                  null,
                  null,
                  null,
                  null,
                  Map.of("01", "Active", "02", "Inactive")));

      int headerRows = ExcelWriterUtils.writeHeader(sheet, columns);
      assertEquals(1, headerRows);

      List<ExcelColumnDef> leaves = ExcelWriterUtils.flattenLeaves(columns);
      ExcelWriterUtils.applyColumnWidths(sheet, leaves);

      List<Map<String, Object>> rows =
          List.of(
              Map.of("id", 1, "name", "Alice", "amount", 50000, "status", "01"),
              Map.of("id", 2, "name", "Bob", "amount", 75000, "status", "02"));

      ExcelWriterUtils.writeRows(sheet, rows, leaves, headerRows, null);

      assertEquals(3, sheet.getPhysicalNumberOfRows());
      assertEquals("Alice", sheet.getRow(1).getCell(1).getStringCellValue());
      assertEquals("50,000", sheet.getRow(1).getCell(2).getStringCellValue());
      assertEquals("Active", sheet.getRow(1).getCell(3).getStringCellValue());

      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      wb.write(bos);
      assertTrue(bos.size() > 0);
    }
  }
}
