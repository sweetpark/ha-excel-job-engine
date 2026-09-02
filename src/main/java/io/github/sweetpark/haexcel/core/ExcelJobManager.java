package io.github.sweetpark.haexcel.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sweetpark.haexcel.autoconfigure.ExcelProperties;
import io.github.sweetpark.haexcel.core.domain.ExcelColumnDef;
import io.github.sweetpark.haexcel.core.domain.ExcelJob;
import io.github.sweetpark.haexcel.core.domain.ExcelJobStatus;
import io.github.sweetpark.haexcel.storage.StorageService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;

/** Lifecycle manager for distributed Excel jobs. */
public class ExcelJobManager {

  private static final Logger log = LoggerFactory.getLogger(ExcelJobManager.class);
  private static final int MAX_ERROR_MSG_LENGTH = 1_000;

  private final ExcelProperties props;
  private final ExcelJobMapper jobMapper;
  private final StorageService storageService;
  private final ObjectMapper objectMapper;
  private final ExcelJobQueue normalJobQueue;
  private final ExcelJobQueue largeJobQueue;

  public ExcelJobManager(
      ExcelProperties props,
      ExcelJobMapper jobMapper,
      StorageService storageService,
      ObjectMapper objectMapper,
      ExcelJobQueue normalJobQueue,
      ExcelJobQueue largeJobQueue) {
    this.props = props;
    this.jobMapper = jobMapper;
    this.storageService = storageService;
    this.objectMapper = objectMapper;
    this.normalJobQueue = normalJobQueue;
    this.largeJobQueue = largeJobQueue;
  }

  public ExcelJob createJob(
      String bizNm,
      String fileName,
      String worker,
      Map<String, Object> params,
      List<ExcelColumnDef> columns,
      String templateId,
      int totalCnt) {
    return createJobInternal(bizNm, fileName, worker, params, columns, templateId, totalCnt);
  }

  public ExcelJob createJob(
      String bizNm,
      String fileName,
      String worker,
      Map<String, Object> params,
      List<ExcelColumnDef> columns,
      String templateId) {
    return createJobInternal(bizNm, fileName, worker, params, columns, templateId, 0);
  }

  private ExcelJob createJobInternal(
      String bizNm,
      String fileName,
      String worker,
      Map<String, Object> params,
      List<ExcelColumnDef> columns,
      String templateId,
      int totalCnt) {

    if (bizNm == null || bizNm.isBlank()) {
      throw new IllegalArgumentException("bizNm is required");
    }
    if (worker == null || worker.isBlank()) {
      throw new IllegalArgumentException("worker is required");
    }

    boolean isTemplateMode = templateId != null && !templateId.isBlank();
    if (!isTemplateMode && (columns == null || columns.isEmpty())) {
      throw new IllegalArgumentException("columns must contain at least one column definition");
    }

    String paramsJson;
    try {
      Map<String, Object> sortedParams = params != null ? new TreeMap<>(params) : new TreeMap<>();
      paramsJson = objectMapper.writeValueAsString(sortedParams);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to serialize paramsJson for bizNm: " + bizNm, e);
    }

    long cutoffMs = System.currentTimeMillis() - props.getIdempotencyWindowMinutes() * 60_000L;

    String columnsJsonForCheck;
    try {
      columnsJsonForCheck = objectMapper.writeValueAsString(columns != null ? columns : List.of());
    } catch (Exception e) {
      throw new IllegalStateException("Failed to serialize columnsJson for bizNm: " + bizNm, e);
    }

    String existingJobId =
        jobMapper.selectActiveJobId(
            worker, bizNm, paramsJson, columnsJsonForCheck, templateId, cutoffMs);

    if (existingJobId != null) {
      log.info(
          "[ExcelJobManager] Idempotent hit, reusing active job: jobId={} worker={} bizNm={}",
          existingJobId,
          worker,
          bizNm);
      return jobMapper.selectByJobId(existingJobId);
    }

    try {
      String columnsJson = objectMapper.writeValueAsString(columns != null ? columns : List.of());

      ExcelJob job =
          ExcelJob.builder()
              .jobId(UUID.randomUUID().toString())
              .bizNm(bizNm)
              .fileName(fileName)
              .worker(worker)
              .templateId(templateId)
              .status(ExcelJobStatus.PENDING)
              .paramsJson(paramsJson)
              .columnsJson(columnsJson)
              .processedRows(0)
              .totalRows(totalCnt)
              .createdAt(System.currentTimeMillis())
              .build();

      jobMapper.insert(job);

      // Enqueue immediately for push processing
      resolveQueue(totalCnt).offer(job);

      log.info(
          "[ExcelJobManager] Job created: jobId={} bizNm={} queue={}",
          job.getJobId(),
          bizNm,
          isLargeJob(totalCnt) ? "large" : "normal");

      return job;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to create job for bizNm: " + bizNm, e);
    }
  }

