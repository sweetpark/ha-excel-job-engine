package io.github.sweetpark.haexcel.generator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.github.sweetpark.haexcel.autoconfigure.ExcelProperties;
import io.github.sweetpark.haexcel.core.ExcelDataProvider;
import io.github.sweetpark.haexcel.core.ExcelDataRegistry;
import io.github.sweetpark.haexcel.core.ExcelJobManager;
import io.github.sweetpark.haexcel.core.domain.ExcelColumnDef;
import io.github.sweetpark.haexcel.core.domain.ExcelJob;
import io.github.sweetpark.haexcel.storage.StorageService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExcelZipGeneratorServiceTest {

  @TempDir Path tempDir;

  @Test
  @DisplayName("ExcelZipGeneratorService splits data into chunks and creates a zip file")
  void testGenerateZip() throws Exception {
    ExcelProperties props = new ExcelProperties();
    props.setTempDir(tempDir.resolve("jobs").toString());
    props.setChunkSize(10); // Small chunk size for test

    ExcelJobManager manager = mock(ExcelJobManager.class);
    StorageService storage = mock(StorageService.class);
    when(storage.storeFile(any(), any(), any())).thenAnswer(inv -> (String) inv.getArgument(1));

    ExcelDataRegistry registry = new ExcelDataRegistry();
    registry.register(
        new ExcelDataProvider() {
          @Override
          public String getName() {
            return "largeData";
          }

          @Override
          public List<Map<String, Object>> fetchData(Map<String, Object> params) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int i = 0; i < 25; i++) {
              rows.add(Map.of("id", i, "name", "user" + i));
            }
            return rows;
          }
        });

    ExcelZipGeneratorService zipService =
        new ExcelZipGeneratorService(props, manager, storage, registry);

    ExcelJob job =
        ExcelJob.builder()
            .jobId("zip-job-1")
            .bizNm("largeData")
            .fileName("large_export")
            .totalRows(25)
            .build();

    List<ExcelColumnDef> columns =
        List.of(new ExcelColumnDef("id", "ID", 80), new ExcelColumnDef("name", "Name", 120));

    zipService.generate(job, "largeData", Map.of(), columns);

    verify(manager, atLeastOnce()).updateProgress(eq("zip-job-1"), anyInt(), eq(25));
    verify(manager).complete(eq("zip-job-1"), eq("zip-job-1.zip"));
  }
}
