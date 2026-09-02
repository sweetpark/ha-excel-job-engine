package io.github.sweetpark.haexcel;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sweetpark.haexcel.autoconfigure.ExcelAutoConfiguration;
import io.github.sweetpark.haexcel.core.ExcelDataProvider;
import io.github.sweetpark.haexcel.core.ExcelDataRegistry;
import io.github.sweetpark.haexcel.core.ExcelJobManager;
import io.github.sweetpark.haexcel.core.domain.ExcelColumnDef;
import io.github.sweetpark.haexcel.core.domain.ExcelJob;
import io.github.sweetpark.haexcel.core.domain.ExcelJobStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;

@SpringBootTest(classes = ExcelEndToEndIntegrationTest.TestConfig.class)
class ExcelEndToEndIntegrationTest {

  @Configuration
  @Import({
    DataSourceAutoConfiguration.class,
    SqlInitializationAutoConfiguration.class,
    MybatisAutoConfiguration.class,
    ExcelAutoConfiguration.class
  })
  static class TestConfig {
    @Bean
    public ExcelDataProvider sampleUserDataProvider() {
      return new ExcelDataProvider() {
        @Override
        public String getName() {
          return "userList";
        }

        @Override
        public List<Map<String, Object>> fetchData(Map<String, Object> params) {
          return List.of(
              Map.of("id", 1, "username", "john_doe", "amount", 125000),
              Map.of("id", 2, "username", "jane_smith", "amount", 450000));
        }
      };
    }
  }

  @Autowired private ExcelJobManager jobManager;

  @Autowired private ExcelDataRegistry dataRegistry;

  @Test
  @DisplayName(
      "End-to-End: Submit export job -> Worker processes -> Generates XLSX -> Verified DONE")
  void testEndToEndExport() throws Exception {
    assertNotNull(dataRegistry.resolve("userList"));

    List<ExcelColumnDef> columns =
        List.of(
            new ExcelColumnDef("id", "User ID", 80),
            new ExcelColumnDef("username", "User Name", 150),
            new ExcelColumnDef("amount", "Amount", 120, null, null, "krw", null, null));

    ExcelJob job =
        jobManager.createJob(
            "userList", "users_export", "admin", Map.of("dept", "engineering"), columns, null, 2);

    assertNotNull(job.getJobId());
    assertEquals(ExcelJobStatus.PENDING, job.getStatus());

    // Wait for worker virtual thread to complete processing
    int attempts = 0;
    ExcelJob completedJob = null;
    while (attempts < 30) {
      Thread.sleep(200);
      completedJob = jobManager.findJob(job.getJobId()).orElse(null);
      if (completedJob != null
          && (completedJob.getStatus() == ExcelJobStatus.DONE
              || completedJob.getStatus() == ExcelJobStatus.FAIL)) {
        break;
      }
      attempts++;
    }

    assertNotNull(completedJob);
    assertEquals(
        ExcelJobStatus.DONE,
        completedJob.getStatus(),
        "Job must finish with status DONE, error: " + completedJob.getErrorMsg());
    assertEquals(2, completedJob.getProcessedRows());
    assertNotNull(completedJob.getFilePath());

    Resource resource = jobManager.loadFile(completedJob).orElse(null);
    assertNotNull(resource);
    assertTrue(resource.exists());
    assertTrue(resource.contentLength() > 0);
  }
}
