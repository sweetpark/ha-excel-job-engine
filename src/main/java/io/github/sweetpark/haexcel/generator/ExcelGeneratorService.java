package io.github.sweetpark.haexcel.generator;

import io.github.sweetpark.haexcel.autoconfigure.ExcelProperties;
import io.github.sweetpark.haexcel.core.ExcelDataProvider;
import io.github.sweetpark.haexcel.core.ExcelDataRegistry;
import io.github.sweetpark.haexcel.core.ExcelJobManager;
import io.github.sweetpark.haexcel.core.ExcelStreamable;
import io.github.sweetpark.haexcel.core.domain.ExcelColumnDef;
import io.github.sweetpark.haexcel.core.domain.ExcelJob;
import io.github.sweetpark.haexcel.storage.StorageService;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Single .xlsx file generation service. Supports memory-efficient Cursor streaming via
 * SXSSFWorkbook(100) or list-based fallback.
 */
public class ExcelGeneratorService {

  private static final Logger log = LoggerFactory.getLogger(ExcelGeneratorService.class);
  private static final String XLSX_CONTENT_TYPE =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
  private static final int STREAM_REPORT_INTERVAL = 1_000;

  private final ExcelProperties props;
  private final ExcelJobManager jobManager;
  private final StorageService storageService;
  private final ExcelDataRegistry dataRegistry;

  @Autowired(required = false)
  private SqlSessionFactory sqlSessionFactory;

  public ExcelGeneratorService(
      ExcelProperties props,
      ExcelJobManager jobManager,
      StorageService storageService,
      ExcelDataRegistry dataRegistry) {
    this.props = props;
    this.jobManager = jobManager;
    this.storageService = storageService;
    this.dataRegistry = dataRegistry;
  }

  public void generate(
      ExcelJob job, String bizNm, Map<String, Object> params, List<ExcelColumnDef> columns) {
    try {
      Path tempDir = Path.of(props.getTempDir());
      Files.createDirectories(tempDir);

      Object provider = (dataRegistry != null) ? dataRegistry.resolve(bizNm) : null;

      if (provider instanceof ExcelStreamable streamable && sqlSessionFactory != null) {
        log.info(
            "[ExcelGeneratorService] Using Cursor streaming path: jobId={} bizNm={}",
            job.getJobId(),
            bizNm);
        doGenerateStreaming(job, streamable, params, columns, tempDir);
      } else {
        log.info(
            "[ExcelGeneratorService] Using In-Memory fallback path: jobId={} bizNm={}",
            job.getJobId(),
            bizNm);
        doGenerateInMemory(job, provider, bizNm, params, columns, tempDir);
      }
    } catch (OutOfMemoryError oom) {
      log.error(
          "[ExcelGeneratorService] OOM while generating Excel: jobId={}", job.getJobId(), oom);
      jobManager.fail(job.getJobId(), "Out of memory during Excel generation");
    } catch (Exception e) {
      log.error("[ExcelGeneratorService] Error generating Excel: jobId={}", job.getJobId(), e);
      jobManager.fail(job.getJobId(), e.getMessage());
    }
  }

  private void doGenerateStreaming(
      ExcelJob job,
      ExcelStreamable streamable,
      Map<String, Object> params,
      List<ExcelColumnDef> columns,
      Path tempDir)
      throws Exception {

    Map<String, Object> queryParams = (params != null) ? new HashMap<>(params) : new HashMap<>();
    queryParams.put("stRow", 0);
    queryParams.put("iRows", Integer.MAX_VALUE);

    List<ExcelColumnDef> leafColumns = ExcelWriterUtils.flattenLeaves(columns);
    Path tmpPath = tempDir.resolve(job.getJobId() + ".xlsx");

    int processed = 0;
    boolean cancelled = false;

    try (SqlSession session = sqlSessionFactory.openSession(false);
        Cursor<Map<String, Object>> cursor = streamable.streamRows(queryParams, session);
        SXSSFWorkbook wb = new SXSSFWorkbook(100);
        FileOutputStream fos = new FileOutputStream(tmpPath.toFile())) {

      var sheet = wb.createSheet("Sheet1");
      int headerRows = ExcelWriterUtils.writeHeader(sheet, columns);
      ExcelWriterUtils.applyColumnWidths(sheet, leafColumns);
      var colStyles = ExcelWriterUtils.buildColumnStyles(wb, leafColumns);

      jobManager.updateProgress(job.getJobId(), 0, 0);

      for (Map<String, Object> rowData : cursor) {
        ExcelWriterUtils.writeRow(sheet, rowData, leafColumns, colStyles, headerRows + processed);
        processed++;

        if (processed % STREAM_REPORT_INTERVAL == 0) {
          jobManager.updateProgress(job.getJobId(), processed, 0);
          if (jobManager.isCancelRequested(job.getJobId())) {
            log.info("[ExcelGeneratorService] Job cancel detected: jobId={}", job.getJobId());
            cancelled = true;
            break;
          }
        }
      }

      if (!cancelled) {
        jobManager.updateProgress(job.getJobId(), processed, processed);
        wb.write(fos);
      }
    }

    if (cancelled) {
      Files.deleteIfExists(tmpPath);
      jobManager.fail(job.getJobId(), "Cancelled by user");
      return;
    }

    String storageKey =
        storageService.storeFile(tmpPath, job.getJobId() + ".xlsx", XLSX_CONTENT_TYPE);
    jobManager.complete(job.getJobId(), storageKey);
    log.info(
        "[ExcelGeneratorService] Streaming export completed: jobId={} rows={}",
        job.getJobId(),
        processed);
  }

  private void doGenerateInMemory(
      ExcelJob job,
      Object provider,
      String bizNm,
      Map<String, Object> params,
      List<ExcelColumnDef> columns,
      Path tempDir)
      throws Exception {

    List<Map<String, Object>> rows = fetchRows(provider, bizNm, params);
    List<ExcelColumnDef> leafColumns = ExcelWriterUtils.flattenLeaves(columns);
    final int total = rows.size();
    jobManager.updateProgress(job.getJobId(), 0, total);

    final int reportInterval = Math.max(1, total / 20);
    Path tmpPath = tempDir.resolve(job.getJobId() + ".xlsx");

    try (SXSSFWorkbook wb = new SXSSFWorkbook(1000);
        FileOutputStream fos = new FileOutputStream(tmpPath.toFile())) {

      var sheet = wb.createSheet("Sheet1");
      int headerRows = ExcelWriterUtils.writeHeader(sheet, columns);
      ExcelWriterUtils.applyColumnWidths(sheet, leafColumns);
      ExcelWriterUtils.writeRows(
          sheet,
          rows,
          leafColumns,
          headerRows,
          written -> {
            if (written % reportInterval == 0 || written == total) {
              jobManager.updateProgress(job.getJobId(), written, total);
            }
          });
      wb.write(fos);
    }

    String storageKey =
        storageService.storeFile(tmpPath, job.getJobId() + ".xlsx", XLSX_CONTENT_TYPE);
    jobManager.complete(job.getJobId(), storageKey);
    log.info(
        "[ExcelGeneratorService] In-memory export completed: jobId={} rows={}",
        job.getJobId(),
        total);
  }

  private List<Map<String, Object>> fetchRows(
      Object provider, String bizNm, Map<String, Object> params) {
    if (provider instanceof ExcelDataProvider dataProvider) {
      return dataProvider.fetchData(params);
    }
    log.debug(
        "[ExcelGeneratorService] No data provider found for bizNm={}, using empty dataset", bizNm);
    return Collections.emptyList();
  }
}
