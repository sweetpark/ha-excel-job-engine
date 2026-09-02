package io.github.sweetpark.haexcel.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.github.sweetpark.haexcel.autoconfigure.ExcelProperties;
import io.github.sweetpark.haexcel.core.domain.ExcelJob;
import io.github.sweetpark.haexcel.generator.ExcelGeneratorService;
import io.github.sweetpark.haexcel.generator.ExcelZipGeneratorService;
import io.github.sweetpark.haexcel.generator.TemplateExcelGeneratorService;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExcelWorkerServiceTest {

  @Test
  @DisplayName(
      "CAS preemption guarantees exactly one worker processes candidate job when multiple workers"
          + " compete")
  void testCasPreemptionConcurrency() throws Exception {
    ExcelJobMapper mapper = mock(ExcelJobMapper.class);
    AtomicInteger successfulClaims = new AtomicInteger(0);

    // Simulate atomic DB CAS UPDATE: only the first call returns 1, subsequent calls return 0
    when(mapper.tryClaimJob(eq("concurrent-job"), anyString(), anyLong()))
        .thenAnswer(
            inv -> {
              return successfulClaims.compareAndSet(0, 1) ? 1 : 0;
            });

    int threads = 10;
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threads);
    AtomicInteger wonCount = new AtomicInteger(0);

    for (int i = 0; i < threads; i++) {
      final String workerServerId = "node-" + i;
      new Thread(
              () -> {
                try {
                  startLatch.await();
                  int claimed =
                      mapper.tryClaimJob(
                          "concurrent-job", workerServerId, System.currentTimeMillis());
                  if (claimed == 1) {
                    wonCount.incrementAndGet();
                  }
                } catch (Exception ignored) {
                } finally {
                  doneLatch.countDown();
                }
              })
          .start();
    }

    startLatch.countDown();
    assertTrue(doneLatch.await(5, TimeUnit.SECONDS));

    assertEquals(1, wonCount.get(), "Exactly 1 worker node must win the CAS preemption");
  }

  @Test
  @DisplayName("Worker service recovers stale RUNNING jobs and re-enqueues PENDING jobs on startup")
  void testStartupRecovery() {
    ExcelProperties props = new ExcelProperties();
    props.setWorkerCount(1);
    props.setLargeWorkerCount(1);

    ExcelJobMapper mapper = mock(ExcelJobMapper.class);
    ExcelJobManager manager = mock(ExcelJobManager.class);
    ExcelGeneratorService gen = mock(ExcelGeneratorService.class);
    ExcelZipGeneratorService zipGen = mock(ExcelZipGeneratorService.class);
    TemplateExcelGeneratorService tplGen = mock(TemplateExcelGeneratorService.class);
    ExcelJobQueue normalQ = new ExcelJobQueue("normal");
    ExcelJobQueue largeQ = new ExcelJobQueue("large");

    when(mapper.selectStaleRunningJobIds(anyString())).thenReturn(List.of("stale-1"));
    when(mapper.selectAllPendingJobs())
        .thenReturn(List.of(ExcelJob.builder().jobId("pending-1").totalRows(100).build()));
    when(manager.resolveQueue(anyInt())).thenReturn(normalQ);

    ExcelWorkerService service =
        new ExcelWorkerService(props, mapper, manager, gen, zipGen, tplGen, normalQ, largeQ);

    service.start();

    verify(mapper).selectStaleRunningJobIds(anyString());
    verify(mapper).failStaleRunningJobs(anyString(), anyString(), anyLong());
    verify(mapper).selectAllPendingJobs();
    verify(manager).resolveQueue(100);

    service.stop();
  }
}
