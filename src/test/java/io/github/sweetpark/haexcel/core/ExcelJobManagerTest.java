package io.github.sweetpark.haexcel.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sweetpark.haexcel.autoconfigure.ExcelProperties;
import io.github.sweetpark.haexcel.core.domain.ExcelColumnDef;
import io.github.sweetpark.haexcel.core.domain.ExcelJob;
import io.github.sweetpark.haexcel.core.domain.ExcelJobStatus;
import io.github.sweetpark.haexcel.storage.StorageService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExcelJobManagerTest {

  private ExcelProperties props;
  private ExcelJobMapper jobMapper;
  private StorageService storageService;
  private ObjectMapper objectMapper;
  private ExcelJobQueue normalQueue;
  private ExcelJobQueue largeQueue;
  private ExcelJobManager manager;

  @BeforeEach
  void setUp() {
    props = new ExcelProperties();
    jobMapper = mock(ExcelJobMapper.class);
    storageService = mock(StorageService.class);
    objectMapper = new ObjectMapper();
    normalQueue = new ExcelJobQueue("normal");
    largeQueue = new ExcelJobQueue("large");
    manager =
        new ExcelJobManager(
            props, jobMapper, storageService, objectMapper, normalQueue, largeQueue);
  }

  @Test
  @DisplayName("createJob validates input fields")
  void testCreateJobValidation() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            manager.createJob(
                "", "file", "user", Map.of(), List.of(new ExcelColumnDef("id", "ID")), null));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            manager.createJob(
                "biz", "file", "", Map.of(), List.of(new ExcelColumnDef("id", "ID")), null));

    assertThrows(
        IllegalArgumentException.class,
        () -> manager.createJob("biz", "file", "user", Map.of(), List.of(), null));
  }

  @Test
  @DisplayName("createJob returns existing job if active duplicate exists (idempotency)")
  void testIdempotencyHit() {
    when(jobMapper.selectActiveJobId(
            anyString(), anyString(), anyString(), anyString(), isNull(), anyLong()))
        .thenReturn("existing-job-id");
    ExcelJob existing =
        ExcelJob.builder().jobId("existing-job-id").status(ExcelJobStatus.PENDING).build();
    when(jobMapper.selectByJobId("existing-job-id")).thenReturn(existing);

    ExcelJob job =
        manager.createJob(
            "biz", "file", "user", Map.of("a", 1), List.of(new ExcelColumnDef("id", "ID")), null);
    assertEquals("existing-job-id", job.getJobId());
    verify(jobMapper, never()).insert(any());
    assertEquals(0, normalQueue.size());
  }

  @Test
  @DisplayName("createJob inserts new job and enqueues to appropriate queue based on totalCnt")
  void testCreateJobNew() {
    when(jobMapper.selectActiveJobId(
            anyString(), anyString(), anyString(), anyString(), isNull(), anyLong()))
        .thenReturn(null);

    // normal job
    ExcelJob job1 =
        manager.createJob(
            "biz",
            "file",
            "user",
            Map.of("a", 1),
            List.of(new ExcelColumnDef("id", "ID")),
            null,
            5000);
    assertNotNull(job1.getJobId());
    assertEquals(1, normalQueue.size());
    assertEquals(0, largeQueue.size());

    // large job
    ExcelJob job2 =
        manager.createJob(
            "biz",
            "file",
            "user",
            Map.of("b", 2),
            List.of(new ExcelColumnDef("id", "ID")),
            null,
            150000);
    assertNotNull(job2.getJobId());
    assertEquals(1, normalQueue.size());
    assertEquals(1, largeQueue.size());
  }

  @Test
  @DisplayName("requestCancel cancels PENDING job or requests cancel for RUNNING job")
  void testRequestCancel() {
    ExcelJob pendingJob =
        ExcelJob.builder().jobId("j1").worker("user").status(ExcelJobStatus.PENDING).build();
    when(jobMapper.selectByJobId("j1")).thenReturn(pendingJob);
    when(jobMapper.cancelPendingJob(eq("j1"), anyString(), anyLong())).thenReturn(1);

    manager.requestCancel("j1", "user");
    verify(jobMapper).cancelPendingJob(eq("j1"), anyString(), anyLong());

    ExcelJob runningJob =
        ExcelJob.builder().jobId("j2").worker("user").status(ExcelJobStatus.RUNNING).build();
    when(jobMapper.selectByJobId("j2")).thenReturn(runningJob);
    when(jobMapper.setCancelRequested("j2")).thenReturn(1);

    manager.requestCancel("j2", "user");
    verify(jobMapper).setCancelRequested("j2");
  }
}
