package io.github.sweetpark.haexcel.core;

import io.github.sweetpark.haexcel.core.domain.ExcelJob;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** MyBatis mapper for ha_excel_job table. */
@Mapper
public interface ExcelJobMapper {

  void insert(ExcelJob job);

  ExcelJob selectByJobId(@Param("jobId") String jobId);

  String selectActiveJobId(
      @Param("worker") String worker,
      @Param("bizNm") String bizNm,
      @Param("paramsJson") String paramsJson,
      @Param("columnsJson") String columnsJson,
      @Param("templateId") String templateId,
      @Param("cutoffMs") long cutoffMs);

  ExcelJob selectOldestPending();

  List<ExcelJob> selectAllPendingJobs();

  List<ExcelJob> selectOrphanedPendingJobs(@Param("cutoffMs") long cutoffMs);

  int tryClaimJob(
      @Param("jobId") String jobId,
      @Param("serverId") String serverId,
      @Param("startedAt") long startedAt);

  void updateProgress(
      @Param("jobId") String jobId,
      @Param("processedRows") int processedRows,
      @Param("totalRows") int totalRows);

  void updateDone(
      @Param("jobId") String jobId,
      @Param("filePath") String filePath,
      @Param("completedAt") long completedAt);

  void updateFail(
      @Param("jobId") String jobId,
      @Param("errorMsg") String errorMsg,
      @Param("completedAt") long completedAt);

  List<String> selectStaleRunningJobIds(@Param("serverId") String serverId);

  int failStaleRunningJobs(
      @Param("serverId") String serverId,
      @Param("errorMsg") String errorMsg,
      @Param("completedAt") long completedAt);

  int countPendingJobsBefore(@Param("createdAt") long createdAt);

  List<String> selectExpiredFilePaths(@Param("cutoffMs") long cutoffMs);

  void clearExpiredFilePaths(@Param("cutoffMs") long cutoffMs);

  int cancelPendingJob(
      @Param("jobId") String jobId,
      @Param("errorMsg") String errorMsg,
      @Param("completedAt") long completedAt);

  int setCancelRequested(@Param("jobId") String jobId);

  String selectCancelYn(@Param("jobId") String jobId);
}
