package io.github.sweetpark.haexcel.core;

import io.github.sweetpark.haexcel.autoconfigure.ExcelProperties;
import io.github.sweetpark.haexcel.core.domain.ExcelColumnDef;
import io.github.sweetpark.haexcel.core.domain.ExcelJob;
import io.github.sweetpark.haexcel.generator.ExcelGeneratorService;
import io.github.sweetpark.haexcel.generator.ExcelZipGeneratorService;
import io.github.sweetpark.haexcel.generator.TemplateExcelGeneratorService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Worker service executing Excel generation jobs using Virtual Threads and atomic CAS preemption.
 */
public class ExcelWorkerService {

  private static final Logger log = LoggerFactory.getLogger(ExcelWorkerService.class);

  private final ExcelProperties props;
  private final ExcelJobMapper jobMapper;
  private final ExcelJobManager jobManager;
  private final ExcelGeneratorService generatorService;
  private final ExcelZipGeneratorService zipGeneratorService;
  private final TemplateExcelGeneratorService templateGeneratorService;
  private final ExcelJobQueue normalJobQueue;
  private final ExcelJobQueue largeJobQueue;

  private final AtomicBoolean stopped = new AtomicBoolean(false);
  private final List<Thread> workerThreads = new ArrayList<>();
  private String serverId;

  public ExcelWorkerService(
      ExcelProperties props,
      ExcelJobMapper jobMapper,
      ExcelJobManager jobManager,
      ExcelGeneratorService generatorService,
      ExcelZipGeneratorService zipGeneratorService,
      TemplateExcelGeneratorService templateGeneratorService,
      ExcelJobQueue normalJobQueue,
      ExcelJobQueue largeJobQueue) {
    this.props = props;
    this.jobMapper = jobMapper;
    this.jobManager = jobManager;
    this.generatorService = generatorService;
    this.zipGeneratorService = zipGeneratorService;
    this.templateGeneratorService = templateGeneratorService;
    this.normalJobQueue = normalJobQueue;
    this.largeJobQueue = largeJobQueue;
  }

  @PostConstruct
  public void start() {
    this.serverId = resolveServerId();

    recoverStaleRunningJobs();
    recoverPendingJobs();

    int normalCount = Math.max(1, Math.min(props.getWorkerCount(), 20));
    for (int i = 0; i < normalCount; i++) {
      final int idx = i;
      Thread t =
          createWorkerThread(
              "ha-excel-normal-worker-" + idx, () -> workerLoop(normalJobQueue, "normal"));
      t.start();
      workerThreads.add(t);
    }

    int largeCount = Math.max(1, Math.min(props.getLargeWorkerCount(), 10));
    for (int i = 0; i < largeCount; i++) {
      final int idx = i;
      Thread t =
          createWorkerThread(
              "ha-excel-large-worker-" + idx, () -> workerLoop(largeJobQueue, "large"));
      t.start();
      workerThreads.add(t);
    }

    log.info(
        "[ExcelWorkerService] Started workers: normal={} large={} serverId={}",
        normalCount,
        largeCount,
        serverId);
  }

