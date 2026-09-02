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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Service for chunking large datasets (> 100k rows) into multiple .xlsx files compressed in a
 * single .zip.
 */
public class ExcelZipGeneratorService {

  private static final Logger log = LoggerFactory.getLogger(ExcelZipGeneratorService.class);
  private static final String ZIP_CONTENT_TYPE = "application/zip";

  private final ExcelProperties props;
  private final ExcelJobManager jobManager;
  private final StorageService storageService;
  private final ExcelDataRegistry dataRegistry;

  @Autowired(required = false)
  private SqlSessionFactory sqlSessionFactory;

  public ExcelZipGeneratorService(
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
    Path tempDir = Path.of(props.getTempDir());
    List<Path> chunkFiles = new ArrayList<>();

    try {
      Files.createDirectories(tempDir);

      Object provider = (dataRegistry != null) ? dataRegistry.resolve(bizNm) : null;

      if (provider instanceof ExcelStreamable streamable && sqlSessionFactory != null) {
        log.info(
            "[ExcelZipGeneratorService] Using Cursor streaming ZIP path: jobId={} bizNm={}",
            job.getJobId(),
            bizNm);
        chunkFiles = writeChunksStreaming(job, streamable, params, columns, tempDir);
      } else {
        log.info(
            "[ExcelZipGeneratorService] Using In-Memory fallback ZIP path: jobId={} bizNm={}",
            job.getJobId(),
            bizNm);
        chunkFiles = writeChunksInMemory(job, provider, bizNm, params, columns, tempDir);
      }

      Path zipPath = buildZip(job, chunkFiles, tempDir);
      String storageKey =
          storageService.storeFile(zipPath, job.getJobId() + ".zip", ZIP_CONTENT_TYPE);
      jobManager.complete(job.getJobId(), storageKey);
      log.info(
          "[ExcelZipGeneratorService] Successfully generated ZIP: jobId={} file={}",
          job.getJobId(),
          storageKey);

    } catch (ExcelJobCancelledException cancel) {
      log.info("[ExcelZipGeneratorService] Job cancelled: jobId={}", job.getJobId());
      jobManager.fail(job.getJobId(), "Cancelled by user");
    } catch (OutOfMemoryError oom) {
      log.error(
          "[ExcelZipGeneratorService] OOM while generating ZIP: jobId={}", job.getJobId(), oom);
      jobManager.fail(job.getJobId(), "Out of memory during ZIP generation");
    } catch (Exception e) {
      log.error("[ExcelZipGeneratorService] Error generating ZIP: jobId={}", job.getJobId(), e);
      jobManager.fail(job.getJobId(), e.getMessage());
    } finally {
      // Clean up temporary chunk files
      for (Path chunkFile : chunkFiles) {
        try {
          Files.deleteIfExists(chunkFile);
        } catch (IOException e) {
          log.debug("[ExcelZipGeneratorService] Could not delete chunk file: {}", chunkFile, e);
        }
      }
    }
  }

  private List<Path> writeChunksStreaming(
      ExcelJob job,
      ExcelStreamable streamable,
      Map<String, Object> params,
      List<ExcelColumnDef> columns,
      Path tempDir)
      throws Exception {

    List<Path> chunkFiles = new ArrayList<>();
    List<ExcelColumnDef> leafColumns = ExcelWriterUtils.flattenLeaves(columns);
    int chunkSize = Math.max(1_000, props.getChunkSize());

    Map<String, Object> queryParams = (params != null) ? new HashMap<>(params) : new HashMap<>();
    queryParams.put("stRow", 0);
    queryParams.put("iRows", Integer.MAX_VALUE);

    int chunkIndex = 0;
    int totalProcessed = 0;

    try (SqlSession session = sqlSessionFactory.openSession(false);
        Cursor<Map<String, Object>> cursor = streamable.streamRows(queryParams, session)) {

      List<Map<String, Object>> buffer = new ArrayList<>(chunkSize);

      for (Map<String, Object> row : cursor) {
        buffer.add(row);
        totalProcessed++;

        if (buffer.size() >= chunkSize) {
          Path chunkPath =
              writeSingleChunk(job, buffer, leafColumns, columns, chunkIndex++, tempDir);
          chunkFiles.add(chunkPath);
          buffer.clear();
          jobManager.updateProgress(job.getJobId(), totalProcessed, 0);

          if (jobManager.isCancelRequested(job.getJobId())) {
            throw new ExcelJobCancelledException("Job cancelled by user");
          }
        }
      }

      if (!buffer.isEmpty()) {
        Path chunkPath = writeSingleChunk(job, buffer, leafColumns, columns, chunkIndex++, tempDir);
        chunkFiles.add(chunkPath);
        buffer.clear();
      }

      jobManager.updateProgress(job.getJobId(), totalProcessed, totalProcessed);
    }

    return chunkFiles;
  }

