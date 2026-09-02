package io.github.sweetpark.haexcel.core;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sweetpark.haexcel.core.domain.ExcelColumnDef;
import io.github.sweetpark.haexcel.core.domain.ExcelJob;
import io.github.sweetpark.haexcel.core.domain.ExcelJobStatus;
import io.github.sweetpark.haexcel.core.domain.ExcelRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExcelJobDomainTest {

  @Test
  void testExcelJobBuilderAndGetters() {
    ExcelJob job =
        ExcelJob.builder()
            .jobId("job-123")
            .bizNm("orderList")
            .fileName("orders")
            .worker("user-1")
            .serverId("node-1")
            .status(ExcelJobStatus.PENDING)
            .processedRows(0)
            .totalRows(100)
            .filePath("/tmp/orders.xlsx")
            .paramsJson("{\"date\":\"20260902\"}")
            .columnsJson("[{\"field\":\"id\"}]")
            .templateId("tpl-1")
            .errorMsg(null)
            .cancelYn("N")
            .createdAt(1000L)
            .startedAt(2000L)
            .completedAt(3000L)
            .build();

    assertEquals("job-123", job.getJobId());
    assertEquals("orderList", job.getBizNm());
    assertEquals("orders", job.getFileName());
    assertEquals("user-1", job.getWorker());
    assertEquals("node-1", job.getServerId());
    assertEquals(ExcelJobStatus.PENDING, job.getStatus());
    assertEquals(0, job.getProcessedRows());
    assertEquals(100, job.getTotalRows());
    assertEquals("/tmp/orders.xlsx", job.getFilePath());
    assertEquals("tpl-1", job.getTemplateId());
    assertEquals(1000L, job.getCreatedAt());
    assertEquals(2000L, job.getStartedAt());
    assertEquals(3000L, job.getCompletedAt());

    ExcelJob same = ExcelJob.builder().jobId("job-123").build();
    assertEquals(job, same);
    assertEquals(job.hashCode(), same.hashCode());
  }

  @Test
  void testExcelColumnDef() {
    ExcelColumnDef col = new ExcelColumnDef("col1", "Header 1", 100);
    assertTrue(col.isLeaf());
    assertEquals(100, col.effectiveWidth());

    ExcelColumnDef flexCol =
        new ExcelColumnDef("col2", "Header 2", null, 80, null, null, null, null);
    assertTrue(flexCol.isLeaf());
    assertEquals(80, flexCol.effectiveWidth());

    ExcelColumnDef groupCol = new ExcelColumnDef("grp", "Group", 200, List.of(col));
    assertFalse(groupCol.isLeaf());
  }

  @Test
  void testExcelRequest() {
    ExcelRequest req =
        new ExcelRequest(
            Map.of("key", "value"), List.of(new ExcelColumnDef("id", "ID")), "file", 50, null);
    assertEquals("file", req.fileName());
    assertEquals(50, req.totalCnt());
    assertEquals(1, req.columns().size());
    assertNull(req.templateId());
  }
}
