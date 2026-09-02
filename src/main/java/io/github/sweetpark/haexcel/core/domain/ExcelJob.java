package io.github.sweetpark.haexcel.core.domain;

import java.io.Serializable;
import java.util.Objects;

/** High-availability distributed Excel job entity mapped to ha_excel_job table. */
public class ExcelJob implements Serializable {

  private static final long serialVersionUID = 1L;

  private String jobId;
  private String bizNm;
  private String fileName;
  private String worker;
  private String serverId;
  private ExcelJobStatus status;
  private int processedRows;
  private int totalRows;
  private String filePath;
  private String paramsJson;
  private String columnsJson;
  private String templateId;
  private String errorMsg;
  private String cancelYn;
  private long createdAt;
  private Long startedAt;
  private Long completedAt;

  public ExcelJob() {}

  public static Builder builder() {
    return new Builder();
  }

  public String getJobId() {
    return jobId;
  }

  public void setJobId(String jobId) {
    this.jobId = jobId;
  }

  public String getBizNm() {
    return bizNm;
  }

  public void setBizNm(String bizNm) {
    this.bizNm = bizNm;
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public String getWorker() {
    return worker;
  }

  public void setWorker(String worker) {
    this.worker = worker;
  }

  public String getServerId() {
    return serverId;
  }

  public void setServerId(String serverId) {
    this.serverId = serverId;
  }

  public ExcelJobStatus getStatus() {
    return status;
  }

  public void setStatus(ExcelJobStatus status) {
    this.status = status;
  }

  public int getProcessedRows() {
    return processedRows;
  }

  public void setProcessedRows(int processedRows) {
    this.processedRows = processedRows;
  }

  public int getTotalRows() {
    return totalRows;
  }

  public void setTotalRows(int totalRows) {
    this.totalRows = totalRows;
  }

  public String getFilePath() {
    return filePath;
  }

  public void setFilePath(String filePath) {
    this.filePath = filePath;
  }

  public String getParamsJson() {
    return paramsJson;
  }

  public void setParamsJson(String paramsJson) {
    this.paramsJson = paramsJson;
  }

  public String getColumnsJson() {
    return columnsJson;
  }

  public void setColumnsJson(String columnsJson) {
    this.columnsJson = columnsJson;
  }

  public String getTemplateId() {
    return templateId;
  }

  public void setTemplateId(String templateId) {
    this.templateId = templateId;
  }

  public String getErrorMsg() {
    return errorMsg;
  }

  public void setErrorMsg(String errorMsg) {
    this.errorMsg = errorMsg;
  }

  public String getCancelYn() {
    return cancelYn;
  }

  public void setCancelYn(String cancelYn) {
    this.cancelYn = cancelYn;
  }

  public long getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(long createdAt) {
    this.createdAt = createdAt;
  }

  public Long getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(Long startedAt) {
    this.startedAt = startedAt;
  }

  public Long getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(Long completedAt) {
    this.completedAt = completedAt;
  }

  public static class Builder {
    private final ExcelJob job = new ExcelJob();

    public Builder jobId(String jobId) {
      job.setJobId(jobId);
      return this;
    }

    public Builder bizNm(String bizNm) {
      job.setBizNm(bizNm);
      return this;
    }

    public Builder fileName(String fileName) {
      job.setFileName(fileName);
      return this;
    }

    public Builder worker(String worker) {
      job.setWorker(worker);
      return this;
    }

    public Builder serverId(String serverId) {
      job.setServerId(serverId);
      return this;
    }

    public Builder status(ExcelJobStatus status) {
      job.setStatus(status);
      return this;
    }

    public Builder processedRows(int processedRows) {
      job.setProcessedRows(processedRows);
      return this;
    }

    public Builder totalRows(int totalRows) {
      job.setTotalRows(totalRows);
      return this;
    }

    public Builder filePath(String filePath) {
      job.setFilePath(filePath);
      return this;
    }

    public Builder paramsJson(String paramsJson) {
      job.setParamsJson(paramsJson);
      return this;
    }

    public Builder columnsJson(String columnsJson) {
      job.setColumnsJson(columnsJson);
      return this;
    }

    public Builder templateId(String templateId) {
      job.setTemplateId(templateId);
      return this;
    }

    public Builder errorMsg(String errorMsg) {
      job.setErrorMsg(errorMsg);
      return this;
    }

    public Builder cancelYn(String cancelYn) {
      job.setCancelYn(cancelYn);
      return this;
    }

    public Builder createdAt(long createdAt) {
      job.setCreatedAt(createdAt);
      return this;
    }

    public Builder startedAt(Long startedAt) {
      job.setStartedAt(startedAt);
      return this;
    }

    public Builder completedAt(Long completedAt) {
      job.setCompletedAt(completedAt);
      return this;
    }

    public ExcelJob build() {
      return job;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ExcelJob excelJob = (ExcelJob) o;
    return Objects.equals(jobId, excelJob.jobId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(jobId);
  }
}
