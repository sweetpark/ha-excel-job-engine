package io.github.sweetpark.haexcel.core;

import io.github.sweetpark.haexcel.core.domain.ExcelJob;
import java.util.concurrent.LinkedBlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-memory dispatch queue for Excel jobs. Push model replaces periodic DB polling: jobs are
 * offered on creation and taken by workers.
 */
public class ExcelJobQueue {

  private static final Logger log = LoggerFactory.getLogger(ExcelJobQueue.class);

  private final String queueName;
  private final LinkedBlockingQueue<ExcelJob> queue = new LinkedBlockingQueue<>();

  public ExcelJobQueue(String queueName) {
    this.queueName = queueName;
  }

  public ExcelJobQueue() {
    this("default");
  }

  public void offer(ExcelJob job) {
    queue.offer(job);
    log.debug(
        "[ExcelJobQueue:{}] Job offered: jobId={} bizNm={}",
        queueName,
        job.getJobId(),
        job.getBizNm());
  }

  public ExcelJob take() throws InterruptedException {
    return queue.take();
  }

  public int size() {
    return queue.size();
  }

  public String getQueueName() {
    return queueName;
  }
}