  public ExcelJobQueue resolveQueue(int totalCnt) {
    return isLargeJob(totalCnt) ? largeJobQueue : normalJobQueue;
  }

  public boolean isLargeJob(int totalCnt) {
    int zipThreshold = props.getZipThreshold();
    return zipThreshold > 0 && totalCnt > zipThreshold;
  }

  public Optional<ExcelJob> findJob(String jobId) {
    return Optional.ofNullable(jobMapper.selectByJobId(jobId));
  }

  public int countPendingJobsBefore(long createdAt) {
    return jobMapper.countPendingJobsBefore(createdAt);
  }

  public Optional<Resource> loadFile(ExcelJob job) {
    if (job.getFilePath() == null) {
      return Optional.empty();
    }
    return storageService.getResource(job.getFilePath());
  }

  public void updateProgress(String jobId, int processedRows, int totalRows) {
    jobMapper.updateProgress(jobId, processedRows, totalRows);
  }

  public void complete(String jobId, String filePath) {
    jobMapper.updateDone(jobId, filePath, System.currentTimeMillis());
    log.info("[ExcelJobManager] Completed job: jobId={} filePath={}", jobId, filePath);
  }

  public void fail(String jobId, String errorMsg) {
    String safeMsg = errorMsg != null ? errorMsg : "Unknown error";
    if (safeMsg.length() > MAX_ERROR_MSG_LENGTH) {
      log.warn("[ExcelJobManager] Truncating long errorMsg: jobId={}", jobId);
      safeMsg = safeMsg.substring(0, MAX_ERROR_MSG_LENGTH) + "...(truncated)";
    }
    jobMapper.updateFail(jobId, safeMsg, System.currentTimeMillis());
    log.warn("[ExcelJobManager] Job failed: jobId={} error={}", jobId, errorMsg);
  }

  public void requestCancel(String jobId, String requestUserId) {
    ExcelJob job =
        Optional.ofNullable(jobMapper.selectByJobId(jobId))
            .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

    if (requestUserId != null
        && !requestUserId.isBlank()
        && !requestUserId.equals(job.getWorker())) {
      throw new IllegalArgumentException("Unauthorized to cancel job: " + jobId);
    }

    switch (job.getStatus()) {
      case PENDING -> {
        int affected =
            jobMapper.cancelPendingJob(jobId, "Cancelled by user", System.currentTimeMillis());
        if (affected <= 0) {
          jobMapper.setCancelRequested(jobId);
        }
        log.info("[ExcelJobManager] Cancelled PENDING job: jobId={}", jobId);
      }
      case RUNNING -> {
        jobMapper.setCancelRequested(jobId);
        log.info("[ExcelJobManager] Set cancel request for RUNNING job: jobId={}", jobId);
      }
      default -> log.debug(
          "[ExcelJobManager] Cancellation ignored for already completed job: jobId={}", jobId);
    }
  }

  public boolean isCancelRequested(String jobId) {
    return "Y".equalsIgnoreCase(jobMapper.selectCancelYn(jobId));
  }

  public Map<String, Object> deserializeParams(String paramsJson) {
    try {
      if (paramsJson == null || paramsJson.isBlank()) {
        return Map.of();
      }
      return objectMapper.readValue(paramsJson, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      throw new IllegalStateException("Failed to deserialize paramsJson", e);
    }
  }

  public List<ExcelColumnDef> deserializeColumns(String columnsJson) {
    try {
      if (columnsJson == null || columnsJson.isBlank()) {
        return List.of();
      }
      return objectMapper.readValue(columnsJson, new TypeReference<List<ExcelColumnDef>>() {});
    } catch (Exception e) {
      throw new IllegalStateException("Failed to deserialize columnsJson", e);
    }
  }

  @Scheduled(fixedDelay = 60_000)
  public void evictExpiredFiles() {
    long cutoffMs = System.currentTimeMillis() - props.getJobTtlMinutes() * 60_000L;
    List<String> expiredPaths = jobMapper.selectExpiredFilePaths(cutoffMs);

    if (expiredPaths.isEmpty()) {
      return;
    }

    for (String filePath : expiredPaths) {
      storageService.deleteFile(filePath);
    }
    jobMapper.clearExpiredFilePaths(cutoffMs);
    log.info("[ExcelJobManager] Evicted {} expired export files", expiredPaths.size());
  }

  public ExcelProperties getProperties() {
    return props;
  }
}