  private List<Path> writeChunksInMemory(
      ExcelJob job,
      Object provider,
      String bizNm,
      Map<String, Object> params,
      List<ExcelColumnDef> columns,
      Path tempDir)
      throws Exception {

    List<Map<String, Object>> rows = fetchRows(provider, bizNm, params);
    List<ExcelColumnDef> leafColumns = ExcelWriterUtils.flattenLeaves(columns);
    int chunkSize = Math.max(1_000, props.getChunkSize());
    int total = rows.size();
    List<Path> chunkFiles = new ArrayList<>();

    jobManager.updateProgress(job.getJobId(), 0, total);

    int chunkIndex = 0;
    for (int i = 0; i < total; i += chunkSize) {
      int toIndex = Math.min(i + chunkSize, total);
      List<Map<String, Object>> subList = rows.subList(i, toIndex);

      Path chunkPath = writeSingleChunk(job, subList, leafColumns, columns, chunkIndex++, tempDir);
      chunkFiles.add(chunkPath);

      jobManager.updateProgress(job.getJobId(), toIndex, total);

      if (jobManager.isCancelRequested(job.getJobId())) {
        throw new ExcelJobCancelledException("Job cancelled by user");
      }
    }

    return chunkFiles;
  }

  private Path writeSingleChunk(
      ExcelJob job,
      List<Map<String, Object>> rows,
      List<ExcelColumnDef> leafColumns,
      List<ExcelColumnDef> allColumns,
      int chunkIndex,
      Path tempDir)
      throws Exception {

    Path chunkPath = tempDir.resolve(job.getJobId() + "_chunk" + chunkIndex + ".xlsx");
    try (SXSSFWorkbook wb = new SXSSFWorkbook(1000);
        FileOutputStream fos = new FileOutputStream(chunkPath.toFile())) {

      var sheet = wb.createSheet("Sheet1");
      int headerRows = ExcelWriterUtils.writeHeader(sheet, allColumns);
      ExcelWriterUtils.applyColumnWidths(sheet, leafColumns);
      ExcelWriterUtils.writeRows(sheet, rows, leafColumns, headerRows, null);
      wb.write(fos);
    }
    return chunkPath;
  }

  private Path buildZip(ExcelJob job, List<Path> chunkFiles, Path tempDir) throws IOException {
    Path zipPath = tempDir.resolve(job.getJobId() + ".zip");
    try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
      for (int i = 0; i < chunkFiles.size(); i++) {
        Path chunkFile = chunkFiles.get(i);
        String entryName =
            (job.getFileName() != null ? job.getFileName() : "export")
                + "_part"
                + (i + 1)
                + ".xlsx";
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        Files.copy(chunkFile, zos);
        zos.closeEntry();
      }
    }
    return zipPath;
  }

  private List<Map<String, Object>> fetchRows(
      Object provider, String bizNm, Map<String, Object> params) {
    if (provider instanceof ExcelDataProvider dataProvider) {
      return dataProvider.fetchData(params);
    }
    return Collections.emptyList();
  }
}
