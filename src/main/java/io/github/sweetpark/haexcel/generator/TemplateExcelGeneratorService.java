package io.github.sweetpark.haexcel.generator;

import io.github.sweetpark.haexcel.autoconfigure.ExcelProperties;
import io.github.sweetpark.haexcel.core.ExcelDataProvider;
import io.github.sweetpark.haexcel.core.ExcelDataRegistry;
import io.github.sweetpark.haexcel.core.ExcelJobManager;
import io.github.sweetpark.haexcel.core.domain.ExcelColumnDef;
import io.github.sweetpark.haexcel.core.domain.ExcelJob;
import io.github.sweetpark.haexcel.storage.StorageService;
import io.github.sweetpark.haexcel.template.ExcelTemplateService;
import io.github.sweetpark.haexcel.template.TemplateExcelEngine;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Generator using Jxls template files to produce styled reports with cell merging. */
public class TemplateExcelGeneratorService {

  private static final Logger log = LoggerFactory.getLogger(TemplateExcelGeneratorService.class);
  private static final String XLSX_CONTENT_TYPE =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

  private final ExcelProperties props;
  private final ExcelJobManager jobManager;
  private final StorageService storageService;
  private final ExcelTemplateService templateService;
  private final TemplateExcelEngine engine;
  private final ExcelDataRegistry dataRegistry;

  public TemplateExcelGeneratorService(
      ExcelProperties props,
      ExcelJobManager jobManager,
      StorageService storageService,
      ExcelTemplateService templateService,
      TemplateExcelEngine engine,
      ExcelDataRegistry dataRegistry) {
    this.props = props;
    this.jobManager = jobManager;
    this.storageService = storageService;
    this.templateService = templateService;
    this.engine = engine;
    this.dataRegistry = dataRegistry;
  }

  public void generate(
      ExcelJob job,
      String bizNm,
      String templateId,
      Map<String, Object> params,
      List<ExcelColumnDef> columns) {
    log.info(
        "[TemplateExcelGenerator] Generating template Excel: jobId={} bizNm={} templateId={}",
        job.getJobId(),
        bizNm,
        templateId);

    try {
      Map<String, Object> context = buildContext(bizNm, params);
      Object dataList = context.get("dataList");
      int rowCount = (dataList instanceof List<?> l) ? l.size() : 1;

      if (jobManager.isCancelRequested(job.getJobId())) {
        jobManager.fail(job.getJobId(), "Cancelled by user");
        return;
      }

      byte[] templateBytes = templateService.getTemplate(templateId);

      Path tempDir = Path.of(props.getTempDir());
      Files.createDirectories(tempDir);
      Path tmpPath = tempDir.resolve(job.getJobId() + ".xlsx");

      try (InputStream in = new ByteArrayInputStream(templateBytes);
          OutputStream out = new FileOutputStream(tmpPath.toFile())) {
        engine.fill(in, context, out);
      }

      if (jobManager.isCancelRequested(job.getJobId())) {
        Files.deleteIfExists(tmpPath);
        jobManager.fail(job.getJobId(), "Cancelled by user");
        return;
      }

      jobManager.updateProgress(job.getJobId(), rowCount, rowCount);

      String storageKey =
          storageService.storeFile(tmpPath, job.getJobId() + ".xlsx", XLSX_CONTENT_TYPE);
      jobManager.complete(job.getJobId(), storageKey);
      log.info(
          "[TemplateExcelGenerator] Completed template export: jobId={} rows={}",
          job.getJobId(),
          rowCount);

    } catch (OutOfMemoryError oom) {
      log.error("[TemplateExcelGenerator] OOM in template export: jobId={}", job.getJobId(), oom);
      jobManager.fail(job.getJobId(), "Out of memory during template generation");
    } catch (Exception e) {
      log.error("[TemplateExcelGenerator] Template export failed: jobId={}", job.getJobId(), e);
      jobManager.fail(job.getJobId(), e.getMessage());
    }
  }

  private Map<String, Object> buildContext(String bizNm, Map<String, Object> params) {
    Map<String, Object> context = new HashMap<>();
    Map<String, Object> safeParams = (params != null) ? params : Map.of();

    List<Map<String, Object>> rows = Collections.emptyList();
    if (dataRegistry != null) {
      Object provider = dataRegistry.resolve(bizNm);
      if (provider instanceof ExcelDataProvider dp) {
        rows = dp.fetchData(safeParams);
      }
    }

    context.put("dataList", rows);
    if (!rows.isEmpty()) {
      context.put("data", rows.get(0));
    }

    context.put("params", safeParams);
    context.put("date", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd")));
    return context;
  }
}
