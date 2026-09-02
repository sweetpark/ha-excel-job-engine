package io.github.sweetpark.haexcel.controller;

import io.github.sweetpark.haexcel.autoconfigure.ExcelProperties;
import io.github.sweetpark.haexcel.core.ExcelJobManager;
import io.github.sweetpark.haexcel.core.domain.ExcelJob;
import io.github.sweetpark.haexcel.core.domain.ExcelJobStatus;
import io.github.sweetpark.haexcel.core.domain.ExcelRequest;
import io.github.sweetpark.haexcel.template.ExcelTemplateService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Standard REST Controller for high-availability Excel jobs. */
@RestController
@RequestMapping("/api/excel")
public class ExcelController {

  private static final Logger log = LoggerFactory.getLogger(ExcelController.class);

  private final ExcelJobManager jobManager;
  private final ExcelTemplateService templateService;
  private final ExcelProperties props;
  private final ExcelSecurityProvider securityProvider;

  public ExcelController(
      ExcelJobManager jobManager,
      ExcelTemplateService templateService,
      ExcelProperties props,
      ExcelSecurityProvider securityProvider) {
    this.jobManager = jobManager;
    this.templateService = templateService;
    this.props = props;
    this.securityProvider =
        (securityProvider != null) ? securityProvider : new DefaultExcelSecurityProvider();
  }

  @GetMapping("/config")
  public ResponseEntity<Map<String, Object>> config() {
    return ResponseEntity.ok(Map.of("clientThreshold", props.getClientThreshold()));
  }

  @PostMapping("/{bizNm}")
  public ResponseEntity<Map<String, Object>> submit(
      @PathVariable("bizNm") String bizNm,
      @RequestBody ExcelRequest request,
      HttpServletRequest servletRequest) {

    boolean isTemplateMode = request.templateId() != null && !request.templateId().isBlank();
    if (!isTemplateMode && (request.columns() == null || request.columns().isEmpty())) {
      log.warn("[ExcelController] Bad request - columns missing for bizNm={}", bizNm);
      return ResponseEntity.badRequest().build();
    }

    Map<String, Object> safeParams = request.params() != null ? request.params() : Map.of();
    String safeFileName =
        (request.fileName() != null && !request.fileName().isBlank())
            ? request.fileName()
            : "export";
    String worker = securityProvider.extractUserId(servletRequest);

    log.info(
        "[ExcelController] Submitting job: bizNm={} totalCnt={} worker={}",
        bizNm,
        request.totalCnt(),
        worker);

    ExcelJob job =
        jobManager.createJob(
            bizNm,
            safeFileName,
            worker,
            safeParams,
            request.columns(),
            request.templateId(),
            request.totalCnt());

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("jobId", job.getJobId());
    response.put("status", job.getStatus().name());
    return ResponseEntity.accepted().body(response);
  }

  @GetMapping("/{jobId}/status")
  public ResponseEntity<Map<String, Object>> status(
      @PathVariable("jobId") String jobId, HttpServletRequest servletRequest) {

    String requestUserId = securityProvider.extractUserId(servletRequest);

    return jobManager
        .findJob(jobId)
        .filter(job -> securityProvider.isOwner(job, requestUserId))
        .map(
            job -> {
              Map<String, Object> res = new LinkedHashMap<>();
              res.put("jobId", job.getJobId());
              res.put("status", job.getStatus().name());
              res.put("processedRows", job.getProcessedRows());
              res.put("totalRows", job.getTotalRows());

              if (job.getStatus() == ExcelJobStatus.PENDING) {
                int aheadCount = jobManager.countPendingJobsBefore(job.getCreatedAt());
                int queuePosition = aheadCount + 1;
                int workerCount = Math.max(1, props.getWorkerCount());
                int estimatedSeconds =
                    (int) Math.ceil((double) queuePosition / workerCount)
                        * props.getEstimatedSecondsPerJob();
                res.put("queuePosition", queuePosition);
                res.put("estimatedSeconds", estimatedSeconds);
              }

              if (job.getErrorMsg() != null) {
                res.put("errorMsg", job.getErrorMsg());
              }
              return ResponseEntity.ok(res);
            })
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/{jobId}/file")
  public ResponseEntity<Resource> download(
      @PathVariable("jobId") String jobId, HttpServletRequest servletRequest) {

    String requestUserId = securityProvider.extractUserId(servletRequest);

    return jobManager
        .findJob(jobId)
        .filter(job -> securityProvider.isOwner(job, requestUserId))
        .filter(job -> job.getStatus() == ExcelJobStatus.DONE)
        .map(job -> buildFileResponse(job, jobId))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @DeleteMapping("/{jobId}/cancel")
  public ResponseEntity<Map<String, Object>> cancel(
      @PathVariable("jobId") String jobId, HttpServletRequest servletRequest) {

    String requestUserId = securityProvider.extractUserId(servletRequest);

    try {
      jobManager.requestCancel(jobId, requestUserId);
      return ResponseEntity.ok(Map.of("jobId", jobId, "message", "Cancel request accepted."));
    } catch (IllegalArgumentException e) {
      log.warn("[ExcelController] Cancel rejected: jobId={} reason={}", jobId, e.getMessage());
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
    }
  }

  @GetMapping("/template/{templateId}")
  public ResponseEntity<byte[]> downloadTemplate(@PathVariable("templateId") String templateId) {
    if (!templateId.matches("[\\w-]+")) {
      return ResponseEntity.badRequest().build();
    }

    try {
      byte[] bytes = templateService.getTemplate(templateId);
      String encodedName =
          URLEncoder.encode(templateId + ".xlsx", StandardCharsets.UTF_8).replace("+", "%20");
      return ResponseEntity.ok()
          .contentType(
              MediaType.parseMediaType(
                  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
          .body(bytes);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.notFound().build();
    } catch (Exception e) {
      log.error("[ExcelController] Failed to download template: templateId={}", templateId, e);
      return ResponseEntity.internalServerError().build();
    }
  }

  private ResponseEntity<Resource> buildFileResponse(ExcelJob job, String jobId) {
    Resource resource = jobManager.loadFile(job).orElse(null);
    if (resource == null) {
      return ResponseEntity.notFound().build();
    }

    boolean isZip = job.getFilePath() != null && job.getFilePath().endsWith(".zip");
    String contentType =
        isZip
            ? "application/zip"
            : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    String ext = isZip ? ".zip" : ".xlsx";
    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    String encodedName =
        URLEncoder.encode(
                (job.getFileName() != null ? job.getFileName() : "export") + "_" + timestamp + ext,
                StandardCharsets.UTF_8)
            .replace("+", "%20");

    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(contentType))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
        .body(resource);
  }
}
