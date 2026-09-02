package io.github.sweetpark.haexcel.core;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sweetpark.haexcel.core.domain.ExcelJob;
import org.junit.jupiter.api.Test;

class ExcelJobQueueTest {

  @Test
  void testOfferAndTake() throws InterruptedException {
    ExcelJobQueue queue = new ExcelJobQueue("normal");
    assertEquals("normal", queue.getQueueName());
    assertEquals(0, queue.size());

    ExcelJob job = ExcelJob.builder().jobId("q-1").build();
    queue.offer(job);
    assertEquals(1, queue.size());

    ExcelJob popped = queue.take();
    assertEquals("q-1", popped.getJobId());
    assertEquals(0, queue.size());
  }
}
