package io.github.sweetpark.haexcel.template;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jxls.transform.poi.JxlsPoiTemplateFillerBuilder;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTCell;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.STCellType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Jxls 3.x template engine implementation. Includes XML rich-text flattening workaround to prevent
 * XmlValueDisconnectedException.
 */
public class JxlsTemplateEngine implements TemplateExcelEngine {

  private static final Logger log = LoggerFactory.getLogger(JxlsTemplateEngine.class);

  @Override
  public void fill(InputStream templateInput, Map<String, Object> context, OutputStream output)
      throws IOException {
    byte[] templateBytes = templateInput.readAllBytes();
    byte[] processedBytes = flattenRichText(templateBytes);

    try {
      JxlsPoiTemplateFillerBuilder.newInstance()
          .withTemplate(new ByteArrayInputStream(processedBytes))
          .withCellStyleGeneralEnsurer()
          .buildAndFill(context, () -> output);
    } catch (Exception e) {
      throw new IOException("Failed to process Jxls template", e);
    }
  }

  private static byte[] flattenRichText(byte[] xlsxBytes) throws IOException {
    try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes));
        ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

      for (int si = 0; si < wb.getNumberOfSheets(); si++) {
        for (Row row : wb.getSheetAt(si)) {
          for (Cell cell : row) {
            if (cell.getCellType() == CellType.STRING) {
              String plain = cell.getStringCellValue();
              CTCell ctCell = ((XSSFCell) cell).getCTCell();
              ctCell.setT(STCellType.STR);
              ctCell.setV(plain);
              if (ctCell.isSetIs()) {
                ctCell.unsetIs();
              }
            }
          }
        }
      }
      wb.write(bos);
      return bos.toByteArray();
    }
  }
}