  @PreDestroy
  public void stop() {
    log.info("[ExcelWorkerService] Stopping workers...");
    stopped.set(true);

    for (Thread worker : workerThreads) {
      worker.interrupt();
    }

    for (Thread worker : workerThreads) {
      try {
        worker.join(10_000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.warn("[ExcelWorkerService] Worker interrupted during join: {}", worker.getName());
      }
    }
    log.info("[ExcelWorkerService] All workers stopped");
  }

  private void recoverStaleRunningJobs() {
    List<String> staleJobIds = jobMapper.selectStaleRunningJobIds(serverId);
    if (staleJobIds.isEmpty()) {
      return;
    }

    deleteStaleTemporaryFiles(staleJobIds);
    int count =
        jobMapper.failStaleRunningJobs(
            serverId,
            "Terminated due to server restart (serverId=" + serverId + ")",
            System.currentTimeMillis());
    log.warn(
        "[ExcelWorkerService] Recovered and marked {} stale RUNNING jobs as FAIL for serverId={}",
        count,
        serverId);
  }

  private void recoverPendingJobs() {
    List<ExcelJob> pendingJobs = jobMapper.selectAllPendingJobs();
    if (pendingJobs.isEmpty()) {
      return;
    }

    pendingJobs.forEach(job -> jobManager.resolveQueue(job.getTotalRows()).offer(job));
    log.info(
        "[ExcelWorkerService] Re-enqueued {} PENDING jobs on startup for serverId={}",
        pendingJobs.size(),
        serverId);
  }

  private void workerLoop(ExcelJobQueue queue, String label) {
    log.debug(
        "[ExcelWorkerService] Worker loop started: thread={} queue={}",
        Thread.currentThread().getName(),
        label);

    while (!stopped.get() && !Thread.currentThread().isInterrupted()) {
      try {
        ExcelJob candidate = queue.take();

        // Atomic CAS preemption
        int claimed =
            jobMapper.tryClaimJob(candidate.getJobId(), serverId, System.currentTimeMillis());
        if (claimed == 0) {
          log.debug(
              "[ExcelWorkerService] Preemption skipped (claimed by another node): jobId={}",
              candidate.getJobId());
          continue;
        }

        log.info(
            "[ExcelWorkerService] Claimed and processing job: jobId={} serverId={} thread={}",
            candidate.getJobId(),
            serverId,
            Thread.currentThread().getName());

        processJob(candidate);

      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.info(
            "[ExcelWorkerService] Worker loop interrupted: thread={}",
            Thread.currentThread().getName());
        break;
      } catch (OutOfMemoryError oom) {
        log.error(
            "[ExcelWorkerService] OutOfMemoryError caught in worker loop - preserving worker"
                + " thread",
            oom);
      } catch (Exception e) {
        log.error("[ExcelWorkerService] Unhandled exception in worker loop", e);
      }
    }
  }

  @Scheduled(fixedDelay = 3_600_000)
  public void scanOrphanedPendingJobs() {
    long cutoffMs = System.currentTimeMillis() - props.getOrphanThresholdHours() * 3_600_000L;
    List<ExcelJob> orphans = jobMapper.selectOrphanedPendingJobs(cutoffMs);
    if (orphans.isEmpty()) {
      return;
    }

    log.info(
        "[ExcelWorkerService] Found {} orphaned PENDING jobs (cutoff={}ms), re-enqueuing for"
            + " recovery",
        orphans.size(),
        cutoffMs);
    orphans.forEach(job -> jobManager.resolveQueue(job.getTotalRows()).offer(job));
  }

  private void processJob(ExcelJob job) {
    try {
      Map<String, Object> params = jobManager.deserializeParams(job.getParamsJson());
      List<ExcelColumnDef> columns = jobManager.deserializeColumns(job.getColumnsJson());

      boolean isTemplate = job.getTemplateId() != null && !job.getTemplateId().isBlank();
      int zipThreshold = props.getZipThreshold();
      boolean useZip = !isTemplate && zipThreshold > 0 && job.getTotalRows() > zipThreshold;

      if (isTemplate) {
        log.info(
            "[ExcelWorkerService] Delegating to template engine: jobId={} templateId={}",
            job.getJobId(),
            job.getTemplateId());
        templateGeneratorService.generate(
            job, job.getBizNm(), job.getTemplateId(), params, columns);
      } else if (useZip) {
        log.info(
            "[ExcelWorkerService] Delegating to chunked ZIP generator: jobId={} totalRows={}",
            job.getJobId(),
            job.getTotalRows());
        zipGeneratorService.generate(job, job.getBizNm(), params, columns);
      } else {
        log.info(
            "[ExcelWorkerService] Delegating to single xlsx generator: jobId={}", job.getJobId());
        generatorService.generate(job, job.getBizNm(), params, columns);
      }
    } catch (Exception e) {
      log.error(
          "[ExcelWorkerService] Failed prior to generator delegation: jobId={}", job.getJobId(), e);
      jobManager.fail(job.getJobId(), e.getMessage());
    }
  }

  private void deleteStaleTemporaryFiles(List<String> staleJobIds) {
    String tempDirStr = props.getTempDir();
    for (String jobId : staleJobIds) {
      deleteSilently(Path.of(tempDirStr, jobId + ".xlsx"));
      deleteSilently(Path.of(tempDirStr, jobId + ".zip"));

      int chunkIndex = 0;
      while (true) {
        Path chunkFile = Path.of(tempDirStr, jobId + "_chunk" + chunkIndex + ".xlsx");
        if (!Files.exists(chunkFile)) {
          break;
        }
        deleteSilently(chunkFile);
        chunkIndex++;
      }
    }
  }

  private void deleteSilently(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (Exception e) {
      log.warn("[ExcelWorkerService] Failed to delete temporary file: {}", path, e);
    }
  }

  private String resolveServerId() {
    String configuredId = props.getServerId();
    if (configuredId != null && !configuredId.isBlank()) {
      return configuredId;
    }
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (Exception e) {
      String fallback = "node-" + UUID.randomUUID().toString().substring(0, 8);
      log.warn(
          "[ExcelWorkerService] Hostname resolution failed, using fallback serverId: {}", fallback);
      return fallback;
    }
  }

  public String getServerId() {
    return serverId;
  }

  /**
   * Creates a worker thread. Uses Virtual Threads on Java 21+ or falls back to daemon threads on
   * Java 17.
   */
  private Thread createWorkerThread(String name, Runnable task) {
    try {
      Object builder = Thread.class.getMethod("ofVirtual").invoke(null);
      Object namedBuilder =
          builder.getClass().getMethod("name", String.class).invoke(builder, name);
      return (Thread)
          namedBuilder.getClass().getMethod("unstarted", Runnable.class).invoke(namedBuilder, task);
    } catch (Throwable ignored) {
      Thread t = new Thread(task, name);
      t.setDaemon(true);
      return t;
    }
  }
}
