package io.github.sweetpark.haexcel.generator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.github.sweetpark.haexcel.autoconfigure.ExcelProperties;
import io.github.sweetpark.haexcel.core.ExcelDataProvider;
import io.github.sweetpark.haexcel.core.ExcelDataRegistry;
import io.github.sweetpark.haexcel.core.ExcelJobManager;
import io.github.sweetpark.haexcel.core.domain.ExcelJob;
import io.github.sweetpark.haexcel.storage.StorageService;
import io.github.sweetpark.haexcel.template.ExcelTemplateService;
import io.github.sweetpark.haexcel.template.TemplateExcelEngine;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TemplateExcelGeneratorServiceTest {

  @TempDir Path tempDir;

  @Test
  @DisplayName("TemplateExcelGeneratorService loads template and fills data via engine")
  void testTemplateGeneration() throws Exception {
    ExcelProperties props = new ExcelProperties();
    props.setTempDir(tempDir.resolve("tpl-jobs").toString());

    ExcelJobManager manager = mock(ExcelJobManager.class);
    StorageService storage = mock(StorageService.class);
    when(storage.storeFile(any(), any(), any())).thenAnswer(inv -> (String) inv.getArgument(1));

    ExcelTemplateService templateService = mock(ExcelTemplateService.class);

    // Generate dummy xlsx template bytes
    byte[] dummyXlsx;
    try (XSSFWorkbook wb = new XSSFWorkbook();
        ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
      var sheet = wb.createSheet("Template");
      var row = sheet.createRow(0);
      row.createCell(0).setCellValue("Template Title");
      wb.write(bos);
      dummyXlsx = bos.toByteArray();
    }
    when(templateService.getTemplate("tpl-sample")).thenReturn(dummyXlsx);

    TemplateExcelEngine engine =
        (in, ctx, out) -> {
          out.write(in.readAllBytes());
        };

    ExcelDataRegistry registry = new ExcelDataRegistry();
    registry.register(
        new ExcelDataProvider() {
          @Override
          public String getName() {
            return "tplBiz";
          }

          @Override
          public List<Map<String, Object>> fetchData(Map<String, Object> params) {
            return List.of(Map.of("name", "sample"));
          }
        });

    TemplateExcelGeneratorService service =
        new TemplateExcelGeneratorService(
            props, manager, storage, templateService, engine, registry);

    ExcelJob job =
        ExcelJob.builder()
            .jobId("tpl-job-1")
            .bizNm("tplBiz")
            .templateId("tpl-sample")
            .totalRows(1)
            .build();

    service.generate(job, "tplBiz", "tpl-sample", Map.of(), List.of());

    verify(manager).updateProgress("tpl-job-1", 1, 1);
    verify(manager).complete("tpl-job-1", "tpl-job-1.xlsx");
  }
}
